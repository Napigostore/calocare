package com.calocare.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

/** Everything the user enters lives in one JSON file in app-private storage. */
class Store(context: Context) {
    private val file = File(context.filesDir, "calocare.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val writer = CoroutineScope(SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher())
    private val secrets = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    private val state = MutableStateFlow(load())
    val data: StateFlow<AppData> = state

    private fun load(): AppData = try {
        if (file.exists()) json.decodeFromString<AppData>(file.readText()) else AppData()
    } catch (e: Exception) {
        // Keep the unreadable file for recovery instead of silently overwriting it.
        file.copyTo(File(file.parentFile, "calocare.corrupt.json"), overwrite = true)
        AppData()
    }

    fun update(transform: (AppData) -> AppData) {
        state.update(transform)
        val snapshot = state.value
        writer.launch {
            val tmp = File(file.parentFile, "calocare.json.tmp")
            tmp.writeText(json.encodeToString(snapshot))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }

    // The dish library as last synchronised from the server. Kept apart from the user's data: it is
    // the same for everyone and can always be downloaded again.
    private val libraryFile = File(context.filesDir, "library.json")

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
}
