package com.calocare.app.ui

import com.calocare.app.data.FoodPhotos
import com.calocare.app.label
import com.calocare.app.L
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calocare.app.AppViewModel
import com.calocare.app.data.AppData
import com.calocare.app.data.Goal
import com.calocare.app.data.Meal
import com.calocare.app.data.MenuPlanner
import com.calocare.app.data.Nutrition
import com.calocare.app.data.entriesOn
import com.calocare.app.data.totals
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d/M", Locale("vi"))

fun dateLabel(date: LocalDate): String = when (date) {
    LocalDate.now() -> L("Hôm nay", "Today")
    LocalDate.now().minusDays(1) -> L("Hôm qua", "Yesterday")
    else -> date.format(dayFormat).replaceFirstChar { it.uppercase() }
}

@Composable
fun DateSwitcher(vm: AppViewModel, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.date = vm.date.minusDays(1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = L("Ngày trước", "Previous day"))
        }
        Text(dateLabel(vm.date), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.width(110.dp))
        IconButton(onClick = { vm.date = vm.date.plusDays(1) }, enabled = vm.date.isBefore(LocalDate.now())) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = L("Ngày sau", "Next day"))
        }
    }
}

@Composable
fun DashboardScreen(vm: AppViewModel, data: AppData, padding: PaddingValues, onCoach: () -> Unit, onAddFood: () -> Unit, onProfile: () -> Unit) {
    val p = data.profile
    val date = vm.date
    val entries = data.entriesOn(date)
    val eaten = entries.totals()
    val target = Nutrition.targetKcal(p)
    val macros = Nutrition.macroTargets(p, target)
    val out = Nutrition.kcalOut(data, date)

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    L("Xin chào", "Hi") + (if (p.name.isNotBlank()) ", ${p.name}" else "") + " 👋", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(onClick = onProfile) { Icon(Icons.Default.Person, contentDescription = L("Hồ sơ", "Profile")) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${Goal.emoji[p.goal]} ${Goal.labels[p.goal]}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f),
                )
                DateSwitcher(vm)
            }
        }

        item {
            // Hero: ring of calories eaten against the daily target, with the in / out / balance figures beside it.
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CalorieRing(eaten.kcal, target.toFloat(), Modifier.size(150.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat(L("Nạp vào", "Eaten"), "${eaten.kcal.roundToInt()}", Palette.In)
                        Stat(L("Tiêu hao", "Burned") + if (out.fromWearable) " ⌚" else "", "${out.total}", Palette.Out)
                        val net = eaten.kcal.roundToInt() - out.total
                        Stat(if (net <= 0) L("Thâm hụt", "Deficit") else L("Dư thừa", "Surplus"), "${abs(net)}", if (net <= 0) Palette.Fat else Palette.Danger)
                    }
                }
                Text(
                    L("Tiêu hao = trao đổi chất cơ bản ${out.bmr} + vận động ${out.active}", "Burned = basal metabolism ${out.bmr} + activity ${out.active}") +
                        (if (out.exercise > 0) L(" + tập luyện ${out.exercise}", " + workouts ${out.exercise}") else "") + " kcal",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            val (emoji, text) = Nutrition.cheer(data, date)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer)))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(emoji, fontSize = 34.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(text, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)
                    val streak = Nutrition.streak(data)
                    if (streak >= 2) Text(L("🔥 Chuỗi $streak ngày ghi nhật ký", "🔥 $streak-day logging streak"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                if (entries.isEmpty()) Button(onClick = onAddFood) { Text(L("Thêm món", "Add food")) }
            }
        }

        if (date == LocalDate.now()) item { NextMealCard(vm, data) }

        item {
            SectionCard(title = L("Dinh dưỡng đa lượng", "Macronutrients")) {
                MacroBar(L("Đạm", "Protein"), eaten.protein, macros.protein, Palette.Protein)
                MacroBar(L("Tinh bột", "Carbs"), eaten.carbs, macros.carbs, Palette.Carbs)
                MacroBar(L("Chất béo", "Fat"), eaten.fat, macros.fat, Palette.Fat)
            }
        }

        item { WeekCard(data, target) }

        item {
            SectionCard(title = L("🩺 Chuyên gia khuyên", "🩺 Expert tips"), trailing = { FilledTonalButton(onClick = onCoach) { Text(L("Hỏi AI", "Ask AI")) } }) {
                // Health conflicts in what was eaten today come first, then the general coaching tips.
                val flagged = entries.flatMap { e -> Nutrition.warnings(e.tags, p).map { e.name to it } }.distinct()
                flagged.take(4).forEach { (name, w) -> WarningLine(w.copy(text = "$name: ${w.text}")) }
                Nutrition.tips(data, date).take(4).forEach {
                    Text("• $it", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item { WeightCard(data) }
    }
}

/** "What should I eat next?": the coach's answer right after every logged meal. */
@Composable
fun NextMealCard(vm: AppViewModel, data: AppData) {
    var seed by rememberSaveable { mutableIntStateOf(LocalDate.now().dayOfYear) }
    val next = remember(data.entries, data.profile, seed) { MenuPlanner.nextMeal(data, LocalTime.now().hour, seed) }
    if (next.meal.items.isEmpty()) return
    SectionCard(
        title = L("🍽️ Bữa tiếp theo nên ăn · ${Meal.labels[next.meal.meal]}", "🍽️ What to eat next · ${Meal.labels[next.meal.meal]}"),
        trailing = { TextButton(onClick = { seed++ }) { Text(L("Đổi món", "Shuffle")) } },
    ) {
        Text(next.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        next.meal.items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                FoodImage(item.food.emoji, item.food.imageUrl ?: FoodPhotos.url(item.food.id), 32.dp)
                Spacer(Modifier.width(10.dp))
                Text("${item.food.label} × ${AppViewModel.fmtQty(item.qty)}", modifier = Modifier.weight(1f))
                Text("${(item.food.kcal * item.qty).roundToInt()} kcal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FilledTonalButton(onClick = { vm.addMeal(next.meal.items.map { it.food to it.qty }, next.meal.meal) }) {
            Text(L("Tôi ăn bữa này · ${next.meal.kcal} kcal", "I'll eat this · ${next.meal.kcal} kcal"))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(" kcal", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
fun CalorieRing(eaten: Float, target: Float, modifier: Modifier = Modifier) {
    val ratio = if (target > 0f) eaten / target else 0f
    val sweep by animateFloatAsState(ratio.coerceIn(0f, 1f), tween(700), label = "ring")
    val over by animateFloatAsState((ratio - 1f).coerceIn(0f, 1f), tween(700), label = "over")
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize().padding(8.dp)) {
            val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, -90f, 360f, false, style = stroke)
            drawArc(Brush.sweepGradient(listOf(Palette.In, Color(0xFF7BC96F), Palette.In)), -90f, 360f * sweep, false, style = stroke)
            // A second, red lap shows how far past the target the day went.
            if (over > 0f) drawArc(Palette.Danger, -90f, 360f * over, false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val left = (target - eaten).roundToInt()
            Text("${abs(left)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (left < 0) Palette.Danger else MaterialTheme.colorScheme.onSurface)
            Text(if (left >= 0) L("kcal còn lại", "kcal left") else L("kcal vượt", "kcal over"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("/ ${target.roundToInt()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MacroBar(label: String, value: Float, target: Int, color: Color) {
    val progress by animateFloatAsState(if (target > 0) (value / target).coerceIn(0f, 1f) else 0f, tween(600), label = label)
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${value.roundToInt()} / $target g", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress }, color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
        )
    }
}

@Composable
private fun WeekCard(data: AppData, target: Int) {
    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val ins = days.map { data.entriesOn(it).totals().kcal }
    val outs = days.map { Nutrition.kcalOut(data, it).total.toFloat() }
    val logged = days.indices.filter { ins[it] > 0f }
    val scaleMax = max(max(ins.max(), outs.max()), target.toFloat()) * 1.1f
    val targetColor = MaterialTheme.colorScheme.onSurfaceVariant

    SectionCard(title = L("Xu hướng 7 ngày", "7-day trend")) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(L("Nạp vào", "Eaten"), Palette.In)
            Legend(L("Tiêu hao", "Burned"), Palette.Out)
            Legend(L("Mục tiêu", "Target"), targetColor)
        }
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val slot = size.width / days.size
            val bar = slot * 0.26f
            fun y(v: Float) = size.height - v / scaleMax * size.height
            days.indices.forEach { i ->
                val x = slot * i + slot / 2f
                if (ins[i] > 0f) drawRoundRect(Palette.In, Offset(x - bar - 2f, y(ins[i])), Size(bar, size.height - y(ins[i])), CornerRadius(bar / 3f))
                drawRoundRect(Palette.Out.copy(alpha = if (ins[i] > 0f) 1f else 0.35f), Offset(x + 2f, y(outs[i])), Size(bar, size.height - y(outs[i])), CornerRadius(bar / 3f))
            }
            drawLine(targetColor, Offset(0f, y(target.toFloat())), Offset(size.width, y(target.toFloat())), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        Row {
            days.forEach { d ->
                val hit = Nutrition.onTarget(data, d)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")[d.dayOfWeek.value - 1], style = MaterialTheme.typography.labelSmall, fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal)
                    Text(if (hit) "🎯" else "·", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (logged.isEmpty()) {
            Text(L("Ghi nhật ký vài ngày để thấy xu hướng của bạn.", "Log a few days to see your trend."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val avgIn = logged.map { ins[it] }.average().roundToInt()
            val avgOut = logged.map { outs[it] }.average().roundToInt()
            val balance = logged.sumOf { (ins[it] - outs[it]).toDouble() }
            val hits = days.count { Nutrition.onTarget(data, it) }
            // 7700 kcal is the usual estimate for one kilogram of body fat.
            val kg = balance / 7700.0
            Text(L("Trung bình nạp $avgIn · tiêu $avgOut kcal/ngày (${logged.size} ngày có ghi)", "Average in $avgIn · out $avgOut kcal/day (${logged.size} days logged)"), style = MaterialTheme.typography.bodyMedium)
            Text(
                L("Cân bằng: ${if (balance <= 0) "thâm hụt" else "dư"} ${abs(balance).roundToInt()} kcal ≈ ${if (kg <= 0) "−" else "+"}${"%.2f".format(abs(kg))} kg · Đạt mục tiêu $hits/7 ngày",
                    "Balance: ${if (balance <= 0) "deficit" else "surplus"} of ${abs(balance).roundToInt()} kcal ≈ ${if (kg <= 0) "−" else "+"}${"%.2f".format(abs(kg))} kg · On target $hits/7 days"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun WeightCard(data: AppData) {
    val p = data.profile
    val bmi = Nutrition.bmi(p)
    SectionCard(title = L("Cân nặng", "Weight")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${"%.1f".format(p.weightKg)} kg", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("BMI ${"%.1f".format(bmi)} · ${Nutrition.bmiLabel(bmi)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (p.goal == Goal.LOSE || p.goal == Goal.GAIN) {
                    val diff = p.targetWeightKg - p.weightKg
                    Text(
                        if (abs(diff) < 0.1f) L("🏆 Đã chạm cân nặng mục tiêu!", "🏆 You've reached your target weight!")
                        else L("Còn ${"%.1f".format(abs(diff))} kg tới mục tiêu ${"%.1f".format(p.targetWeightKg)} kg", "${"%.1f".format(abs(diff))} kg to go to your ${"%.1f".format(p.targetWeightKg)} kg target"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            val points = data.weights.takeLast(14).map { it.kg }
            if (points.size >= 2) {
                val line = MaterialTheme.colorScheme.tertiary
                Canvas(Modifier.width(130.dp).height(56.dp)) {
                    val lo = points.min() - 0.3f
                    val hi = points.max() + 0.3f
                    val step = size.width / (points.size - 1)
                    val pts = points.mapIndexed { i, v -> Offset(i * step, size.height - (v - lo) / (hi - lo) * size.height) }
                    for (i in 1 until pts.size) drawLine(line, pts[i - 1], pts[i], strokeWidth = 5f, cap = StrokeCap.Round)
                    drawCircle(line, 7f, pts.last())
                }
            }
        }
    }
}
