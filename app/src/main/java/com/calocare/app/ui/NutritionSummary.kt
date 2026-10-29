package com.calocare.app.ui

import com.calocare.app.data.AppData
import com.calocare.app.data.entriesOn
import com.calocare.app.data.totals
import com.calocare.app.label
import com.calocare.app.net.DayNutrition
import com.calocare.app.net.NutritionSummary
import java.time.LocalDate
import kotlin.math.roundToInt

internal fun AppData.dayNutrition(date: LocalDate): DayNutrition {
    val entries = entriesOn(date)
    val t = entries.totals()
    return DayNutrition(
        date = date.toString(),
        foods = entries.map { it.label },
        kcal = t.kcal.roundToInt(),
        protein_g = t.protein,
        carbs_g = t.carbs,
        fat_g = t.fat,
    )
}

/** The last [days] days up to today, oldest first. */
internal fun AppData.recentDays(days: Int): List<DayNutrition> =
    (days - 1 downTo 0).map { dayNutrition(LocalDate.now().minusDays(it.toLong())) }

internal fun AppData.nutritionSummary(period: String, days: Int): NutritionSummary {
    val rows = recentDays(days)
    val totalKcal = rows.sumOf { it.kcal }
    val totalProtein = rows.sumOf { it.protein_g.toDouble() }.toFloat()
    val totalCarbs = rows.sumOf { it.carbs_g.toDouble() }.toFloat()
    val totalFat = rows.sumOf { it.fat_g.toDouble() }.toFloat()
    val logged = rows.count { it.kcal > 0 }.coerceAtLeast(1)
    val entries = (days - 1 downTo 0).flatMap { entriesOn(LocalDate.now().minusDays(it.toLong())) }
    val entriesKcal = entries.sumOf { it.totalKcal.toDouble() }.coerceAtLeast(1.0)
    val courses = entries.groupBy { it.course }.mapValues { (_, list) -> (list.sumOf { it.totalKcal.toDouble() } / entriesKcal).toFloat() }
    return NutritionSummary(
        period = period,
        startDate = rows.first().date,
        endDate = rows.last().date,
        daysCount = logged,
        totalDays = days,
        totalKcal = totalKcal,
        totalProtein = totalProtein,
        totalCarbs = totalCarbs,
        totalFat = totalFat,
        avgKcal = totalKcal.toFloat() / logged,
        avgProtein = totalProtein / logged,
        avgCarbs = totalCarbs / logged,
        avgFat = totalFat / logged,
        mealsPerDay = rows.sumOf { it.foods.size }.toFloat() / logged,
        courseBreakdown = courses,
    )
}
