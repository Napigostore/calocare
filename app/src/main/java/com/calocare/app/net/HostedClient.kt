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

    private fun analysis(result: JsonElement): MealAnalysis = try {
        json.decodeFromJsonElement<MealAnalysis>(result)
    } catch (e: SerializationException) {
        throw AiException(L("Không đọc được kết quả AI. Hãy thử lại.", "Couldn't read the AI's answer. Please try again."))
    }

    private suspend fun post(path: String, body: JsonObject): JsonElement =
        request(path, body)["result"] ?: throw AiException(L("AI không trả về nội dung. Hãy thử lại.", "The AI returned nothing. Please try again."))

    private suspend fun request(path: String, body: JsonObject?): JsonObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(baseUrl.trimEnd('/') + path).header("X-App-Key", appKey).header("X-Device", deviceId).header("X-Lang", com.calocare.app.Lang.code)
        if (body != null) builder.post(body.toString().toRequestBody("application/json".toMediaType()))
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
