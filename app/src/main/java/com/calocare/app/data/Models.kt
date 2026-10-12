package com.calocare.app.data

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

    val labels = mapOf(
        SOUP to "Canh / khai vị", VEG to "Rau", PROTEIN to "Đạm", MAIN to "Món chính",
        FRUIT to "Trái cây", DESSERT to "Tráng miệng", DRINK to "Đồ uống", SNACK to "Ăn vặt",
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

    val allergens = linkedMapOf(
        PEANUT to "Đậu phộng", NUTS to "Hạt cây (điều, hạnh nhân…)", SHELLFISH to "Tôm cua, hải sản có vỏ",
        FISH to "Cá", MILK to "Sữa / lactose", EGG to "Trứng", GLUTEN to "Gluten (lúa mì)",
        SOY to "Đậu nành", SESAME to "Mè (vừng)",
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

    val labels = linkedMapOf(
        DIABETES to "Tiểu đường", HYPERTENSION to "Cao huyết áp", GOUT to "Gout", CHOLESTEROL to "Mỡ máu cao",
    )
}

object Diet {
    const val NONE = "none"
    const val VEGETARIAN = "vegetarian"
    const val VEGAN = "vegan"
    const val KETO = "keto"
    const val HALAL = "halal"

    val labels = linkedMapOf(
        NONE to "Không kiêng", VEGETARIAN to "Ăn chay (có trứng sữa)", VEGAN to "Thuần chay",
        KETO to "Keto / ít tinh bột", HALAL to "Halal",
    )
}

object Goal {
    const val LOSE = "lose"
    const val MAINTAIN = "maintain"
    const val GAIN = "gain"
    const val GYM = "gym"
    const val RUN = "run"

    val labels = linkedMapOf(
        LOSE to "Giảm cân", MAINTAIN to "Giữ cân", GAIN to "Tăng cân", GYM to "Gym · tăng cơ", RUN to "Chạy bộ",
    )
    val emoji = mapOf(LOSE to "🔥", MAINTAIN to "⚖️", GAIN to "🍚", GYM to "🏋️", RUN to "🏃")
}

object Meal {
    const val BREAKFAST = "breakfast"
    const val LUNCH = "lunch"
    const val DINNER = "dinner"
    const val SNACK = "snack"

    val all = listOf(BREAKFAST, LUNCH, DINNER, SNACK)
    val labels = mapOf(BREAKFAST to "Sáng", LUNCH to "Trưa", DINNER to "Tối", SNACK to "Ăn vặt")
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
    val labels = linkedMapOf(VN to "Việt Nam", INTL to "Quốc tế / Âu Mỹ")
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
    val region: String = Region.VN,
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
