package com.calocare.app.ui

import com.calocare.app.L
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calocare.app.AppViewModel
import com.calocare.app.data.AppData
import com.calocare.app.data.Nutrition
import com.calocare.app.data.entriesOn
import com.calocare.app.data.totals
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val weekdays = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")

/** Grouped bars per day, with an optional dashed target line. Zero values draw nothing. */
@Composable
fun BarChart(days: List<LocalDate>, series: List<List<Float>>, colors: List<Color>, target: Float? = null) {
    val top = max(series.maxOf { it.maxOrNull() ?: 0f }, target ?: 0f).coerceAtLeast(1f) * 1.1f
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val slot = size.width / days.size
            val bar = slot * 0.7f / series.size
            fun y(v: Float) = size.height - v / top * size.height
            days.indices.forEach { i ->
                series.forEachIndexed { s, values ->
                    val v = values[i]
                    if (v > 0f) drawRoundRect(colors[s], Offset(slot * i + slot * 0.15f + bar * s, y(v)), Size(bar * 0.9f, size.height - y(v)), CornerRadius(bar / 3f))
                }
            }
            if (target != null) drawLine(lineColor, Offset(0f, y(target)), Offset(size.width, y(target)), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        Row {
            days.forEachIndexed { i, d ->
                // A week shows weekday names; a month shows the date every fifth day.
                val label = if (days.size <= 7) weekdays[d.dayOfWeek.value - 1] else if (i % 5 == 0 || i == days.lastIndex) "${d.dayOfMonth}" else ""
                Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun LineChart(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val lo = values.min() - 0.3f
        val hi = values.max() + 0.3f
        val step = size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v -> Offset(i * step, size.height - (v - lo) / (hi - lo) * size.height) }
        for (i in 1 until pts.size) drawLine(color, pts[i - 1], pts[i], strokeWidth = 5f, cap = StrokeCap.Round)
        pts.forEach { drawCircle(color, 6f, it) }
    }
}

/** The Progress section: weight, calories, macros, steps, workouts and sleep over 7 or 30 days. */
fun LazyListScope.progressCharts(vm: AppViewModel, data: AppData, range: Int, onRange: (Int) -> Unit) {
    val today = LocalDate.now()
    val days = (range - 1 downTo 0).map { today.minusDays(it.toLong()) }
    val totals = days.map { data.entriesOn(it).totals() }
    val logged = days.indices.filter { totals[it].kcal > 0f }
    val p = data.profile
    val target = Nutrition.targetKcal(p)

    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7 to L("7 ngày", "7 days"), 30 to L("30 ngày", "30 days")).forEach { (n, label) -> FilterChip(range == n, { onRange(n) }, label = { Text(label) }) }
        }
    }

    Nutrition.adaptive(data)?.let { a ->
        item {
            SectionCard(title = L("🧠 Mục tiêu calo thích nghi", "🧠 Adaptive calorie target")) {
                Text(
                    L("Trong ${a.days} ngày qua cân nặng của bạn thay đổi ${signed(a.actualPerWeek)} kg/tuần, trong khi kế hoạch là ${signed(a.plannedPerWeek)} kg/tuần. " +
                        "Cơ thể bạn tiêu hao ${if (a.delta > 0) "nhiều" else "ít"} hơn công thức ước tính.",
                        "Over the last ${a.days} days your weight changed ${signed(a.actualPerWeek)} kg/week against a plan of ${signed(a.plannedPerWeek)} kg/week. " +
                        "Your body burns ${if (a.delta > 0) "more" else "less"} than the formula estimates."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { vm.applyAdaptive(a.delta) }) { Text(L("Điều chỉnh mục tiêu ${if (a.delta > 0) "+" else ""}${a.delta} kcal/ngày", "Adjust target by ${if (a.delta > 0) "+" else ""}${a.delta} kcal/day")) }
            }
        }
    }

    item {
        SectionCard(title = L("Calo nạp vào / tiêu hao", "Calories in / out")) {
            val outs = days.map { Nutrition.kcalOut(data, it).total.toFloat() }
            BarChart(days, listOf(totals.map { it.kcal }, outs), listOf(Palette.In, Palette.Out.copy(alpha = 0.75f)), target.toFloat())
            if (logged.isEmpty()) Text(L("Chưa có nhật ký trong khoảng này.", "Nothing logged in this period."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                val balance = logged.sumOf { (totals[it].kcal - outs[it]).toDouble() }
                Text(L("Trung bình nạp ${logged.map { totals[it].kcal }.average().roundToInt()} · tiêu ${logged.map { outs[it] }.average().roundToInt()} kcal/ngày", "Average in ${logged.map { totals[it].kcal }.average().roundToInt()} · out ${logged.map { outs[it] }.average().roundToInt()} kcal/day"), style = MaterialTheme.typography.bodyMedium)
                Text(
                    L("${logged.size}/$range ngày có ghi · ${if (balance <= 0) "thâm hụt" else "dư"} ${abs(balance).roundToInt()} kcal ≈ ${signed((balance / 7700.0).toFloat())} kg · đạt mục tiêu ${days.count { Nutrition.onTarget(data, it) }} ngày",
                        "${logged.size}/$range days logged · ${if (balance <= 0) "deficit" else "surplus"} ${abs(balance).roundToInt()} kcal ≈ ${signed((balance / 7700.0).toFloat())} kg · on target ${days.count { Nutrition.onTarget(data, it) }} days"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    item {
        SectionCard(title = L("Cân nặng", "Weight")) {
            val from = days.first().toString()
            val points = data.weights.filter { it.date >= from }
            if (points.size >= 2) {
                LineChart(points.map { it.kg }, MaterialTheme.colorScheme.tertiary, Modifier.fillMaxWidth().height(110.dp))
                Text("${"%.1f".format(points.first().kg)} → ${"%.1f".format(points.last().kg)} kg (${signed(points.last().kg - points.first().kg)} kg)", fontWeight = FontWeight.SemiBold)
            } else {
                Text("${"%.1f".format(p.weightKg)} kg · BMI ${"%.1f".format(Nutrition.bmi(p))} (${Nutrition.bmiLabel(Nutrition.bmi(p))})", fontWeight = FontWeight.SemiBold)
                Text(L("Cân mỗi tuần vào cùng một buổi sáng để thấy đường xu hướng.", "Weigh yourself weekly on the same morning to see the trend line."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (logged.isNotEmpty()) item {
        SectionCard(title = L("Đa lượng trung bình mỗi ngày", "Average daily macros")) {
            val macros = Nutrition.macroTargets(p, target)
            MacroBar(L("Đạm", "Protein"), logged.map { totals[it].protein }.average().toFloat(), macros.protein, Palette.Protein)
            MacroBar(L("Tinh bột", "Carbs"), logged.map { totals[it].carbs }.average().toFloat(), macros.carbs, Palette.Carbs)
            MacroBar(L("Chất béo", "Fat"), logged.map { totals[it].fat }.average().toFloat(), macros.fat, Palette.Fat)
        }
    }

    item {
        val wear = days.map { data.wearable[it.toString()] }
        val steps = wear.map { (it?.steps ?: 0).toFloat() }
        val sleep = wear.mapNotNull { it?.sleepMin?.takeIf { m -> m > 0 } }
        val from = days.first().toString()
        val workouts = data.exercises.filter { it.date >= from }
        SectionCard(title = L("Vận động & phục hồi", "Activity & recovery")) {
            if (steps.any { it > 0f }) {
                BarChart(days, listOf(steps), listOf(MaterialTheme.colorScheme.tertiary), 8000f)
                Text(L("👟 Trung bình ${steps.filter { it > 0f }.average().roundToInt()} bước/ngày (đường kẻ: 8.000 bước)", "👟 Average ${steps.filter { it > 0f }.average().roundToInt()} steps/day (line: 8,000 steps)"), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(L("👟 Chưa có dữ liệu bước chân — kết nối đồng hồ ở bên dưới.", "👟 No step data yet — connect a watch below."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(L("🏋️ ${workouts.size} buổi tập tự ghi · ${workouts.sumOf { it.minutes }} phút · ${workouts.sumOf { it.kcal }} kcal", "🏋️ ${workouts.size} logged workouts · ${workouts.sumOf { it.minutes }} min · ${workouts.sumOf { it.kcal }} kcal"), style = MaterialTheme.typography.bodyMedium)
            if (sleep.isNotEmpty()) {
                val avg = sleep.average().roundToInt()
                Text(L("😴 Ngủ trung bình ${avg / 60}h${"%02d".format(avg % 60)} mỗi đêm", "😴 Average sleep ${avg / 60}h${"%02d".format(avg % 60)} a night") + if (avg < 390) L(" — dưới mức 7 giờ khuyến nghị", " — below the recommended 7 hours") else "", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun signed(v: Float) = (if (v > 0f) "+" else if (v < 0f) "−" else "") + "%.2f".format(abs(v))
