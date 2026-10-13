package com.calocare.app.data

import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class Totals(val kcal: Float = 0f, val protein: Float = 0f, val carbs: Float = 0f, val fat: Float = 0f)

data class Macros(val protein: Int, val carbs: Int, val fat: Int)

data class OutBreakdown(
    val bmr: Int, val active: Int, val exercise: Int, val steps: Int, val fromWearable: Boolean,
) {
    val total get() = bmr + active + exercise
}

data class Warning(val danger: Boolean, val text: String)

data class OrderStep(val name: String, val emoji: String, val why: String)

fun AppData.entriesOn(date: LocalDate) = date.toString().let { d -> entries.filter { it.date == d } }

fun AppData.exercisesOn(date: LocalDate) = date.toString().let { d -> exercises.filter { it.date == d } }

fun List<LogEntry>.totals() = Totals(
    kcal = sumOf { it.kcal * it.qty.toDouble() }.toFloat(),
    protein = sumOf { it.protein * it.qty.toDouble() }.toFloat(),
    carbs = sumOf { it.carbs * it.qty.toDouble() }.toFloat(),
    fat = sumOf { it.fat * it.qty.toDouble() }.toFloat(),
)

object Nutrition {
    fun age(p: Profile) = (LocalDate.now().year - p.birthYear).coerceIn(10, 100)

    /** Mifflin-St Jeor. */
    fun bmr(p: Profile): Float =
        10f * p.weightKg + 6.25f * p.heightCm - 5f * age(p) + if (p.male) 5f else -161f

    fun tdee(p: Profile) = bmr(p) * p.activity

    fun bmi(p: Profile): Float = (p.heightCm / 100f).let { h -> if (h > 0f) p.weightKg / (h * h) else 0f }

    /** Asian-Pacific cut-offs (WHO WPRO), which fit Vietnamese adults better than the global ones. */
    fun bmiLabel(bmi: Float) = when {
        bmi < 18.5f -> "Thiếu cân"
        bmi < 23f -> "Bình thường"
        bmi < 25f -> "Thừa cân"
        else -> "Béo phì"
    }

    fun targetKcal(p: Profile): Int {
        val tdee = tdee(p)
        val perDay = p.weeklyRateKg * 7700f / 7f
        val raw = when (p.goal) {
            Goal.LOSE -> tdee - min(perDay, 1000f)
            Goal.GAIN -> tdee + min(perDay, 700f)
            Goal.GYM -> tdee + 250f
            else -> tdee
        }
        // Never recommend a crash diet: below these floors micronutrient needs are hard to meet.
        val floor = if (p.male) 1500f else 1200f
        return max(raw + p.kcalAdjust, floor).roundToInt()
    }

    data class Adaptive(val delta: Int, val actualPerWeek: Float, val plannedPerWeek: Float, val days: Int)

    /**
     * Adaptive target: compares the real weight trend with the plan. Formulas only estimate TDEE;
     * after two or more weeks of data the scale is the better measurement.
     */
    fun adaptive(data: AppData): Adaptive? {
        val p = data.profile
        val today = LocalDate.now()
        val from = maxOf(today.minusDays(28).toString(), p.kcalAdjustDate)
        val weights = data.weights.filter { it.date >= from }
        if (weights.size < 2) return null
        val first = weights.first()
        val last = weights.last()
        val days = (LocalDate.parse(last.date).toEpochDay() - LocalDate.parse(first.date).toEpochDay()).toInt()
        if (days < 14) return null
        // Without a mostly complete diary we cannot tell a wrong target from untracked eating.
        val logged = data.entries.filter { it.date >= first.date && it.date <= last.date }.map { it.date }.toSet().size
        if (logged < days * 0.6f) return null
        val actual = (last.kg - first.kg) / days * 7f
        val planned = when (p.goal) {
            Goal.LOSE -> -p.weeklyRateKg
            Goal.GAIN -> p.weeklyRateKg
            else -> 0f
        }
        val delta = (((planned - actual) * 7700f / 7f).coerceIn(-200f, 200f) / 50f).roundToInt() * 50
        if (delta == 0 || (p.kcalAdjust + delta) !in -500..500) return null
        return Adaptive(delta, actual, planned, days)
    }

    fun macroTargets(p: Profile, kcal: Int = targetKcal(p)): Macros {
        if (p.diet == Diet.KETO) return split(kcal, 0.25f, 0.08f, 0.67f)
        val m = when (p.goal) {
            Goal.LOSE -> split(kcal, 0.30f, 0.40f, 0.30f)
            Goal.GAIN -> split(kcal, 0.20f, 0.55f, 0.25f)
            Goal.RUN -> split(kcal, 0.18f, 0.60f, 0.22f)
            Goal.GYM -> {
                val protein = (2f * p.weightKg).roundToInt()
                val fat = (kcal * 0.25f / 9f).roundToInt()
                Macros(protein, max(0, (kcal - protein * 4 - fat * 9) / 4), fat)
            }
            else -> split(kcal, 0.20f, 0.50f, 0.30f)
        }
        // Diabetes: keep carbohydrate at or below ~45% of energy and move the rest to protein.
        if (Condition.DIABETES in p.conditions && m.carbs * 4 > kcal * 0.45f) {
            val carbs = (kcal * 0.45f / 4f).roundToInt()
            return Macros(m.protein + (m.carbs - carbs), carbs, m.fat)
        }
        return m
    }

    private fun split(kcal: Int, p: Float, c: Float, f: Float) =
        Macros((kcal * p / 4f).roundToInt(), (kcal * c / 4f).roundToInt(), (kcal * f / 9f).roundToInt())

    /**
     * Energy out for a day. With watch data: BMR + measured active energy. Without: BMR + a light
     * daily-living allowance (20% of BMR). Manually logged workouts are always added on top.
     */
    fun kcalOut(data: AppData, date: LocalDate): OutBreakdown {
        val p = data.profile
        val bmr = bmr(p)
        val w = data.wearable[date.toString()]
        val exercise = data.exercisesOn(date).sumOf { it.kcal }
        val complete = date.isBefore(LocalDate.now())
        val active: Float
        val wearable: Boolean
        when {
            w != null && w.activeKcal > 0 -> { active = w.activeKcal.toFloat(); wearable = true }
            w != null && complete && w.totalKcal > bmr -> { active = w.totalKcal - bmr; wearable = true }
            w != null && w.steps > 0 -> { active = w.steps * 0.04f * p.weightKg / 70f; wearable = true }
            else -> { active = bmr * 0.2f; wearable = false }
        }
        return OutBreakdown(bmr.roundToInt(), active.roundToInt(), exercise, w?.steps ?: 0, wearable)
    }

    fun exerciseKcal(met: Float, weightKg: Float, minutes: Int) = (met * weightKg * minutes / 60f).roundToInt()

    // ---------------------------------------------------------------- health warnings

    fun warnings(tags: Set<String>, p: Profile): List<Warning> {
        val out = mutableListOf<Warning>()
        for ((tag, label) in Tag.allergens) {
            if (tag in tags && tag in p.allergies) out += Warning(true, "Chứa ${label.lowercase()} — bạn đã khai dị ứng")
        }
        if (Condition.DIABETES in p.conditions) {
            if (Tag.HIGH_SUGAR in tags) out += Warning(false, "Nhiều đường — dễ làm tăng đường huyết")
            else if (Tag.HIGH_GI in tags) out += Warning(false, "Chỉ số đường huyết cao — ăn ít và ăn sau rau, đạm")
        }
        if (Condition.HYPERTENSION in p.conditions && Tag.HIGH_SODIUM in tags)
            out += Warning(false, "Nhiều muối — không tốt cho huyết áp")
        if (Condition.GOUT in p.conditions) {
            if (Tag.HIGH_PURINE in tags) out += Warning(false, "Nhiều purin — có thể gây cơn gout")
            if (Tag.ALCOHOL in tags) out += Warning(false, "Rượu bia làm tăng acid uric")
        }
        if (Condition.CHOLESTEROL in p.conditions && (Tag.HIGH_SATFAT in tags || Tag.FRIED in tags))
            out += Warning(false, "Nhiều chất béo bão hòa / chiên rán — hạn chế khi mỡ máu cao")
        when (p.diet) {
            Diet.VEGETARIAN ->
                if (tags.any { it == Tag.MEAT || it == Tag.FISH || it == Tag.SHELLFISH || it == Tag.PORK })
                    out += Warning(false, "Có thịt / cá — không hợp chế độ ăn chay")
            Diet.VEGAN ->
                if (tags.any { it in setOf(Tag.MEAT, Tag.FISH, Tag.SHELLFISH, Tag.PORK, Tag.ANIMAL, Tag.MILK, Tag.EGG) })
                    out += Warning(false, "Có nguồn gốc động vật — không hợp thuần chay")
            Diet.KETO ->
                if (tags.any { it == Tag.HIGH_CARB || it == Tag.HIGH_SUGAR || it == Tag.HIGH_GI })
                    out += Warning(false, "Nhiều tinh bột / đường — vượt ngưỡng keto")
            Diet.HALAL ->
                if (Tag.PORK in tags || Tag.ALCOHOL in tags) out += Warning(false, "Có thịt heo / rượu — không Halal")
        }
        return out
    }

    // ---------------------------------------------------------------- eating order

    private fun rank(course: String, tags: Set<String>) = when (course) {
        Course.SOUP -> 0
        Course.VEG -> 1
        Course.PROTEIN -> 2
        Course.MAIN -> 3
        Course.FRUIT -> 4
        Course.DRINK -> if (Tag.HIGH_SUGAR in tags) 6 else 4
        else -> 5
    }

    private fun why(course: String, tags: Set<String>, diabetic: Boolean) = when (course) {
        Course.SOUP -> "Khai vị: làm ấm dạ dày, tạo cảm giác no sớm"
        Course.VEG -> if (diabetic) "Chất xơ đi trước giúp đường huyết sau ăn tăng chậm hơn" else "Chất xơ trước giúp no lâu, hấp thu đường chậm lại"
        Course.PROTEIN -> "Đạm giúp no bền và giữ cơ"
        Course.MAIN -> if (diabetic) "Tinh bột ăn sau cùng trong bữa chính để hạn chế đường huyết tăng vọt" else "Tinh bột ăn sau rau và đạm để tránh ăn quá tay"
        Course.FRUIT -> "Trái cây sau bữa khoảng 30 phút, hoặc để dành bữa phụ"
        Course.DRINK -> if (Tag.HIGH_SUGAR in tags) "Đồ uống ngọt để sau cùng, uống ít" else "Uống kèm hoặc sau bữa"
        else -> "Tráng miệng: ăn sau cùng, lượng nhỏ"
    }

    /** Vegetables and soup first, then protein, then starch, sweets last. */
    fun eatingOrder(entries: List<LogEntry>, p: Profile): List<OrderStep> {
        val diabetic = Condition.DIABETES in p.conditions
        return entries.distinctBy { it.name }
            .sortedBy { rank(it.course, it.tags) }
            .map { OrderStep(it.name, it.emoji, why(it.course, it.tags, diabetic)) }
    }

    // ---------------------------------------------------------------- motivation and coaching

    /** Days in a row (ending today or yesterday) with at least one diary entry. */
    fun streak(data: AppData): Int {
        val days = data.entries.map { it.date }.toSet()
        var d = LocalDate.now()
        if (d.toString() !in days) d = d.minusDays(1)
        var n = 0
        while (d.toString() in days) { n++; d = d.minusDays(1) }
        return n
    }

    fun onTarget(data: AppData, date: LocalDate): Boolean {
        val eaten = data.entriesOn(date).totals().kcal
        if (eaten <= 0f) return false
        val target = targetKcal(data.profile).toFloat()
        return when (data.profile.goal) {
            Goal.LOSE -> eaten <= target * 1.05f && eaten >= target * 0.7f
            Goal.GAIN, Goal.GYM -> eaten >= target * 0.95f
            else -> eaten in target * 0.9f..target * 1.1f
        }
    }

    fun cheer(data: AppData, date: LocalDate): Pair<String, String> {
        val p = data.profile
        val eaten = data.entriesOn(date).totals().kcal
        val target = targetKcal(p).toFloat()
        val ratio = eaten / target
        val streak = streak(data)
        val who = p.name.ifBlank { "bạn" }
        return when {
            eaten <= 0f -> "👋" to "Chào $who! Chạm vào món đầu tiên để bắt đầu ngày mới nhé."
            p.goal == Goal.LOSE && ratio > 1.1f -> "💪" to "Hôm nay vượt mục tiêu ${(eaten - target).roundToInt()} kcal. Không sao — đi bộ 30 phút và quay lại nhịp vào bữa tới."
            (p.goal == Goal.GAIN || p.goal == Goal.GYM) && ratio < 0.6f && LocalTime.now().hour >= 18 && date == LocalDate.now() ->
                "🍚" to "Còn thiếu ${(target - eaten).roundToInt()} kcal để tăng cân. Thêm một bữa phụ giàu đạm trước khi ngủ nhé."
            onTarget(data, date) && streak >= 7 -> "🏆" to "Tuyệt vời! $streak ngày liên tiếp và hôm nay đúng mục tiêu. Giữ phong độ này nhé $who!"
            onTarget(data, date) && ratio > 1.02f -> "⚖️" to "Vượt mục tiêu ${(eaten - target).roundToInt()} kcal — vẫn trong mức cho phép. Bữa sau ăn nhẹ hơn một chút là cân bằng."
            onTarget(data, date) -> "🎯" to "Đúng mục tiêu rồi! Bạn đang làm rất tốt."
            ratio < 0.5f -> "🌱" to "Khởi đầu tốt — còn ${(target - eaten).roundToInt()} kcal cho hôm nay."
            ratio <= 1f -> "🔥" to "Đã đi được ${(ratio * 100).roundToInt()}% chặng đường hôm nay. Cố lên $who!"
            else -> "⚖️" to "Hơi vượt mục tiêu một chút. Bữa sau ưu tiên rau và đạm nạc nhé."
        }
    }

    /** Rule-based advice that works offline; the AI coach builds on the same numbers. */
    fun tips(data: AppData, date: LocalDate): List<String> {
        val p = data.profile
        val entries = data.entriesOn(date)
        val t = entries.totals()
        val target = targetKcal(p)
        val macros = macroTargets(p, target)
        val out = kcalOut(data, date)
        val today = date == LocalDate.now()
        val hour = LocalTime.now().hour
        val tips = mutableListOf<String>()

        if (today && hour >= 18 && t.kcal > 0f && t.protein < macros.protein * 0.6f)
            tips += "Đạm hôm nay mới ${t.protein.roundToInt()}g / ${macros.protein}g. Bữa tối thêm cá, ức gà, trứng hoặc đậu hũ."
        if (today && hour >= 20 && t.kcal > 0 && t.kcal < bmr(p) * 0.7f)
            tips += "Bạn đang ăn thấp hơn nhiều so với mức trao đổi chất cơ bản (${out.bmr} kcal). Ăn quá ít dễ mất cơ và mệt — đừng bỏ bữa."
        if (t.kcal > target * 1.15f)
            tips += "Đã vượt mục tiêu ${(t.kcal - target).roundToInt()} kcal. Khoảng ${((t.kcal - target) / (3.5f * p.weightKg / 60f)).roundToInt()} phút đi bộ nhanh sẽ bù lại."
        if (macros.carbs > 0 && t.carbs > macros.carbs * 1.2f)
            tips += "Tinh bột hơi cao (${t.carbs.roundToInt()}g). Thử đổi nửa chén cơm bằng rau hoặc khoai lang."
        if (out.fromWearable && out.steps in 1..4999 && today && hour >= 15)
            tips += "Mới ${out.steps} bước. Đi bộ 15 phút sau bữa tối giúp tiêu hóa và ổn định đường huyết."

        val sleep = data.wearable[date.toString()]?.sleepMin ?: 0
        if (sleep in 1..359)
            tips += "Đêm qua bạn ngủ ${sleep / 60}h${"%02d".format(sleep % 60)}. Thiếu ngủ làm tăng cảm giác thèm ngọt và giảm phục hồi — hôm nay tập nhẹ, ưu tiên đạm và ngủ sớm hơn."

        val sugary = entries.count { Tag.HIGH_SUGAR in it.tags }
        if (Condition.DIABETES in p.conditions) {
            if (sugary > 0) tips += "Hôm nay có $sugary món nhiều đường. Với tiểu đường, nên thay bằng trái cây ít ngọt (ổi, táo) và đo đường huyết sau ăn 2 giờ."
            tips += "Tiểu đường: ăn rau → đạm → tinh bột, chia 3 bữa chính + 1–2 bữa phụ nhỏ, không bỏ bữa."
        }
        if (Condition.HYPERTENSION in p.conditions)
            tips += "Huyết áp: giữ muối dưới 5g/ngày — hạn chế nước dùng phở/bún, đồ kho mặn, mì gói."
        if (Condition.GOUT in p.conditions)
            tips += "Gout: uống 2–2,5 lít nước/ngày, hạn chế nội tạng, hải sản, bia rượu."
        if (Condition.CHOLESTEROL in p.conditions)
            tips += "Mỡ máu: ưu tiên cá, yến mạch, các loại hạt; hạn chế đồ chiên và mỡ động vật."

        val bmi = bmi(p)
        if (bmi < 18.5f && p.goal == Goal.LOSE)
            tips += "BMI của bạn là ${"%.1f".format(bmi)} (thiếu cân) — không nên giảm cân thêm. Hãy đổi mục tiêu sang giữ hoặc tăng cân."

        tips += when (p.goal) {
            Goal.LOSE -> "Giảm cân bền vững: thâm hụt 300–500 kcal/ngày, đạm 1,6g/kg để giữ cơ, ngủ đủ 7 giờ."
            Goal.GAIN -> "Tăng cân: ăn dư 300–500 kcal/ngày, thêm bữa phụ (sữa, chuối, hạt) và tập kháng lực để tăng cơ thay vì mỡ."
            Goal.GYM -> "Tăng cơ: đạm khoảng 2g/kg chia đều 4 bữa, nạp 20–30g đạm trong 2 giờ sau tập."
            Goal.RUN -> "Chạy bộ: nạp tinh bột dễ tiêu 1–2 giờ trước khi chạy (chuối, bánh mì), bù nước và điện giải nếu chạy trên 60 phút."
            else -> "Giữ cân: giữ nhịp ăn đều, mỗi bữa đủ rau – đạm – tinh bột theo tỉ lệ ½ – ¼ – ¼ đĩa."
        }
        return tips
    }

    val goalGuides = mapOf(
        Goal.LOSE to listOf(
            "Mỗi bữa: ½ đĩa rau, ¼ đạm nạc, ¼ tinh bột nguyên cám.",
            "Uống nước trước bữa ăn 15 phút; hạn chế nước ngọt, trà sữa.",
            "Giảm 0,5 kg/tuần là an toàn; giảm nhanh hơn dễ mất cơ và tăng lại.",
        ),
        Goal.MAINTAIN to listOf(
            "Ăn đa dạng, đủ 3 bữa chính; cân lại mỗi tuần vào cùng một buổi sáng.",
            "Vận động vừa ít nhất 150 phút mỗi tuần.",
        ),
        Goal.GAIN to listOf(
            "Ăn 5–6 bữa nhỏ, thêm đồ uống giàu năng lượng (sinh tố bơ, sữa).",
            "Tăng 0,25–0,5 kg/tuần; kết hợp tập tạ 3 buổi/tuần.",
        ),
        Goal.GYM to listOf(
            "Trước tập 60–90 phút: tinh bột + ít đạm (chuối + sữa chua).",
            "Sau tập trong 2 giờ: 20–30g đạm + tinh bột (cơm + ức gà, hoặc whey + chuối).",
            "Ngủ 7–8 giờ — cơ phát triển lúc nghỉ, không phải lúc tập.",
        ),
        Goal.RUN to listOf(
            "Ngày chạy dài: tăng tinh bột lên 6–8g/kg; ngày nghỉ giảm lại.",
            "Chạy trên 60 phút: bổ sung 30–60g tinh bột mỗi giờ và nước có điện giải.",
            "Sau chạy 30 phút: tinh bột + đạm tỉ lệ 3:1 để hồi phục glycogen.",
        ),
    )
}
