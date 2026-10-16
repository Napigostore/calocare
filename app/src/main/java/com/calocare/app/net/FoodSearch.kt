package com.calocare.app.net

import com.calocare.app.L
import com.calocare.app.data.Course
import com.calocare.app.data.Food
import com.calocare.app.data.Region
import com.calocare.app.data.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Looks foods up online in Open Food Facts (ODbL data, CC BY-SA photos — credited in the app). */
object FoodSearch {
    private const val USER_AGENT = "CaloCare/1.0 (Android; personal calorie tracker)"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private val allergenMap = mapOf(
        "en:peanuts" to Tag.PEANUT, "en:nuts" to Tag.NUTS, "en:milk" to Tag.MILK, "en:eggs" to Tag.EGG,
        "en:gluten" to Tag.GLUTEN, "en:soybeans" to Tag.SOY, "en:fish" to Tag.FISH,
        "en:crustaceans" to Tag.SHELLFISH, "en:molluscs" to Tag.SHELLFISH, "en:sesame-seeds" to Tag.SESAME,
    )

    private fun get(url: okhttp3.HttpUrl): JsonObject {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return json.parseToJsonElement(response.body!!.string()).jsonObject
        }
    }

    private fun JsonObject.str(key: String) = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.num(key: String) = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.let { it.floatOrNull ?: it.contentOrNull?.toFloatOrNull() }

    /** Packaged foods and common dishes with calories, macros, allergens and a product photo. */
    suspend fun search(query: String): List<Food> = withContext(Dispatchers.IO) {
        // The old /cgi/search.pl endpoint now answers 503; this is Open Food Facts' current search service.
        val url = "https://search.openfoodfacts.org/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page_size", "24")
            .addQueryParameter("langs", "vi,en")
            .addQueryParameter(
                "fields",
                "code,product_name,product_name_vi,brands,image_front_small_url,nutriments,serving_size,allergens_tags",
            )
            .build()
        val products = get(url)["hits"]?.jsonArray ?: return@withContext emptyList()
        products.mapNotNull { element ->
            val p = element.jsonObject
            val name = p.str("product_name_vi") ?: p.str("product_name") ?: return@mapNotNull null
            val n = p["nutriments"] as? JsonObject ?: return@mapNotNull null
            // Prefer the per-serving figures; fall back to per 100 g/ml.
            val perServing = n.num("energy-kcal_serving")?.takeIf { it > 0f }
            val suffix = if (perServing != null) "_serving" else "_100g"
            val kcal = perServing ?: n.num("energy-kcal_100g") ?: return@mapNotNull null
            if (kcal <= 0f) return@mapNotNull null

            val tags = mutableSetOf<String>()
            (p["allergens_tags"] as? kotlinx.serialization.json.JsonArray)?.forEach { t ->
                allergenMap[t.jsonPrimitive.contentOrNull]?.let { tags += it }
            }
            if ((n.num("sugars_100g") ?: 0f) >= 10f) tags += Tag.HIGH_SUGAR
            if ((n.num("salt_100g") ?: 0f) >= 1.5f) tags += Tag.HIGH_SODIUM
            if ((n.num("saturated-fat_100g") ?: 0f) >= 5f) tags += Tag.HIGH_SATFAT
            if ((n.num("carbohydrates_100g") ?: 0f) >= 45f) tags += Tag.HIGH_CARB

            // A list in the current API, a comma-separated string in older records.
            val brand = (p["brands"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull()?.jsonPrimitive?.contentOrNull
                ?: p.str("brands")?.substringBefore(',')
            Food(
                id = "off_" + (p.str("code") ?: name.hashCode().toString()),
                name = if (brand != null && !name.contains(brand, ignoreCase = true)) "$name · $brand" else name,
                emoji = "🛒",
                imageUrl = p.str("image_front_small_url"),
                serving = if (perServing != null) (p.str("serving_size") ?: L("1 phần", "1 serving")) else "100g",
                kcal = kcal.roundToInt(),
                protein = n.num("proteins$suffix") ?: 0f,
                carbs = n.num("carbohydrates$suffix") ?: 0f,
                fat = n.num("fat$suffix") ?: 0f,
                course = Course.SNACK,
                tags = tags,
                region = Region.ALL,
                custom = true,
            )
        }.distinctBy { it.name.lowercase() }
    }
}
