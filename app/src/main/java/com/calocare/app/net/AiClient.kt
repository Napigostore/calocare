package com.calocare.app.net

import com.calocare.app.L
import android.util.Base64
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.calocare.app.data.Course
import com.calocare.app.data.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DetectedItem(
    val name: String,
    val emoji: String = "🍽️",
    val portion: String = "1 phần",
    /** How many servings were seen; [kcal] and the macros are for one serving. */
    val qty: Float = 1f,
    /** small, medium or large for its kind; scales the calories of one serving. */
    val size: String = "medium",
    /** Estimated total weight of the dish as photographed, for display. */
    val grams: Int = 0,
    @SerialName("fiber_g") val fiber: Float = 0f,
    @SerialName("sugar_g") val sugar: Float = 0f,
    @SerialName("sodium_mg") val sodium: Float = 0f,
    val kcal: Int = 0,
    @SerialName("protein_g") val protein: Float = 0f,
    @SerialName("carbs_g") val carbs: Float = 0f,
    @SerialName("fat_g") val fat: Float = 0f,
    val course: String = Course.MAIN,
    val tags: List<String> = emptyList(),
    val confidence: Float = 0.5f,
    /** Other dishes this could be, when the AI was not sure. */
    val alternatives: List<String> = emptyList(),
)

@Serializable
data class MealAnalysis(
    val items: List<DetectedItem> = emptyList(),
    val advice: String = "",
    /** The meal as a whole, when the photo was read by the built-in AI. */
    val title: String = "",
    val score: Int = -1,
    val pros: List<String> = emptyList(),
    val cons: List<String> = emptyList(),
)

/** One ingredient of a dish: its weight, and its nutrition per 100 g. */
@Serializable
data class Ingredient(
    val name: String,
    val grams: Int,
    @SerialName("kcal_100g") val kcal100: Float = 0f,
    @SerialName("protein_100g") val protein100: Float = 0f,
    @SerialName("carbs_100g") val carbs100: Float = 0f,
    @SerialName("fat_100g") val fat100: Float = 0f,
) {
    val kcal get() = kcal100 * grams / 100f
    val protein get() = protein100 * grams / 100f
    val carbs get() = carbs100 * grams / 100f
    val fat get() = fat100 * grams / 100f
}

data class ChatTurn(val fromUser: Boolean, val text: String)

class AiException(message: String) : Exception(message)

/** AI features: meal photo analysis, calorie estimates for unknown dishes, nutrition coach. */
interface AiClient {
    /** False for text-only providers; the photo feature is then unavailable. */
    val supportsPhotos: Boolean
    suspend fun analyzePhoto(jpeg: ByteArray, userContext: String): MealAnalysis
    suspend fun estimateFood(name: String): MealAnalysis
    suspend fun chat(system: String, history: List<ChatTurn>): String

    /** Splits a dish (or a few named ingredients) into ingredients with weights. Only the built-in AI does this. */
    suspend fun breakdown(text: String): List<Ingredient> = throw AiException(L("Tính theo thành phần cần AI có sẵn của ứng dụng.", "Ingredient breakdown needs the app's built-in AI."))

    companion object {
        // Without a personal key the view model uses HostedClient (the CaloCare server) instead.
        /** Anthropic keys start with "sk-ant-"; any other key is treated as a DeepSeek key. */
        fun isClaudeKey(key: String) = key.startsWith("sk-ant-")
        operator fun invoke(apiKey: String): AiClient = if (isClaudeKey(apiKey)) ClaudeClient(apiKey) else DeepSeekClient(apiKey)

        internal val tagGuide = """
            Tag every item with all that apply:
            allergens: peanut, nuts (tree nuts), shellfish, fish (including fish sauce as a main seasoning), milk, egg, gluten, soy, sesame;
            properties: high_sugar, high_gi, high_carb, high_sodium, high_purine, high_satfat, fried, meat, pork,
            animal (animal-derived but not meat: egg, dairy, honey), alcohol.
            course is one of: soup (soups and starters), veg, protein, main (starch-based or one-dish meals), fruit, dessert, drink, snack.
        """.trimIndent()

        internal fun estimatePrompt(name: String) = """
            Estimate the nutrition of one typical serving of this food or dish: "$name".
            Return exactly one item, or an empty list if this is not a food. Give its name in ${if (com.calocare.app.Lang.en) "English" else "Vietnamese"}.
            $tagGuide
            Leave advice as a short note on what the estimate assumes.
        """.trimIndent()
    }
}

class ClaudeClient(apiKey: String) : AiClient {
    override val supportsPhotos = true

    private val client: AnthropicClient = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val analysisSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "required" to listOf("items", "advice"),
        "properties" to mapOf(
            "advice" to mapOf("type" to "string", "description" to "One or two short sentences of advice in ${if (com.calocare.app.Lang.en) "English" else "Vietnamese"}"),
            "items" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "additionalProperties" to false,
                    "required" to listOf(
                        "name", "emoji", "portion", "kcal", "protein_g", "carbs_g", "fat_g", "course", "tags", "confidence",
                    ),
                    "properties" to mapOf(
                        "name" to mapOf("type" to "string", "description" to "Dish name in ${if (com.calocare.app.Lang.en) "English" else "Vietnamese"}"),
                        "emoji" to mapOf("type" to "string", "description" to "A single food emoji"),
                        "portion" to mapOf("type" to "string", "description" to "Estimated portion in ${if (com.calocare.app.Lang.en) "English" else "Vietnamese"}, e.g. '1 bowl (150g)'"),
                        "kcal" to mapOf("type" to "integer", "description" to "Calories for that portion"),
                        "protein_g" to mapOf("type" to "number"),
                        "carbs_g" to mapOf("type" to "number"),
                        "fat_g" to mapOf("type" to "number"),
                        "course" to mapOf("type" to "string", "enum" to Course.all),
                        "tags" to mapOf("type" to "array", "items" to mapOf("type" to "string", "enum" to Tag.all)),
                        "confidence" to mapOf("type" to "number", "description" to "0 to 1"),
                    ),
                ),
            ),
        ),
    )

    private val analysisFormat: OutputConfig by lazy {
        val schema = JsonOutputFormat.Schema.builder()
        analysisSchema.forEach { (key, value) -> schema.putAdditionalProperty(key, JsonValue.from(value)) }
        OutputConfig.builder().format(JsonOutputFormat.builder().schema(schema.build()).build()).build()
    }

    override suspend fun analyzePhoto(jpeg: ByteArray, userContext: String): MealAnalysis = call {
        val image = ImageBlockParam.builder()
            .source(
                Base64ImageSource.builder()
                    .data(Base64.encodeToString(jpeg, Base64.NO_WRAP))
                    .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                    .build()
            )
            .build()
        val prompt = """
            This is a photo of a meal. Identify each distinct dish or drink and estimate, for the portion visible,
            its calories and macronutrients. Use typical Vietnamese recipes and portion sizes when the dish is Vietnamese.
            Estimate realistically: account for cooking oil, sauces and hidden sugar. If the photo shows no food, return an empty list.
            ${AiClient.tagGuide}
            The advice field should speak to this person directly: $userContext
        """.trimIndent()
        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(16000L)
            .outputConfig(analysisFormat)
            .addUserMessageOfBlockParams(
                listOf(
                    ContentBlockParam.ofImage(image),
                    ContentBlockParam.ofText(TextBlockParam.builder().text(prompt).build()),
                )
            )
            .build()
        json.decodeFromString<MealAnalysis>(text(client.messages().create(params)))
    }

    /** Nutrition estimate for a dish known only by name (when the online database has no match). */
    override suspend fun estimateFood(name: String): MealAnalysis = call {
        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(4000L)
            .outputConfig(analysisFormat)
            .addUserMessage(AiClient.estimatePrompt(name))
            .build()
        json.decodeFromString<MealAnalysis>(text(client.messages().create(params)))
    }

    override suspend fun chat(system: String, history: List<ChatTurn>): String = call {
        val builder = MessageCreateParams.builder().model(MODEL).maxTokens(16000L).system(system)
        history.forEach { if (it.fromUser) builder.addUserMessage(it.text) else builder.addAssistantMessage(it.text) }
        text(client.messages().create(builder.build()))
    }

    private fun text(message: Message): String {
        if (message.stopReason().orElse(null) == StopReason.REFUSAL) throw AiException(L("AI từ chối trả lời yêu cầu này.", "The AI declined this request."))
        val text = message.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
        if (text.isBlank()) throw AiException(L("AI không trả về nội dung. Hãy thử lại.", "The AI returned nothing. Please try again."))
        return text
    }

    private suspend fun <T> call(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: UnauthorizedException) {
            throw AiException(L("API key không hợp lệ. Kiểm tra lại trong Hồ sơ → Trợ lý AI.", "Invalid API key. Check it in Profile → AI assistant."))
        } catch (e: RateLimitException) {
            throw AiException(L("Đang quá tải lượt gọi AI. Thử lại sau ít phút.", "The AI is busy. Try again in a few minutes."))
        } catch (e: AnthropicServiceException) {
            throw AiException(L("Lỗi từ dịch vụ AI (${e.statusCode()}): ${e.message ?: ""}", "AI service error (${e.statusCode()}): ${e.message ?: ""}").take(200))
        } catch (e: AnthropicIoException) {
            throw AiException(L("Không kết nối được tới AI. Kiểm tra mạng.", "Couldn't reach the AI. Check your connection."))
        } catch (e: kotlinx.serialization.SerializationException) {
            throw AiException(L("Không đọc được kết quả AI. Hãy thử lại.", "Couldn't read the AI's answer. Please try again."))
        }
    }

    private companion object {
        const val MODEL = "claude-haiku-5-5"
    }
}
