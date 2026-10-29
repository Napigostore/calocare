package com.calocare.app.net

import com.calocare.app.L
import android.util.Base64
import com.calocare.app.data.Course
import com.calocare.app.data.Food
import com.calocare.app.data.Region
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A dish as the server's library holds it. */
@Serializable
data class RemoteDish(
    val id: String,
    val name: String,
    val emoji: String = "🍽️",
    val serving: String = "1 phần",
    val kcal: Int,
    val protein_g: Float = 0f,
    val carbs_g: Float = 0f,
    val fat_g: Float = 0f,
    val course: String = Course.MAIN,
    val tags: List<String> = emptyList(),
    val region: String = Region.VN,
) {
    fun toFood() = Food(id, name, emoji, null, serving, kcal, protein_g, carbs_g, fat_g, course, tags.toSet(), region)
}

/** What changed in the library since [version] was last seen. */
@Serializable
data class LibraryUpdate(val version: Long = 0, val dishes: List<RemoteDish> = emptyList(), val removed: List<String> = emptyList())

/** What the CaloCare server says this device may still use. */
@Serializable
data class Quota(
    val trial_photos_left: Int = 0,
    val trial_chats_left: Int = 0,
    val photos_left_today: Int = 0,
    val chats_left_today: Int = 0,
    val pro: Boolean = false,
    val pro_until: Long = 0,
    val free_photos_per_day: Int = 2,
    val free_chats_per_day: Int = 5,
)

/** Weekly/monthly nutrition summary for AI analysis. */
@Serializable
data class NutritionSummary(
    val period: String, // "week" or "month"
    val startDate: String, // yyyy-MM-dd
    val endDate: String,
    val daysCount: Int,
    val totalDays: Int,
    val totalKcal: Int,
    val totalProtein: Float,
    val totalCarbs: Float,
    val totalFat: Float,
    val avgKcal: Float,
    val avgProtein: Float,
    val avgCarbs: Float,
    val avgFat: Float,
    val mealsPerDay: Float,
    val courseBreakdown: Map<String, Float>,
)

/** AI-generated nutrition coaching insights. */
@Serializable
data class InsightResult(
    val title: String,
    val patterns: List<String> = emptyList(),
    val strengths: List<String> = emptyList(),
    val improvements: List<String> = emptyList(),
    val substitutionTip: String? = null,
)

/** User metrics for personalized macro calculation. */
@Serializable
data class UserMetrics(
    val weightKg: Float,
    val heightCm: Float,
    val age: Int,
    val male: Boolean,
    val activityLevel: Float,
    val goal: String, // "lose", "gain", "maintain", "gym", "run"
    val weeklyRateKg: Float? = null,
    val dietRestriction: String? = null,
)

/** Personalized macro targets. */
@Serializable
data class MacroTargets(
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val kcal: Int,
    val explanation: String,
)

/** Daily nutrition data for habit scoring. */
@Serializable
data class DayNutrition(
    val date: String,
    val foods: List<String>,
    val kcal: Int,
    val protein_g: Float,
    val carbs_g: Float,
    val fat_g: Float,
)

/** User nutrition goals for habit scoring. */
@Serializable
data class UserGoal(
    val kcal: Int,
    val protein_g: Float,
    val carbs_g: Float,
    val fat_g: Float,
)

/** Eating habit score for gamification. */
@Serializable
data class HabitScore(
    val diversity: Int,
    val timing: Int,
    val macroBalance: Int,
    val overall: Int,
    val streak: Int,
    val nextBadge: String? = null,
)

/** A logged food sent to the server for a healthier swap. */
@Serializable
data class DetectedFood(
    val name: String,
    val kcal: Int,
    val protein_g: Float,
    val carbs_g: Float,
    val fat_g: Float,
    val fiber_g: Float? = null,
    val tags: List<String> = emptyList(),
)

/** A healthier alternative to a logged food. */
@Serializable
data class Substitution(
    val name: String,
    val kcal: Int,
    val protein_g: Float,
    val carbs_g: Float,
    val fat_g: Float,
    val reason: String = "",
    val benefits: List<String> = emptyList(),
)

/** Recipe item (food with quantity). */
@Serializable
data class RecipeItem(
    val foodId: String,
    val qty: Float,
    val name: String? = null,
    val kcal: Int? = null,
    val protein_g: Float? = null,
    val carbs_g: Float? = null,
    val fat_g: Float? = null,
)

/** Saved recipe for meal planning. */
@Serializable
data class Recipe(
    val id: String,
    val name: String,
    val description: String? = null,
    val items: List<RecipeItem> = emptyList(),
    val servings: Int = 1,
    val prepTime: Int? = null,
    val cookTime: Int? = null,
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** Recipe list item for display. */
@Serializable
data class RecipeListItem(
    val id: String,
    val name: String,
    val kcal: Int,
    val servings: Int,
    val tags: List<String> = emptyList(),
)

/** Shopping list item. */
@Serializable
data class ShoppingListItem(
    val foodId: String? = null,
    val name: String,
    val unit: String = "g",
    val quantity: Float,
    val checked: Boolean = false,
)

/** Shopping list generated from recipes. */
@Serializable
data class ShoppingList(
    val recipes: List<String> = emptyList(),
    val items: List<ShoppingListItem> = emptyList(),
    val generatedAt: Long = 0,
)

/** Nutrition report data. */
@Serializable
data class NutritionReport(
    val period: String,
    val totalDays: Int,
    val avgKcal: Float,
    val avgProtein: Float,
    val avgCarbs: Float,
    val avgFat: Float,
    val maxKcal: Int,
    val minKcal: Int,
    val macroBreakdown: Map<String, Float> = emptyMap(),
    val consistencyScore: Float,
)

/**
 * The built-in AI: talks to the CaloCare server (see /server), which holds the provider key and
 * enforces the per-device allowance. No key is stored in the app.
 */
class HostedClient(
    private val baseUrl: String,
    private val appKey: String,
    private val deviceId: String,
    private val onQuota: (Quota) -> Unit,
    private val onProRequired: () -> Unit = {},
) : AiClient {
    override val supportsPhotos = true

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun analyzePhoto(jpeg: ByteArray, userContext: String): MealAnalysis = analysis(
        post("/v1/photo", buildJsonObject {
            put("image", Base64.encodeToString(jpeg, Base64.NO_WRAP))
            put("context", userContext)
        })
    )

    override suspend fun estimateFood(name: String): MealAnalysis = analysis(post("/v1/estimate", buildJsonObject { put("name", name) }))

    override suspend fun chat(system: String, history: List<ChatTurn>): String {
        val result = post("/v1/chat", buildJsonObject {
            put("context", system)
            putJsonArray("history") {
                history.forEach { add(buildJsonObject { put("role", if (it.fromUser) "user" else "assistant"); put("text", it.text) }) }
            }
        })
        return result.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: throw AiException(L("AI không trả về nội dung. Hãy thử lại.", "The AI returned nothing. Please try again."))
    }

    /**
     * Tells the server the AI called a dish [aiName] and the user changed it to [chosen]. Only the two
     * names are sent. Once several users make the same correction, everyone gets the right name.
     */
    suspend fun sendCorrection(ai: DetectedItem, chosen: String) {
        runCatching {
            request("/v1/correction", buildJsonObject {
                put("ai_name", ai.name)
                put("chosen", chosen)
                // Lets the server judge whether the two dishes are plausibly confused when it does not know the AI's name.
                put("ai", buildJsonObject { put("kcal", ai.kcal); put("protein_g", ai.protein); put("carbs_g", ai.carbs); put("fat_g", ai.fat) })
            })
        }
    }

    override suspend fun breakdown(text: String): List<Ingredient> {
        val result = post("/v1/breakdown", buildJsonObject { put("text", text) })
        return try {
            json.decodeFromJsonElement<List<Ingredient>>(result.jsonObject["ingredients"] ?: return emptyList())
        } catch (e: SerializationException) {
            throw AiException(L("Không đọc được kết quả AI. Hãy thử lại.", "Couldn't read the AI's answer. Please try again."))
        }
    }

    /** Dishes added, changed or removed in the library since the phone's copy; null when the server cannot be reached. */
    suspend fun library(since: Long): LibraryUpdate? =
        runCatching { json.decodeFromJsonElement<LibraryUpdate>(request("/v1/dishes?since=$since", null)) }.getOrNull()

    /**
     * Offers a dish the user created to the shared library: its name and nutrition, nothing about the
     * user. It reaches other people only if the library's owner approves it.
     */
    suspend fun submitDish(food: Food) {
        runCatching {
            request("/v1/dish", buildJsonObject {
                put("name", food.name); put("emoji", food.emoji); put("serving", food.serving); put("kcal", food.kcal)
                put("protein_g", food.protein); put("carbs_g", food.carbs); put("fat_g", food.fat); put("course", food.course)
                putJsonArray("tags") { food.tags.forEach { add(JsonPrimitive(it)) } }
            })
        }
    }

    suspend fun refreshQuota() {
        runCatching { request("/v1/quota", null) }
    }

    /** Sends a Google Play purchase to the server, which checks it with Google and turns Pro on for this device. */
    suspend fun activatePro(purchaseToken: String): Boolean =
        runCatching { request("/v1/pro", buildJsonObject { put("platform", "android"); put("token", purchaseToken) }) }.isSuccess

    /** Get AI coaching insights for a week/month of nutrition data. */
    suspend fun reportInsights(summary: NutritionSummary): InsightResult? = runCatching {
        val summaryJson = buildJsonObject {
            put("period", summary.period)
            put("startDate", summary.startDate)
            put("endDate", summary.endDate)
            put("daysCount", summary.daysCount)
            put("totalDays", summary.totalDays)
            put("totalKcal", summary.totalKcal)
            put("totalProtein", summary.totalProtein)
            put("totalCarbs", summary.totalCarbs)
            put("totalFat", summary.totalFat)
            put("avgKcal", summary.avgKcal)
            put("avgProtein", summary.avgProtein)
            put("avgCarbs", summary.avgCarbs)
            put("avgFat", summary.avgFat)
            put("mealsPerDay", summary.mealsPerDay)
            putJsonArray("courseBreakdown") {
                summary.courseBreakdown.forEach { (course, pct) ->
                    add(buildJsonObject { put(course, pct) })
                }
            }
        }
        val result = post("/v1/report/insights", buildJsonObject { put("summary", summaryJson) })
        json.decodeFromJsonElement<InsightResult>(result)
    }.getOrNull()

    /** Get personalized macro targets based on user metrics and goal. */
    suspend fun macroTargets(metrics: UserMetrics): MacroTargets? = runCatching {
        val metricsJson = buildJsonObject {
            put("weightKg", metrics.weightKg)
            put("heightCm", metrics.heightCm)
            put("age", metrics.age)
            put("male", metrics.male)
            put("activityLevel", metrics.activityLevel)
            put("goal", metrics.goal)
            if (metrics.weeklyRateKg != null) put("weeklyRateKg", metrics.weeklyRateKg)
            if (metrics.dietRestriction != null) put("dietRestriction", metrics.dietRestriction)
        }
        val result = request("/v1/macros", buildJsonObject { put("metrics", metricsJson) })
        json.decodeFromJsonElement<MacroTargets>(result.getValue("macros"))
    }.getOrNull()

    /** Get eating habit score for gamification. */
    suspend fun habitScore(today: DayNutrition, target: UserGoal, pastDays: List<DayNutrition> = emptyList()): HabitScore? = runCatching {
        val todayJson = buildJsonObject {
            put("date", today.date)
            putJsonArray("foods") { today.foods.forEach { add(JsonPrimitive(it)) } }
            put("kcal", today.kcal)
            put("protein_g", today.protein_g)
            put("carbs_g", today.carbs_g)
            put("fat_g", today.fat_g)
        }
        val targetJson = buildJsonObject {
            put("kcal", target.kcal)
            put("protein_g", target.protein_g)
            put("carbs_g", target.carbs_g)
            put("fat_g", target.fat_g)
        }
        val result = request("/v1/habit-score", buildJsonObject {
            put("today", todayJson)
            put("target", targetJson)
            putJsonArray("pastDays") {
                pastDays.forEach { day ->
                    add(buildJsonObject {
                        put("date", day.date)
                        putJsonArray("foods") { day.foods.forEach { add(JsonPrimitive(it)) } }
                        put("kcal", day.kcal)
                        put("protein_g", day.protein_g)
                        put("carbs_g", day.carbs_g)
                        put("fat_g", day.fat_g)
                    })
                }
            }
        })
        json.decodeFromJsonElement<HabitScore>(result.getValue("score"))
    }.getOrNull()

    /** A healthier swap for a logged food; null when the server cannot offer one. */
    suspend fun substitute(food: DetectedFood): Substitution? = runCatching {
        val result = post("/v1/substitute", buildJsonObject {
            put("food", buildJsonObject {
                put("name", food.name)
                put("kcal", food.kcal)
                put("protein_g", food.protein_g)
                put("carbs_g", food.carbs_g)
                put("fat_g", food.fat_g)
                food.fiber_g?.let { put("fiber_g", it) }
                putJsonArray("tags") { food.tags.forEach { add(JsonPrimitive(it)) } }
            })
        })
        json.decodeFromJsonElement<Substitution>(result)
    }.getOrNull()

    /** Get user's saved recipes. */
    suspend fun getRecipes(): List<RecipeListItem>? = runCatching {
        val result = request("/v1/recipes", null)
        json.decodeFromJsonElement<List<RecipeListItem>>(result.jsonObject["recipes"] ?: return@runCatching null)
    }.getOrNull()

    /** Save or update a recipe. */
    suspend fun saveRecipe(recipe: Recipe): String? = runCatching {
        val recipeJson = buildJsonObject {
            put("id", recipe.id)
            put("name", recipe.name)
            put("description", recipe.description ?: "")
            putJsonArray("items") {
                recipe.items.forEach { item ->
                    add(buildJsonObject {
                        put("foodId", item.foodId)
                        put("qty", item.qty)
                        if (item.name != null) put("name", item.name)
                        if (item.kcal != null) put("kcal", item.kcal)
                        if (item.protein_g != null) put("protein_g", item.protein_g)
                        if (item.carbs_g != null) put("carbs_g", item.carbs_g)
                        if (item.fat_g != null) put("fat_g", item.fat_g)
                    })
                }
            }
            put("servings", recipe.servings)
            if (recipe.prepTime != null) put("prepTime", recipe.prepTime)
            if (recipe.cookTime != null) put("cookTime", recipe.cookTime)
            putJsonArray("tags") { recipe.tags.forEach { add(JsonPrimitive(it)) } }
        }
        val result = request("/v1/recipes", buildJsonObject { put("recipe", recipeJson) })
        result["id"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    /** Get a single recipe. */
    suspend fun getRecipe(id: String): Recipe? = runCatching {
        val result = request("/v1/recipes/$id", null)
        json.decodeFromJsonElement<Recipe>(result.jsonObject["recipe"] ?: return@runCatching null)
    }.getOrNull()

    /** Delete a recipe. */
    suspend fun deleteRecipe(id: String): Boolean = runCatching {
        request("/v1/recipes/$id", null, delete = true)
        true
    }.getOrDefault(false)

    /** Generate shopping list from recipes. */
    suspend fun generateShoppingList(recipeIds: List<String>): ShoppingList? = runCatching {
        val result = request("/v1/shopping-list", buildJsonObject {
            putJsonArray("recipeIds") { recipeIds.forEach { add(JsonPrimitive(it)) } }
        })
        json.decodeFromJsonElement<ShoppingList>(result.getValue("shoppingList"))
    }.getOrNull()

    /** Get nutrition report. */
    suspend fun nutritionReport(entries: List<DayNutrition>, period: String): NutritionReport? = runCatching {
        val entriesJson = buildJsonObject {
            putJsonArray("entries") {
                entries.forEach { day ->
                    add(buildJsonObject {
                        put("date", day.date)
                        put("kcal", day.kcal)
                        put("protein_g", day.protein_g)
                        put("carbs_g", day.carbs_g)
                        put("fat_g", day.fat_g)
                    })
                }
            }
            put("period", period)
        }
        val result = request("/v1/report/nutrition", entriesJson)
        json.decodeFromJsonElement<NutritionReport>(result.getValue("report"))
    }.getOrNull()

    private fun analysis(result: JsonElement): MealAnalysis = try {
        json.decodeFromJsonElement<MealAnalysis>(result)
    } catch (e: SerializationException) {
        throw AiException(L("Không đọc được kết quả AI. Hãy thử lại.", "Couldn't read the AI's answer. Please try again."))
    }

    private suspend fun post(path: String, body: JsonObject): JsonElement =
        request(path, body)["result"] ?: throw AiException(L("AI không trả về nội dung. Hãy thử lại.", "The AI returned nothing. Please try again."))

    private suspend fun request(path: String, body: JsonObject?, delete: Boolean = false): JsonObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(baseUrl.trimEnd('/') + path).header("X-App-Key", appKey).header("X-Device", deviceId).header("X-Lang", com.calocare.app.Lang.code)
        when {
            delete -> builder.delete()
            body != null -> builder.post(body.toString().toRequestBody("application/json".toMediaType()))
        }
        try {
            http.newCall(builder.build()).execute().use { response ->
                val parsed = runCatching { json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject }.getOrNull()
                parsed?.get("quota")?.let { q -> runCatching { onQuota(json.decodeFromJsonElement<Quota>(q)) } }
                if (!response.isSuccessful || parsed == null) {
                    // The server's messages are already written for the user (quota reached, busy, ...).
                    val code = parsed?.get("error")?.jsonPrimitive?.contentOrNull
                    if (code == "pro_required") onProRequired()
                    throw AiException(code = code, message = parsed?.get("message")?.jsonPrimitive?.contentOrNull ?: L("Máy chủ AI đang gặp sự cố (${response.code}). Hãy thử lại sau.", "The AI server has a problem (${response.code}). Please try again later."))
                }
                parsed
            }
        } catch (e: IOException) {
            throw AiException(L("Không kết nối được tới máy chủ AI. Kiểm tra mạng.", "Couldn't reach the AI server. Check your connection."))
        }
    }
}
