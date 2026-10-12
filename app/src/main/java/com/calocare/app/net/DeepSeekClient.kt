package com.calocare.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** DeepSeek (OpenAI-compatible chat completions). Text only: no meal photo analysis. */
class DeepSeekClient(private val apiKey: String) : AiClient {
    override val supportsPhotos = false

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun analyzePhoto(jpeg: ByteArray, userContext: String): MealAnalysis =
        throw AiException("DeepSeek chưa đọc được ảnh. Chụp ảnh bữa ăn cần API key của Anthropic.")

    override suspend fun estimateFood(name: String): MealAnalysis {
        val system = """
            Reply with a single JSON object and nothing else, shaped like:
            {"items":[{"name":"","emoji":"","portion":"","kcal":0,"protein_g":0,"carbs_g":0,"fat_g":0,"course":"main","tags":[],"confidence":0.7}],"advice":""}
        """.trimIndent()
        val text = complete(system, listOf(ChatTurn(true, AiClient.estimatePrompt(name))), jsonMode = true)
        return try {
            json.decodeFromString<MealAnalysis>(text)
        } catch (e: SerializationException) {
            throw AiException("Không đọc được kết quả AI. Hãy thử lại.")
        }
    }

    override suspend fun chat(system: String, history: List<ChatTurn>): String = complete(system, history, jsonMode = false)

    private suspend fun complete(system: String, history: List<ChatTurn>, jsonMode: Boolean): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", MODEL)
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                history.forEach { add(buildJsonObject { put("role", if (it.fromUser) "user" else "assistant"); put("content", it.text) }) }
            }
            if (jsonMode) putJsonObject("response_format") { put("type", "json_object") }
        }
        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when (response.code) {
                    in 200..299 -> Unit
                    401 -> throw AiException("API key DeepSeek không hợp lệ. Kiểm tra lại trong Hồ sơ → Trợ lý AI.")
                    402 -> throw AiException("Tài khoản DeepSeek đã hết số dư.")
                    429 -> throw AiException("Đang quá tải lượt gọi AI. Thử lại sau ít phút.")
                    else -> throw AiException("Lỗi từ DeepSeek (${response.code}): ${text.take(150)}")
                }
                val content = runCatching {
                    json.parseToJsonElement(text).jsonObject["choices"]!!.jsonArray[0].jsonObject["message"]!!
                        .jsonObject["content"]!!.jsonPrimitive.contentOrNull
                }.getOrNull()
                content?.takeIf { it.isNotBlank() } ?: throw AiException("AI không trả về nội dung. Hãy thử lại.")
            }
        } catch (e: IOException) {
            throw AiException("Không kết nối được tới DeepSeek. Kiểm tra mạng.")
        }
    }

    private companion object {
        const val MODEL = "deepseek-chat"
    }
}
