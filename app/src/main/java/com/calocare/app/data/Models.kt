package com.calocare.app.data

import com.calocare.app.L
import kotlinx.serialization.Serializable

/** Where a dish sits in a meal; drives the suggested eating order. */
object Course {
    const val SOUP = "soup"
    const val VEG = "veg"
    const val PROTEIN = "protein"
    const val MAIN = "main" // carb-based or mixed one-dish meals
    const val FRUIT = "fruit"
    const val DESSERT = "dessert"
    const val DRINK = "drink"
    const val SNACK = "snack"

    val all = listOf(SOUP, VEG, PROTEIN, MAIN, FRUIT, DESSERT, DRINK, SNACK)

    val labels get() = mapOf(
        SOUP to L("Canh / khai vị", "Soups & starters"), VEG to L("Rau", "Vegetables"), PROTEIN to L("Đạm", "Protein"), MAIN to L("Món chính", "Mains"),
        FRUIT to L("Trái cây", "Fruit"), DESSERT to L("Tráng miệng", "Desserts"), DRINK to L("Đồ uống", "Drinks"), SNACK to L("Ăn vặt", "Snacks"),
    )
}

/** Tags describe what a food contains. Allergens and health-relevant properties share one namespace. */
object Tag {
    // allergens
    const val PEANUT = "peanut"
    const val NUTS = "nuts"
    const val SHELLFISH = "shellfish"
    const val FISH = "fish"
    const val MILK = "milk"
    const val EGG = "egg"
    const val GLUTEN = "gluten"
    const val SOY = "soy"
    const val SESAME = "sesame"

    // properties
    const val HIGH_SUGAR = "high_sugar"
    const val HIGH_GI = "high_gi"
    const val HIGH_CARB = "high_carb"
    const val HIGH_SODIUM = "high_sodium"
    const val HIGH_PURINE = "high_purine"
    const val HIGH_SATFAT = "high_satfat"
    const val FRIED = "fried"
    const val MEAT = "meat"
    const val PORK = "pork"
    const val ANIMAL = "animal" // animal-derived but not meat (egg, dairy, honey)
    const val ALCOHOL = "alcohol"

    val allergens get() = linkedMapOf(
        PEANUT to L("Đậu phộng", "Peanuts"), NUTS to L("Hạt cây (điều, hạnh nhân…)", "Tree nuts (cashew, almond…)"), SHELLFISH to L("Tôm cua, hải sản có vỏ", "Shellfish"),
        FISH to L("Cá", "Fish"), MILK to L("Sữa / lactose", "Milk / lactose"), EGG to L("Trứng", "Eggs"), GLUTEN to L("Gluten (lúa mì)", "Gluten (wheat)"),
        SOY to L("Đậu nành", "Soy"), SESAME to L("Mè (vừng)", "Sesame"),
    )

    val all = allergens.keys + listOf(
        HIGH_SUGAR, HIGH_GI, HIGH_CARB, HIGH_SODIUM, HIGH_PURINE, HIGH_SATFAT, FRIED, MEAT, PORK, ANIMAL, ALCOHOL,
    )
}

object Condition {
    const val DIABETES = "diabetes"
    const val HYPERTENSION = "hypertension"
    const val GOUT = "gout"
    const val CHOLESTEROL = "cholesterol"

    val labels get() = linkedMapOf(
        DIABETES to L("Tiểu đường", "Diabetes"), HYPERTENSION to L("Cao huyết áp", "High blood pressure"), GOUT to "Gout", CHOLESTEROL to L("Mỡ máu cao", "High cholesterol"),
    )
}

object Diet {
    const val NONE = "none"
    const val VEGETARIAN = "vegetarian"
    const val VEGAN = "vegan"
    const val KETO = "keto"
    const val HALAL = "halal"

    val labels get() = linkedMapOf(
        NONE to L("Không kiêng", "No restrictions"), VEGETARIAN to L("Ăn chay (có trứng sữa)", "Vegetarian (eggs & dairy)"), VEGAN to L("Thuần chay", "Vegan"),
        KETO to L("Keto / ít tinh bột", "Keto / low-carb"), HALAL to "Halal",
    )
}

object Goal {
    const val LOSE = "lose"
    const val MAINTAIN = "maintain"
    const val GAIN = "gain"
    const val GYM = "gym"
    const val RUN = "run"

    val labels get() = linkedMapOf(
        LOSE to L("Giảm cân", "Lose weight"), MAINTAIN to L("Giữ cân", "Maintain"), GAIN to L("Tăng cân", "Gain weight"), GYM to L("Gym · tăng cơ", "Gym · build muscle"), RUN to L("Chạy bộ", "Running"),
    )
    val emoji = mapOf(LOSE to "🔥", MAINTAIN to "⚖️", GAIN to "🍚", GYM to "🏋️", RUN to "🏃")
}

object Meal {
    const val BREAKFAST = "breakfast"
    const val LUNCH = "lunch"
    const val DINNER = "dinner"
    const val SNACK = "snack"

    val all = listOf(BREAKFAST, LUNCH, DINNER, SNACK)
    val labels get() = mapOf(BREAKFAST to L("Sáng", "Breakfast"), LUNCH to L("Trưa", "Lunch"), DINNER to L("Tối", "Dinner"), SNACK to L("Ăn vặt", "Snack"))
    val emoji = mapOf(BREAKFAST to "🌅", LUNCH to "☀️", DINNER to "🌙", SNACK to "🍪")

    fun forHour(hour: Int) = when (hour) {
        in 4..9 -> BREAKFAST
        in 10..13 -> LUNCH
        in 17..20 -> DINNER
        else -> SNACK
    }
}

object Region {
    const val VN = "vn"
    const val INTL = "intl"
    const val ALL = "all"
    val labels get() = linkedMapOf(VN to L("Việt Nam", "Vietnamese"), INTL to L("Quốc tế / Âu Mỹ", "International / Western"))
}

@Serializable
data class Food(
    val id: String,
    val name: String,
    val emoji: String = "🍽️",
    val imageUrl: String? = null,
    val serving: String = "1 phần",
    val kcal: Int,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val course: String = Course.MAIN,
    val tags: Set<String> = emptySet(),
    val region: String = Region.ALL,
    val custom: Boolean = false,
)

/** One diary line. Nutrition is copied from the food so later edits/deletes never rewrite history. */
@Serializable
data class LogEntry(
    val id: Long,
    val date: String, // ISO yyyy-MM-dd
    val meal: String,
    val foodId: String,
    val name: String,
    val emoji: String = "🍽️",
    val imageUrl: String? = null,
    val serving: String = "",
    val kcal: Int, // per 1 serving
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val course: String = Course.MAIN,
    val tags: Set<String> = emptySet(),
    val qty: Float = 1f,
) {
    val totalKcal get() = kcal * qty
}

@Serializable
data class ExerciseEntry(
    val id: Long,
    val date: String,
    val name: String,
    val emoji: String,
    val minutes: Int,
    val kcal: Int,
)

@Serializable
data class WeightEntry(val date: String, val kg: Float)

/** A day of data read from the watch / band through Health Connect. */
@Serializable
data class WearableDay(val activeKcal: Int = 0, val totalKcal: Int = 0, val steps: Int = 0, val sleepMin: Int = 0)

@Serializable
data class Profile(
    val onboarded: Boolean = false,
    val name: String = "",
    val male: Boolean = true,
    val birthYear: Int = 1995,
    val heightCm: Float = 165f,
    val weightKg: Float = 60f,
    val activity: Float = 1.375f,
    val goal: String = Goal.MAINTAIN,
    val targetWeightKg: Float = 60f,
    val weeklyRateKg: Float = 0.5f,
    val conditions: Set<String> = emptySet(),
    val allergies: Set<String> = emptySet(),
    val diet: String = Diet.NONE,
    // Which cuisine menus and the quick-add grid favour: international dishes for English-speaking users.
    val region: String = if (com.calocare.app.Lang.en) Region.INTL else Region.VN,
    val wearableLinked: Boolean = false,
    /** Correction learned from the real weight trend, added on top of the formula target. */
    val kcalAdjust: Int = 0,
    val kcalAdjustDate: String = "",
)

@Serializable
data class AppData(
    val profile: Profile = Profile(),
    val customFoods: List<Food> = emptyList(),
    val entries: List<LogEntry> = emptyList(),
    val exercises: List<ExerciseEntry> = emptyList(),
    val weights: List<WeightEntry> = emptyList(),
    val wearable: Map<String, WearableDay> = emptyMap(),
    /** Portion multipliers the user applied to AI-recognised dishes, keyed by dish name. */
    val portionBias: Map<String, Float> = emptyMap(),
    /** Dish names the AI got wrong and the food the user picked instead: AI name → food id. */
    val nameFixes: Map<String, String> = emptyMap(),
)

/** The phone's copy of the server's dish library; [version] is what the next sync asks changes since. */
@Serializable
data class LibraryCache(val version: Long = 0, val foods: List<Food> = emptyList())
