package com.calocare.app.data

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

data class PlannedItem(val food: Food, val qty: Float)

data class PlannedMeal(val meal: String, val items: List<PlannedItem>) {
    val kcal get() = items.sumOf { (it.food.kcal * it.qty).toDouble() }.roundToInt()
}

/**
 * Builds a one-day menu from meal templates of local dishes. Templates that conflict with the
 * user's allergies, conditions or diet are dropped, and portions are scaled to the calorie target.
 */
object MenuPlanner {
    private class Combo(val meal: String, val region: String, val goals: Set<String>, val items: List<Pair<String, Float>>)

    private val all = emptySet<String>()
    private const val L = Goal.LOSE
    private const val M = Goal.MAINTAIN
    private const val G = Goal.GAIN
    private const val Y = Goal.GYM
    private const val R = Goal.RUN

    private fun c(meal: String, region: String, goals: Set<String>, vararg items: Pair<String, Float>) =
        Combo(meal, region, goals, items.toList())

    private infix fun String.x(q: Double) = this to q.toFloat()
    private val String.one get() = this to 1f

    private val combos = listOf(
        // ---------------- breakfast
        c(Meal.BREAKFAST, Region.VN, setOf(M, G, Y, R), "pho_bo".one),
        c(Meal.BREAKFAST, Region.VN, all, "pho_ga".one),
        c(Meal.BREAKFAST, Region.VN, setOf(M, G, Y, R), "banh_mi_trung".one, "sua_dau_nanh".one),
        c(Meal.BREAKFAST, Region.ALL, setOf(L, R, M, Y), "yen_mach".one, "chuoi".one),
        c(Meal.BREAKFAST, Region.ALL, setOf(L, Y, M), "trung_luoc" x 2.0, "khoai_lang".one, "sua_tuoi".one),
        c(Meal.BREAKFAST, Region.VN, setOf(L, M), "chao_ga".one, "trung_luoc".one),
        c(Meal.BREAKFAST, Region.VN, setOf(G, R), "xoi_man".one, "sua_dau_nanh".one),
        c(Meal.BREAKFAST, Region.VN, setOf(M, G), "banh_cuon".one),
        c(Meal.BREAKFAST, Region.ALL, all, "khoai_lang".one, "sua_dau_nanh".one, "chuoi".one),
        c(Meal.BREAKFAST, Region.ALL, all, "bo".one, "trung_luoc" x 2.0, "ca_phe_den".one),
        c(Meal.BREAKFAST, Region.ALL, all, "bo".one, "dau_hu".one, "tra_xanh".one),
        c(Meal.BREAKFAST, Region.INTL, all, "banh_mi_nuong".one, "trung_chien" x 2.0, "sua_tuoi".one),
        c(Meal.BREAKFAST, Region.INTL, setOf(L, M, Y), "sua_chua_hy_lap".one, "dau_tay".one, "hanh_nhan".one),
        c(Meal.BREAKFAST, Region.INTL, setOf(G, R, M), "banh_mi_nuong".one, "bo_dau_phong".one, "chuoi".one),

        // ---------------- lunch
        c(Meal.LUNCH, Region.ALL, setOf(L, Y, M), "canh_rau".one, "rau_luoc".one, "uc_ga".one, "com_gao_lut".one),
        c(Meal.LUNCH, Region.VN, setOf(M, G, R), "canh_chua".one, "rau_muong_xao".one, "ca_kho".one, "com_trang".one),
        c(Meal.LUNCH, Region.VN, setOf(G, M), "canh_bi".one, "rau_luoc".one, "thit_kho".one, "com_trang".one),
        c(Meal.LUNCH, Region.ALL, setOf(Y, L, M), "salad".one, "bo_xao".one, "com_gao_lut".one),
        c(Meal.LUNCH, Region.VN, setOf(G, Y, R), "canh_rau".one, "com_ga".one),
        c(Meal.LUNCH, Region.VN, setOf(R, G, M), "bun_cha".one),
        c(Meal.LUNCH, Region.ALL, all, "canh_rau".one, "rau_luoc".one, "dau_hu".one, "com_gao_lut".one),
        c(Meal.LUNCH, Region.ALL, all, "salad".one, "ca_hoi".one, "bo".one),
        c(Meal.LUNCH, Region.ALL, all, "salad".one, "dau_hu_chien" x 2.0, "bo".one),
        c(Meal.LUNCH, Region.VN, setOf(G), "canh_rau".one, "com_tam".one),
        c(Meal.LUNCH, Region.VN, setOf(R, M), "goi_cuon".one, "pho_ga".one),
        c(Meal.LUNCH, Region.INTL, setOf(M, Y, R), "salad".one, "sandwich".one),
        c(Meal.LUNCH, Region.INTL, setOf(G, R, M), "salad".one, "mi_y".one),
        c(Meal.LUNCH, Region.INTL, setOf(M, R, L), "salad".one, "sushi".one),

        // ---------------- dinner
        c(Meal.DINNER, Region.ALL, setOf(L, M), "canh_rau".one, "rau_luoc".one, "ca_hap".one, "com_gao_lut" x 0.5),
        c(Meal.DINNER, Region.ALL, setOf(L, Y), "salad".one, "uc_ga".one, "khoai_lang".one),
        c(Meal.DINNER, Region.VN, setOf(M, Y, R), "canh_chua".one, "rau_luoc".one, "tom_luoc".one, "com_trang".one),
        c(Meal.DINNER, Region.VN, setOf(G, M, Y), "canh_bi".one, "rau_muong_xao".one, "thit_luoc".one, "com_trang".one),
        c(Meal.DINNER, Region.ALL, all, "canh_rau".one, "rau_muong_xao".one, "dau_hu".one, "com_trang".one),
        c(Meal.DINNER, Region.ALL, all, "rau_luoc".one, "trung_chien" x 2.0, "dau_hu_chien".one),
        c(Meal.DINNER, Region.VN, setOf(G, Y, R), "rau_luoc".one, "bo_xao".one, "com_trang" x 1.5),
        c(Meal.DINNER, Region.ALL, all, "rau_luoc".one, "ga_luoc".one, "bo".one),
        c(Meal.DINNER, Region.INTL, setOf(G, Y, M), "salad".one, "steak".one, "khoai_lang".one),
        c(Meal.DINNER, Region.INTL, all, "salad".one, "ca_hoi".one, "com_gao_lut".one),

        // ---------------- snack
        c(Meal.SNACK, Region.ALL, setOf(L, M), "sua_chua".one, "dau_tay".one),
        c(Meal.SNACK, Region.ALL, setOf(Y), "chuoi".one, "whey".one),
        c(Meal.SNACK, Region.ALL, setOf(R, G), "chuoi".one, "dau_phong".one),
        c(Meal.SNACK, Region.VN, setOf(G), "sinh_to_bo".one),
        c(Meal.SNACK, Region.ALL, setOf(L, M, Y), "tao".one, "hanh_nhan".one),
        c(Meal.SNACK, Region.ALL, setOf(G), "sua_tuoi".one, "banh_quy".one),
        c(Meal.SNACK, Region.ALL, all, "cam".one),
        c(Meal.SNACK, Region.ALL, all, "hat_dieu".one),
        c(Meal.SNACK, Region.VN, all, "oi".one),
        c(Meal.SNACK, Region.ALL, all, "dua_leo" x 2.0, "trung_luoc".one),
    )

    private fun shares(goal: String) = when (goal) {
        Goal.GAIN, Goal.GYM -> mapOf(Meal.BREAKFAST to 0.25f, Meal.LUNCH to 0.30f, Meal.DINNER to 0.30f, Meal.SNACK to 0.15f)
        else -> mapOf(Meal.BREAKFAST to 0.25f, Meal.LUNCH to 0.35f, Meal.DINNER to 0.30f, Meal.SNACK to 0.10f)
    }

    private fun pick(profile: Profile, goal: String, meal: String, wantKcal: Float, rnd: Random): PlannedMeal {
        val pool = combos.filter { it.meal == meal && (it.region == profile.region || it.region == Region.ALL) }
        fun Combo.foods() = items.mapNotNull { (id, q) -> FoodDb.byId[id]?.let { PlannedItem(it, q) } }
        fun Combo.clean() = foods().none { Nutrition.warnings(it.food.tags, profile).isNotEmpty() }
        fun Combo.safe() = foods().none { Nutrition.warnings(it.food.tags, profile).any { w -> w.danger } }
        fun Combo.fits() = goals.isEmpty() || goal in goals

        // Prefer templates with no warning at all; relax the goal first, never the allergy check.
        val candidates = pool.filter { it.clean() && it.fits() }
            .ifEmpty { pool.filter { it.clean() } }
            .ifEmpty { pool.filter { it.safe() && it.fits() } }
            .ifEmpty { pool.filter { it.safe() } }
        if (candidates.isEmpty()) return PlannedMeal(meal, emptyList())

        val items = candidates[rnd.nextInt(candidates.size)].foods()
        val base = items.sumOf { (it.food.kcal * it.qty).toDouble() }.toFloat()
        val scale = (wantKcal / base).coerceIn(0.5f, 2.5f)
        return PlannedMeal(meal, items.map { it.copy(qty = max(0.5f, (it.qty * scale * 2f).roundToInt() / 2f)) })
    }

    fun plan(profile: Profile, goal: String, targetKcal: Int, seed: Int): List<PlannedMeal> {
        val rnd = Random(seed)
        return Meal.all.map { meal -> pick(profile, goal, meal, targetKcal * (shares(goal)[meal] ?: 0.25f), rnd) }
    }

    data class NextMeal(val meal: PlannedMeal, val budget: Int, val note: String)

    /**
     * "What should I eat next?" — the next meal not yet logged today, sized from the calories still
     * left and split fairly with the meals that come after it.
     */
    fun nextMeal(data: AppData, hour: Int, seed: Int): NextMeal {
        val p = data.profile
        val entries = data.entriesOn(LocalDate.now())
        val eaten = entries.totals()
        val target = Nutrition.targetKcal(p)
        val remaining = target - eaten.kcal
        val share = shares(p.goal)
        val start = when { hour < 10 -> 0; hour < 14 -> 1; hour < 21 -> 2; else -> 3 }
        val open = listOf(Meal.BREAKFAST, Meal.LUNCH, Meal.DINNER).drop(start).filter { m -> entries.none { it.meal == m } }
        val rnd = Random(seed)
        if (open.isEmpty() || remaining < 200f) {
            val budget = remaining.coerceIn(80f, 250f)
            val note = if (remaining < 200f) "Bạn gần chạm mục tiêu hôm nay — nếu còn đói, chọn món nhẹ."
            else "Các bữa chính đã xong. Còn ${remaining.roundToInt()} kcal cho một bữa phụ."
            return NextMeal(pick(p, p.goal, Meal.SNACK, budget, rnd), budget.roundToInt(), note)
        }
        val next = open.first()
        val budget = remaining * (share[next] ?: 0.3f) / open.sumOf { (share[it] ?: 0.3f).toDouble() }.toFloat()
        val macros = Nutrition.macroTargets(p, target)
        val note = if (eaten.kcal > 0f && eaten.protein / macros.protein < eaten.kcal / target - 0.1f)
            "Đạm đang thấp hơn tiến độ — bữa này đừng bỏ phần đạm."
        else "Còn ${remaining.roundToInt()} kcal cho ${open.size} bữa chính còn lại."
        return NextMeal(pick(p, p.goal, next, budget, rnd), budget.roundToInt(), note)
    }
}
