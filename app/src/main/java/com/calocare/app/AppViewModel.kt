package com.calocare.app

import com.calocare.app.billing.ProBilling
import com.calocare.app.servingLabel
import com.calocare.app.label
import com.calocare.app.L
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calocare.app.data.AppData
import com.calocare.app.data.Condition
import com.calocare.app.data.Diet
import com.calocare.app.data.ExerciseEntry
import com.calocare.app.data.Food
import com.calocare.app.data.FoodDb
import com.calocare.app.data.FoodMatcher
import com.calocare.app.data.Goal
import com.calocare.app.data.LibraryCache
import com.calocare.app.data.LogEntry
import com.calocare.app.data.Meal
import com.calocare.app.data.Nutrition
import com.calocare.app.data.Profile
import com.calocare.app.data.Region
import com.calocare.app.data.Store
import com.calocare.app.data.Tag
import com.calocare.app.data.Warning
import com.calocare.app.data.WeightEntry
import com.calocare.app.data.entriesOn
import com.calocare.app.data.totals
import com.calocare.app.health.HealthConnect
import com.calocare.app.net.AiClient
import com.calocare.app.net.AiException
import com.calocare.app.net.ChatTurn
import com.calocare.app.net.DetectedItem
import com.calocare.app.net.FoodSearch
import com.calocare.app.ui.DishTotals
import com.calocare.app.net.HostedClient
import com.calocare.app.net.Ingredient
import com.calocare.app.net.Quota
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * One dish found in a meal photo. [item] holds the numbers for ONE serving; [qty] is how many
 * servings, which the user can change, as they can the dish itself when the AI named it wrongly.
 */
class PhotoItem(val ai: DetectedItem, item: DetectedItem, fromTable: Boolean, qty: Float, val learned: Boolean) {
    /** The name exactly as the AI gave it, before the app matched it to its table. */
    val aiName get() = ai.name
    var item by mutableStateOf(item)

    /** True when the calories come from the app's food table rather than the AI's own estimate. */
    var fromTable by mutableStateOf(fromTable)
    /** Set when [item] is a food the app knows; logging it then counts on that food's tile. */
    var food by mutableStateOf<Food?>(null)
    var checked by mutableStateOf(true)
    var qty by mutableStateOf(qty)
    var size by mutableStateOf(if (SIZES.any { it.first == ai.size }) ai.size else "medium")

    /** Servings to log: the count, scaled by the size. */
    val amount get() = qty * (SIZE_FACTOR[size] ?: 1f)

    /** Other dishes the AI (or earlier users' corrections) say this could be; one tap switches to it. */
    var alternatives by mutableStateOf(item.alternatives)
    /** True when the user settled the dish's numbers ingredient by ingredient: their knowledge, not the AI's guess. */
    var userBuilt by mutableStateOf(false)
    /** True once the user changed what the dish is, which is worth sharing so the next person need not. */
    var corrected by mutableStateOf(false)
    val kcal get() = (item.kcal * amount).roundToInt()

    companion object {
        val SIZES = listOf("small" to L("Nhỏ", "Small"), "medium" to L("Vừa", "Medium"), "large" to L("To", "Large"))
        // Roughly how far a small or large egg, bowl or fruit is from the standard one.
        val SIZE_FACTOR = mapOf("small" to 0.75f, "medium" to 1f, "large" to 1.3f)
    }
}

/** [bitmap] is exactly what is sent to the AI. */
class PhotoState(val bitmap: Bitmap) {
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var advice by mutableStateOf("")
    var title by mutableStateOf("")
    var score by mutableStateOf(-1)
    var pros by mutableStateOf<List<String>>(emptyList())
    var cons by mutableStateOf<List<String>>(emptyList())
    val items = mutableStateListOf<PhotoItem>()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    val data = store.data
    val health = HealthConnect(app)

    /** One-line messages for the snackbar. */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    var date by mutableStateOf(LocalDate.now())
    var meal by mutableStateOf(Meal.forHour(LocalTime.now().hour))

    var apiKey by mutableStateOf(store.apiKey)
        private set

    /** The built-in AI server is used unless the user has entered a key of their own. */
    val hostedConfigured = BuildConfig.BACKEND_URL.isNotBlank()
    // A personal Anthropic key takes over; a DeepSeek key cannot read photos and does not know the app's
    // food table, so with the built-in AI available it is simply not used.
    val usingHosted get() = hostedConfigured && !AiClient.isClaudeKey(apiKey)
    val hasAi get() = apiKey.isNotBlank() || hostedConfigured
    private val photosOnHosted get() = usingHosted
    val hasPhotoAi get() = hostedConfigured || AiClient.isClaudeKey(apiKey)

    /** Remaining allowance on the built-in AI, as last reported by the server. */
    var quota by mutableStateOf<Quota?>(null)
        private set

    private val deviceId by lazy {
        // ANDROID_ID is per app-signing key and device and survives reinstalls, so the trial is not reset
        // by reinstalling. Only its hash leaves the phone.
        val androidId = Settings.Secure.getString(getApplication<Application>().contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        MessageDigest.getInstance("SHA-256").digest("calocare:$androidId".toByteArray()).joinToString("") { "%02x".format(it) }
    }
    private val hosted by lazy { HostedClient(BuildConfig.BACKEND_URL, BuildConfig.BACKEND_APP_KEY, deviceId, onQuota = { quota = it }, onProRequired = { paywall = true }) }
    val api: HostedClient get() = hosted

    /** The CaloCare Pro offer is on screen: opened by the user, or when the free AI allowance runs out. */
    var paywall by mutableStateOf(false)
    val isPro get() = quota?.pro == true
    val billing by lazy { ProBilling(getApplication(), viewModelScope) { token -> hosted.activatePro(token) } }

    private var aiClient: Pair<String, AiClient>? = null
    private fun ai(): AiClient {
        if (usingHosted) return hosted
        aiClient?.takeIf { it.first == apiKey }?.let { return it.second }
        return AiClient(apiKey).also { aiClient = apiKey to it }
    }

    fun refreshQuota() {
        if (usingHosted) viewModelScope.launch { hosted.refreshQuota() }
    }

    fun saveApiKey(key: String) {
        store.apiKey = key
        apiKey = key.trim()
    }

    fun wipeAllData() {
        store.wipe()
        apiKey = ""
        chat.clear()
        date = LocalDate.now()
    }

    // ------------------------------------------------------------------ dish library

    private var libraryCache = store.loadLibrary()

    /** The dishes the app knows: the server's library once synchronised, the built-in table until then. */
    var library by mutableStateOf(libraryCache.foods.ifEmpty { FoodDb.foods })
        private set

    /** Picks up dishes added or corrected in the library since the last sync; no app update needed. */
    fun syncLibrary() {
        if (!hostedConfigured) return
        viewModelScope.launch {
            val update = hosted.library(libraryCache.version) ?: return@launch
            if (update.version <= libraryCache.version) return@launch
            val merged = LinkedHashMap(libraryCache.foods.associateBy { it.id })
            update.dishes.forEach { merged[it.id] = it.toFood() }
            update.removed.forEach { merged.remove(it) }
            libraryCache = LibraryCache(update.version, merged.values.toList())
            store.saveLibrary(libraryCache)
            library = libraryCache.foods.ifEmpty { FoodDb.foods }
        }
    }

    /** Offers a dish this user created to the shared library. Only its name and numbers are sent. */
    private fun offerToLibrary(food: Food) {
        if (usingHosted && library.none { it.id == food.id || it.name.equals(food.name, ignoreCase = true) }) {
            viewModelScope.launch { hosted.submitDish(food) }
        }
    }

    private val ids = AtomicLong(System.currentTimeMillis() * 1000)
    private fun nextId() = ids.incrementAndGet()

    // ------------------------------------------------------------------ diary

    /** A dish that triggers an allergy warning waits here until the user confirms. */
    var pendingDanger by mutableStateOf<Pair<Food, List<Warning>>?>(null)

    private fun Food.toEntry(qty: Float, meal: String, date: LocalDate) = LogEntry(
        id = nextId(), date = date.toString(), meal = meal, foodId = id, name = name, emoji = emoji,
        imageUrl = imageUrl, serving = serving, kcal = kcal, protein = protein, carbs = carbs, fat = fat,
        course = course, tags = tags, qty = qty,
    )

    /** Tap on a food tile: +1 serving. Stops for confirmation only when it hits a declared allergy. */
    fun tapFood(food: Food) {
        val warnings = Nutrition.warnings(food.tags, data.value.profile)
        if (warnings.any { it.danger }) pendingDanger = food to warnings else addFood(food, 1f, warnings)
    }

    fun addFood(food: Food, qty: Float = 1f, warnings: List<Warning> = Nutrition.warnings(food.tags, data.value.profile)) {
        val meal = meal
        val date = date
        store.update { d ->
            // Tapping the same food again in the same meal bumps the quantity instead of adding a new line.
            val existing = d.entries.firstOrNull { it.date == date.toString() && it.meal == meal && it.foodId == food.id }
            val entries = if (existing != null) d.entries.map { if (it.id == existing.id) it.copy(qty = it.qty + qty) else it }
            else d.entries + food.toEntry(qty, meal, date)
            // Foods found online or estimated by AI are kept locally, so the next search needs no network.
            val keep = (food.id.startsWith("off_") || food.id.startsWith("ai_")) && d.customFoods.none { it.id == food.id }
            d.copy(entries = entries, customFoods = if (keep) d.customFoods + food else d.customFoods)
        }
        val amount = if (qty == 1f) "+1" else "+${fmtQty(qty)}"
        messages.tryEmit("$amount ${food.label} · ${(food.kcal * qty).roundToInt()} kcal" + (warnings.firstOrNull()?.let { "\n⚠️ ${it.text}" } ?: ""))
    }

    fun addMeal(items: List<Pair<Food, Float>>, meal: String) {
        val day = date
        store.update { d ->
            var entries = d.entries
            for ((food, qty) in items) {
                val existing = entries.firstOrNull { it.date == day.toString() && it.meal == meal && it.foodId == food.id }
                entries = if (existing != null) entries.map { if (it.id == existing.id) it.copy(qty = it.qty + qty) else it }
                else entries + food.toEntry(qty, meal, day)
            }
            d.copy(entries = entries)
        }
        messages.tryEmit(L("Đã thêm ${items.size} món vào bữa ${Meal.labels[meal]?.lowercase()}", "Added ${items.size} item(s) to ${Meal.labels[meal]?.lowercase()}"))
    }

    fun changeQty(entryId: Long, delta: Float) = store.update { d ->
        d.copy(entries = d.entries.mapNotNull {
            if (it.id != entryId) it else (it.qty + delta).let { q -> if (q <= 0.01f) null else it.copy(qty = q) }
        })
    }

    fun removeEntry(entryId: Long) = store.update { d -> d.copy(entries = d.entries.filterNot { it.id == entryId }) }

    /** Saves an edit and applies it to what is already logged for the day being viewed. */
    fun saveCustomFood(food: Food) {
        val day = date.toString()
        store.update { d ->
            d.copy(
                customFoods = d.customFoods.filterNot { it.id == food.id } + food,
                entries = d.entries.map {
                    if (it.date == day && it.foodId == food.id) it.copy(
                        name = food.name, emoji = food.emoji, serving = food.serving, kcal = food.kcal, protein = food.protein,
                        carbs = food.carbs, fat = food.fat, course = food.course, tags = food.tags,
                    ) else it
                },
            )
        }
        offerToLibrary(food)
        messages.tryEmit(L("Đã lưu ${food.name}", "Saved ${food.label}"))
    }

    fun deleteCustomFood(id: String) = store.update { d -> d.copy(customFoods = d.customFoods.filterNot { it.id == id }) }

    fun newCustomId() = "custom_${nextId()}"

    /** Foods the user logs often, most frequent first; items usually eaten at this meal rank higher. */
    fun frequentFoods(d: AppData, meal: String): List<Food> {
        val since = LocalDate.now().minusDays(45).toString()
        return d.entries.filter { it.date >= since }
            .groupBy { it.foodId }
            .filter { it.value.size >= 2 }
            .entries.sortedByDescending { (_, list) -> list.size + 2 * list.count { it.meal == meal } }
            .take(8)
            .map { (id, list) ->
                val e = list.last()
                Food(id, e.name, e.emoji, e.imageUrl, e.serving, e.kcal, e.protein, e.carbs, e.fat, e.course, e.tags)
            }
    }

    // ------------------------------------------------------------------ activity, weight, profile

    fun addExercise(activity: FoodDb.Activity, minutes: Int) {
        val kcal = Nutrition.exerciseKcal(activity.met, data.value.profile.weightKg, minutes)
        store.update { d ->
            d.copy(exercises = d.exercises + ExerciseEntry(nextId(), date.toString(), activity.name, activity.emoji, minutes, kcal))
        }
        messages.tryEmit(L("${activity.emoji} ${activity.name} $minutes phút · −$kcal kcal", "${activity.emoji} ${activity.name} $minutes min · −$kcal kcal"))
    }

    fun removeExercise(id: Long) = store.update { d -> d.copy(exercises = d.exercises.filterNot { it.id == id }) }

    fun logWeight(kg: Float) = store.update { d ->
        val today = LocalDate.now().toString()
        d.copy(
            profile = d.profile.copy(weightKg = kg),
            weights = (d.weights.filterNot { it.date == today } + WeightEntry(today, kg)).sortedBy { it.date },
        )
    }

    fun updateProfile(transform: (Profile) -> Profile) = store.update { d ->
        val updated = transform(d.profile)
        val today = LocalDate.now().toString()
        val weights = if (updated.weightKg != d.profile.weightKg || d.weights.isEmpty())
            (d.weights.filterNot { it.date == today } + WeightEntry(today, updated.weightKg)).sortedBy { it.date }
        else d.weights
        d.copy(profile = updated, weights = weights)
    }

    fun applyAdaptive(delta: Int) {
        updateProfile { it.copy(kcalAdjust = it.kcalAdjust + delta, kcalAdjustDate = LocalDate.now().toString()) }
        messages.tryEmit(L("Đã điều chỉnh mục tiêu ${if (delta > 0) "+" else ""}$delta kcal/ngày", "Target adjusted by ${if (delta > 0) "+" else ""}$delta kcal/day"))
    }

    // ------------------------------------------------------------------ watch / band (Health Connect)

    var wearableBusy by mutableStateOf(false)
    var wearableNote by mutableStateOf("")

    private var lastWearableSync = 0L

    fun syncWearable(announce: Boolean = false) {
        if (!health.available || wearableBusy) return
        val now = System.currentTimeMillis()
        if (!announce && now - lastWearableSync < WEARABLE_SYNC_INTERVAL_MS) return
        lastWearableSync = now
        viewModelScope.launch {
            wearableBusy = true
            try {
                if (health.granted().isEmpty()) {
                    updateProfile { it.copy(wearableLinked = false) }
                    return@launch
                }
                // A full month on first link or manual sync; routine refreshes only need the last few days.
                val days = health.readDays(if (announce || data.value.wearable.size < 7) 31 else 3)
                val body = health.readBody()
                store.update { d -> d.copy(wearable = d.wearable + days, profile = d.profile.copy(wearableLinked = true)) }
                body.heightCm?.takeIf { it in 100f..230f }?.let { h -> updateProfile { it.copy(heightCm = h) } }
                body.weightKg?.takeIf { it in 25f..300f && it != data.value.profile.weightKg }?.let { logWeight(it) }
                wearableNote = if (days.isEmpty()) L("Đã kết nối nhưng chưa có dữ liệu. Mở app của đồng hồ và bật đồng bộ với Health Connect.", "Connected, but no data yet. Open your watch's app and turn on syncing with Health Connect.")
                else L("Đã đồng bộ ${days.size} ngày gần nhất.", "Synced the last ${days.size} days.")
                if (announce) messages.tryEmit(wearableNote)
            } catch (e: Exception) {
                wearableNote = L("Không đọc được dữ liệu: ${e.message}", "Couldn't read the data: ${e.message}")
            } finally {
                wearableBusy = false
            }
        }
    }

    // ------------------------------------------------------------------ online search

    var onlineResults by mutableStateOf<List<Food>>(emptyList())
    var onlineBusy by mutableStateOf(false)
    var onlineNote by mutableStateOf("")

    fun clearOnline() {
        aiSuggestion = null
        onlineResults = emptyList()
        onlineNote = ""
    }

    fun searchOnline(query: String) {
        if (onlineBusy || query.isBlank()) return
        viewModelScope.launch {
            onlineBusy = true
            onlineNote = ""
            try {
                onlineResults = FoodSearch.search(query.trim())
                if (onlineResults.isEmpty()) {
                    onlineNote = if (hasAi) L("Không thấy sản phẩm nào trên Open Food Facts. Thử “AI gợi ý calo”.", "Nothing found on Open Food Facts. Try “AI calorie estimate”.") else L("Không tìm thấy. Bạn có thể tự nhập món này.", "Nothing found. You can enter this food yourself.")
                }
            } catch (e: Exception) {
                onlineNote = L("Không tìm được trên mạng (${e.message ?: "lỗi mạng"}).", "Online search failed (${e.message ?: "network error"}).")
            } finally {
                onlineBusy = false
            }
        }
    }

    /** Dish not in any database: ask the AI for an estimate. */
    /** The AI's calorie suggestion for a dish the app does not have, waiting for the user to add or adjust it. */
    var aiSuggestion by mutableStateOf<Food?>(null)

    fun estimateWithAi(query: String) {
        if (onlineBusy || query.isBlank()) return
        viewModelScope.launch {
            onlineBusy = true
            onlineNote = ""
            try {
                // A personal DeepSeek key answers chat; estimates go to the built-in AI, which knows the app's food table.
                val analysis = (if (photosOnHosted) hosted else ai()).estimateFood(query.trim())
                val item = analysis.items.firstOrNull()
                if (item == null) onlineNote = L("AI không nhận ra đây là món ăn. Hãy thử mô tả rõ hơn.", "The AI doesn't recognise this as a food. Try describing it more clearly.")
                else {
                    aiSuggestion = item.toFood("ai_${nextId()}")
                    onlineNote = analysis.advice
                }
            } catch (e: AiException) {
                onlineNote = e.message ?: L("Lỗi AI", "AI error")
            } finally {
                onlineBusy = false
            }
        }
    }

    private fun DetectedItem.toFood(id: String) = Food(
        id = id, name = name, emoji = emoji.ifBlank { "🍽️" }, serving = portion,
        kcal = kcal, protein = protein, carbs = carbs, fat = fat,
        course = course, tags = tags.filter { it in Tag.all }.toSet(), region = Region.ALL, custom = true,
    )

    // ------------------------------------------------------------------ meal photo

    var photo by mutableStateOf<PhotoState?>(null)

    /** A photo picked from the gallery: sent whole, scaled down. */
    fun analyzePhoto(uri: Uri) {
        viewModelScope.launch {
            val picked = withContext(Dispatchers.IO) { runCatching { loadBitmap(uri, GALLERY_MAX_SIDE) }.getOrNull() }
            if (picked == null) messages.tryEmit(L("Không mở được ảnh.", "Couldn't open the photo.")) else analyze(picked)
        }
    }

    /** A shot from the in-app camera, already cropped to the square around the guide circle. */
    fun analyzeCapture(square: Bitmap) {
        viewModelScope.launch {
            analyze(withContext(Dispatchers.Default) {
                if (square.width <= CAPTURE_SIDE) square else Bitmap.createScaledBitmap(square, CAPTURE_SIDE, CAPTURE_SIDE, true)
            })
        }
    }

    private suspend fun analyze(bitmap: Bitmap) {
        val state = PhotoState(bitmap)
        photo = state
        try {
            val jpeg = withContext(Dispatchers.Default) {
                ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 65, it) }.toByteArray()
            }
            val analysis = (if (photosOnHosted) hosted else ai()).analyzePhoto(jpeg, photoContext(data.value))
            state.items.addAll(analysis.items.map { ground(it) })
            state.advice = analysis.advice
            state.title = analysis.title
            state.score = analysis.score
            state.pros = analysis.pros
            state.cons = analysis.cons
            if (analysis.items.isEmpty()) state.error = L("AI không nhận ra món nào trong ảnh. Bạn có thể tự thêm món bên dưới bằng cách gõ hoặc nói.", "The AI found no food in the photo. You can add items below by typing or speaking.")
        } catch (e: AiException) {
            state.error = e.message
        } finally {
            state.loading = false
        }
    }

    /** The app's foods as the user sees them: their own and edited ones first, then the built-in table. */
    fun foods(d: AppData = data.value): List<Food> {
        val edited = d.customFoods.map { it.id }.toSet()
        return d.customFoods + library.filterNot { it.id in edited }
    }

    private fun DetectedItem.like(food: Food) = copy(
        name = food.label, emoji = food.emoji, portion = food.servingLabel, kcal = food.kcal, protein = food.protein,
        carbs = food.carbs, fat = food.fat, course = food.course, tags = food.tags.toList(),
    )

    /**
     * The AI says what the dish is and how much of it there is; the calories then come from the app's
     * own table whenever the dish is in it. A name the user corrected before is corrected the same way again.
     */
    private fun ground(ai: DetectedItem): PhotoItem {
        val d = data.value
        val key = ai.name.trim().lowercase()
        val foods = foods(d)
        val food = d.nameFixes[key]?.let { id -> foods.firstOrNull { it.id == id } } ?: FoodMatcher.best(ai.name, foods)
        val ratio = d.portionBias[key]
        val qty = (ai.qty.coerceIn(0.5f, 10f) * (ratio ?: 1f) * 2f).roundToInt().coerceAtLeast(1) / 2f
        return PhotoItem(ai, if (food != null) ai.like(food) else ai, food != null, qty, ratio != null).also { it.food = food }
    }

    /**
     * What helps the AI tell look-alike dishes apart: who is eating, which meal it is, and what this
     * person usually eats. A bowl of noodle soup at 7 am from someone who logs phở most mornings is phở.
     */
    private fun photoContext(d: AppData): String {
        val usual = frequentFoods(d, meal).take(10).joinToString { it.label }
        return profileSummary(d) + "; meal: ${Meal.labels[meal]}" + if (usual.isNotEmpty()) "; often eats: $usual" else ""
    }

    /** Adds a food the app knows to the photo result, for a dish the AI missed. */
    fun addPhotoFood(food: Food) {
        val item = DetectedItem(name = food.name).like(food).copy(confidence = 1f)
        photo?.items?.add(PhotoItem(item, item, fromTable = true, qty = 1f, learned = false).also { it.food = food })
    }

    var describing by mutableStateOf(false)
        private set

    /**
     * Works out a dish from the user's own words: a name, a list of ingredients with amounts, or a
     * comparison ("tương đương 1 tô phở"). Replaces [target], or adds a new dish when there is none.
     */
    fun describeDish(text: String, target: PhotoItem?, onDone: () -> Unit) {
        if (describing || text.isBlank()) return
        viewModelScope.launch {
            describing = true
            try {
                val item = (if (photosOnHosted) hosted else ai()).estimateFood(text.trim()).items.firstOrNull()
                if (item == null) messages.tryEmit(L("AI không nhận ra đây là món ăn. Hãy mô tả rõ hơn.", "The AI doesn't recognise this as a food. Please describe it more clearly."))
                else {
                    // The description already carries the amount and size, so the numbers are taken as they are.
                    val described = item.copy(qty = 1f, size = "medium")
                    if (target != null) {
                        target.corrected = true
                        target.item = described
                        target.food = null
                        target.fromTable = false
                        target.qty = 1f
                        target.size = "medium"
                        target.alternatives = emptyList()
                    } else {
                        photo?.items?.add(PhotoItem(described, described, fromTable = false, qty = 1f, learned = false))
                    }
                    onDone()
                }
            } catch (e: AiException) {
                messages.tryEmit(e.message ?: L("Lỗi AI", "AI error"))
            } finally {
                describing = false
            }
        }
    }

    /** Ingredients of a dish for the ingredient editor, or null (with a message shown) when the AI could not say. */
    suspend fun breakdown(text: String): List<Ingredient>? = try {
        (if (photosOnHosted) hosted else ai()).breakdown(text.trim()).ifEmpty {
            messages.tryEmit(L("AI không tách được thành phần. Hãy ghi rõ hơn, ví dụ “thịt vịt 300g”.", "The AI couldn't split this into ingredients. Be more specific, e.g. “duck meat 300g”."))
            null
        }
    } catch (e: AiException) {
        messages.tryEmit(e.message ?: L("Lỗi AI", "AI error"))
        null
    }

    /** Replaces a photo dish's numbers with what its ingredients add up to; the result is one serving of exactly that. */
    fun applyIngredients(p: PhotoItem, totals: DishTotals) {
        p.userBuilt = true
        p.item = p.item.copy(kcal = totals.kcal, protein = totals.protein, carbs = totals.carbs, fat = totals.fat, portion = totals.portion, qty = 1f, grams = 0)
        p.food = null
        p.fromTable = false
        p.qty = 1f
        p.size = "medium"
    }

    /** Switches the dish to one of the suggested alternatives. */
    fun pickAlternative(p: PhotoItem, name: String) {
        val previous = p.item.name
        val food = FoodMatcher.best(name, foods())
        if (food != null) correctPhotoItem(p, food) else renamePhotoItem(p, name)
        // The dish it was becomes an alternative, so a mis-tap is one tap to undo.
        p.alternatives = (p.alternatives - name + previous).distinct()
    }

    /** The user says this dish is actually [food]. */
    fun correctPhotoItem(p: PhotoItem, food: Food) {
        p.corrected = true
        p.item = p.item.like(food)
        p.food = food
        p.fromTable = true
        store.update { d -> d.copy(nameFixes = d.nameFixes + (p.aiName.trim().lowercase() to food.id)) }
    }

    /** The user gives the dish a name the app does not know: keep the AI's numbers under that name. */
    fun renamePhotoItem(p: PhotoItem, name: String) {
        p.corrected = true
        p.item = p.item.copy(name = name.trim())
        p.food = null
        p.fromTable = false
    }

    fun commitPhoto() {
        val state = photo ?: return
        val picked = state.items.filter { it.checked }
        // Learn from corrections: remember how this user sizes each dish relative to what the AI counted.
        val resized = picked.filter { it.item.qty > 0f && (it.qty != it.item.qty || it.learned) }
        if (resized.isNotEmpty()) store.update { d ->
            d.copy(portionBias = d.portionBias + resized.associate { p ->
                val key = p.aiName.trim().lowercase()
                val ratio = p.qty / p.item.qty.coerceIn(0.5f, 10f)
                key to (d.portionBias[key]?.let { (it + ratio) / 2f } ?: ratio)
            })
        }
        // Share what the dishes really were (names only) once the user confirms, not on every tap along the way.
        val fixes = picked.filter { it.corrected && !it.item.name.equals(it.aiName, ignoreCase = true) }.map { it.ai to it.item.name }
        if (photosOnHosted && fixes.isNotEmpty()) viewModelScope.launch { fixes.forEach { (ai, chosen) -> hosted.sendCorrection(ai, chosen) } }
        picked.filter { it.userBuilt && it.food == null }.forEach { offerToLibrary(it.item.toFood("ai_${nextId()}")) }
        if (picked.isNotEmpty()) addMeal(picked.map { (it.food ?: it.item.toFood("ai_${nextId()}")) to it.amount }, meal)
        photo = null
    }

    private fun loadBitmap(uri: Uri, maxSide: Int): Bitmap {
        val resolver = getApplication<Application>().contentResolver
        val raw = if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder applies the EXIF rotation, so portrait shots arrive upright.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > maxSide) {
                    val k = maxSide.toFloat() / longest
                    decoder.setTargetSize((info.size.width * k).roundToInt(), (info.size.height * k).roundToInt())
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: error("decode failed")
        }
        val longest = max(raw.width, raw.height)
        if (longest <= maxSide) return raw
        val k = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(raw, (raw.width * k).roundToInt(), (raw.height * k).roundToInt(), true)
    }

    // ------------------------------------------------------------------ AI coach

    val chat = mutableStateListOf<ChatTurn>()
    var chatBusy by mutableStateOf(false)

    fun sendChat(text: String) {
        if (chatBusy || text.isBlank()) return
        chat += ChatTurn(true, text.trim())
        viewModelScope.launch {
            chatBusy = true
            try {
                val context = if (usingHosted) coachData(data.value) else coachPersona(data.value) + "\n\n" + coachData(data.value)
                // Older turns are resent, and paid for, with every new message; the last few carry the thread.
                chat += ChatTurn(false, ai().chat(context, chat.toList().takeLast(MAX_CHAT_TURNS).dropWhile { !it.fromUser }))
            } catch (e: AiException) {
                chat.removeAt(chat.lastIndex)
                messages.tryEmit(e.message ?: L("Lỗi AI", "AI error"))
            } finally {
                chatBusy = false
            }
        }
    }

    private fun profileSummary(d: AppData): String {
        val p = d.profile
        val parts = mutableListOf(
            "${if (p.male) "male" else "female"}, ${Nutrition.age(p)} years, ${p.heightCm.roundToInt()} cm, ${p.weightKg} kg",
            "goal: ${p.goal}", "daily target ${Nutrition.targetKcal(p)} kcal",
        )
        if (p.conditions.isNotEmpty()) parts += "conditions: " + p.conditions.mapNotNull { Condition.labels[it] }.joinToString()
        if (p.allergies.isNotEmpty()) parts += "allergies: " + p.allergies.mapNotNull { Tag.allergens[it] }.joinToString()
        if (p.diet != Diet.NONE) parts += "diet: ${Diet.labels[p.diet]}"
        return parts.joinToString("; ")
    }

    private fun coachPersona(d: AppData): String {
        val p = d.profile
        return listOf(
            "You are the in-app coach of CaloCare, acting as both a registered dietitian and a sports coach.",
            "Reply in ${if (com.calocare.app.Lang.en) "English" else "Vietnamese"}, warm and concrete, in short paragraphs or brief lists that read well on a phone. Give specific dishes, portions and numbers rather than general principles.",
            "You are not a substitute for their doctor: for medication, blood-sugar targets or symptoms, give general guidance and tell them to check with their clinician. Never suggest eating below ${if (p.male) 1500 else 1200} kcal a day.",
            "Respect their conditions, allergies and diet in every suggestion, and point out conflicts in what they have eaten.",
            "Keep each reply under 120 words, unless the user asks for a full menu or plan.",
        ).joinToString("\n")
    }

    /** The user's numbers in as few tokens as still read unambiguously: this is sent with every chat message. */
    private fun coachData(d: AppData): String {
        val p = d.profile
        val macros = Nutrition.macroTargets(p)
        val lines = mutableListOf(
            "User: ${p.name.ifBlank { "(no name)" }}, prefers ${if (p.region == Region.INTL) "international / Western" else "Vietnamese"} food (suggest dishes they can easily find).",
            "Profile: ${profileSummary(d)}; BMI ${"%.1f".format(Nutrition.bmi(p))}; target weight ${p.targetWeightKg} kg.",
            "Daily macro targets: P ${macros.protein}g, C ${macros.carbs}g, F ${macros.fat}g.",
            "Last 7 days (date: kcal in/out, protein/carbs/fat g, steps); days with nothing logged are omitted:",
        )
        for (back in 6 downTo 0) {
            val day = LocalDate.now().minusDays(back.toLong())
            val t = d.entriesOn(day).totals()
            if (t.kcal <= 0f) continue
            val out = Nutrition.kcalOut(d, day)
            lines += "${day.monthValue}-${day.dayOfMonth}: ${t.kcal.roundToInt()}/${out.total}, ${t.protein.roundToInt()}/${t.carbs.roundToInt()}/${t.fat.roundToInt()}, ${out.steps}"
        }
        val eatenToday = d.entriesOn(LocalDate.now()).totals().kcal.roundToInt()
        lines += "Today so far: $eatenToday kcal eaten, ${Nutrition.targetKcal(p) - eatenToday} kcal left of the target."
        lines += "Eaten today: " + d.entriesOn(LocalDate.now()).joinToString { "${it.label} x${fmtQty(it.qty)} (${Meal.labels[it.meal]})" }.ifBlank { "nothing logged yet" }
        return lines.joinToString("\n")
    }

    init {
        syncLibrary()
        // Re-sends an existing subscription, so renewals and reinstalls keep Pro on.
        if (hostedConfigured) billing.refresh()
    }

    companion object {
        // Image tokens grow with pixel area. The guide circle guarantees the frame is all food, so a
        // 768 px square measured about 4 points more accurate than 512 px on the test set (server/test/eval) for ~10% more cost.
        private const val CAPTURE_SIDE = 768
        private const val GALLERY_MAX_SIDE = 768
        private const val MAX_CHAT_TURNS = 10
        private const val WEARABLE_SYNC_INTERVAL_MS = 15 * 60_000L

        fun fmtQty(q: Float): String = if (q % 1f == 0f) q.toInt().toString() else "%.2f".format(q).trimEnd('0').trimEnd('.', ',')
    }
}
