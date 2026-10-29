package com.calocare.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

/**
 * Everything the user enters lives in one encrypted file in app-private storage, written at most once
 * per burst of edits. The AI key sits in an encrypted preference store; both are protected by a key
 * held in the Android Keystore.
 */
class Store(context: Context) {
    private val appContext = context.applicationContext
    private val dir = context.filesDir
    private val legacyFile = File(dir, "calocare.json")
    private val encryptedFile = File(dir, "calocare.enc")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val writer = CoroutineScope(SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher())
    private var pendingSave: Job? = null
    private val masterKeyAlias by lazy { MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC) }

    private val secrets: SharedPreferences = EncryptedSharedPreferences.create(
        "secrets_v2", masterKeyAlias, appContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val state = MutableStateFlow(load())
    val data: StateFlow<AppData> = state

    init {
        appContext.getSharedPreferences("secrets", Context.MODE_PRIVATE).let { old ->
            old.getString("anthropic_api_key", null)?.let { key ->
                secrets.edit().putString("anthropic_api_key", key).apply()
                old.edit().clear().apply()
            }
        }
    }

    private fun encrypted(f: File) = EncryptedFile.Builder(f, appContext, masterKeyAlias, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()

    private fun load(): AppData = try {
        when {
            encryptedFile.exists() -> json.decodeFromString(encrypted(encryptedFile).openFileInput().use { it.readBytes() }.decodeToString())
            legacyFile.exists() -> json.decodeFromString<AppData>(legacyFile.readText()).also { migrated ->
                // The first encrypted copy is written right away; the plaintext one is removed after it.
                writeEncrypted(json.encodeToString(migrated))
                legacyFile.delete()
            }
            else -> AppData()
        }
    } catch (e: Exception) {
        // Keep the unreadable file for recovery instead of silently overwriting it.
        if (encryptedFile.exists()) encryptedFile.copyTo(File(dir, "calocare.corrupt.enc"), overwrite = true)
        AppData()
    }

    private fun writeEncrypted(text: String) {
        val staging = File(dir, "tmp").apply { mkdirs() }
        val tmp = File(staging, encryptedFile.name)
        tmp.delete()
        encrypted(tmp).openFileOutput().use { it.write(text.encodeToByteArray()) }
        if (!tmp.renameTo(encryptedFile)) { encryptedFile.delete(); tmp.renameTo(encryptedFile) }
    }

    fun update(transform: (AppData) -> AppData) {
        state.update(transform)
        pendingSave?.cancel()
        pendingSave = writer.launch {
            delay(SAVE_DELAY_MS)
            writeEncrypted(json.encodeToString(state.value))
        }
    }

    // The dish library as last synchronised from the server. Kept apart from the user's data: it is
    // the same for everyone and can always be downloaded again.
    private val libraryFile = File(dir, "library.json")

    fun loadLibrary(): LibraryCache = try {
        if (libraryFile.exists()) json.decodeFromString<LibraryCache>(libraryFile.readText()) else LibraryCache()
    } catch (e: Exception) {
        LibraryCache()
    }

    fun saveLibrary(cache: LibraryCache) {
        writer.launch { libraryFile.writeText(json.encodeToString(cache)) }
    }

    /** Erases the profile, diary and AI key from the device. */
    fun wipe() {
        secrets.edit().clear().apply()
        update { AppData() }
    }

    /** The AI key is kept out of the data file so exports/debug dumps of the diary never contain it. */
    var apiKey: String
        get() = secrets.getString("anthropic_api_key", "") ?: ""
        set(value) = secrets.edit().putString("anthropic_api_key", value.trim()).apply()

    private companion object {
        const val SAVE_DELAY_MS = 300L
    }
}
