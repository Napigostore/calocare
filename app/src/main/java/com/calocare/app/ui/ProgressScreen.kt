package com.calocare.app.ui

import com.calocare.app.L
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.calocare.app.AppViewModel
import com.calocare.app.data.AppData
import com.calocare.app.data.FoodDb
import com.calocare.app.data.Nutrition
import com.calocare.app.data.exercisesOn
import com.calocare.app.net.HabitScore
import com.calocare.app.net.MacroTargets
import com.calocare.app.net.InsightResult
import com.calocare.app.net.NutritionSummary
import com.calocare.app.net.UserGoal
import com.calocare.app.net.UserMetrics
import com.calocare.app.net.DayNutrition
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(vm: AppViewModel, data: AppData, padding: PaddingValues) {
    val context = LocalContext.current
    val out = Nutrition.kcalOut(data, vm.date)
    val exercises = data.exercisesOn(vm.date)
    var picking by remember { mutableStateOf<FoodDb.Activity?>(null) }
    var weightDialog by remember { mutableStateOf(false) }
    var range by rememberSaveable { mutableIntStateOf(7) }
    var habitScore by remember { mutableStateOf<HabitScore?>(null) }
    var habitScoreLoading by remember { mutableStateOf(false) }
    var insights by remember { mutableStateOf<InsightResult?>(null) }
    var insightsLoading by remember { mutableStateOf(false) }
    var customGoalExpanded by remember { mutableStateOf(false) }
    var selectedGoal by remember { mutableStateOf("maintain") }
    var weeklyRate by remember { mutableStateOf(0f) }
    var customMacros by remember { mutableStateOf<MacroTargets?>(null) }
    var customMacrosLoading by remember { mutableStateOf(false) }

    val requestPermissions = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.isNotEmpty()) vm.syncWearable(announce = true)
        else vm.messages.tryEmit(L("Chưa cấp quyền đọc dữ liệu sức khỏe.", "Health data permission not granted."))
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L("Tiến độ", "Progress"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { weightDialog = true }) { Text(L("⚖️ Cân hôm nay", "⚖️ Weigh in")) }
            }
        }

        progressCharts(vm, data, range) { range = it }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L("Vận động trong ngày", "Today's activity"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                DateSwitcher(vm)
            }
        }

        item {
            SectionCard(title = L("🔥 Calo tiêu hao: ${out.total} kcal", "🔥 Calories burned: ${out.total} kcal")) {
                Line(L("Trao đổi chất cơ bản (BMR)", "Basal metabolism (BMR)"), "${out.bmr}")
                Line(if (out.fromWearable) L("Vận động (từ đồng hồ ⌚)", "Activity (from watch ⌚)") else L("Sinh hoạt hằng ngày (ước tính)", "Daily living (estimate)"), "${out.active}")
                if (out.exercise > 0) Line(L("Tập luyện tự nhập", "Logged workouts"), "${out.exercise}")
                if (out.fromWearable && out.steps > 0) Line(L("Số bước", "Steps"), "${out.steps}")
                if (out.fromWearable && out.exercise > 0) {
                    Text(L("Lưu ý: nếu đồng hồ đã ghi buổi tập này thì không cần nhập tay, tránh tính hai lần.", "Note: if your watch already recorded this workout, don't add it by hand or it counts twice."), style = MaterialTheme.typography.bodySmall, color = Palette.Warn)
                }
            }
        }

        item {
            SectionCard(title = L("⌚ Đồng hồ & vòng tay thông minh", "⌚ Smartwatches & fitness bands")) {
                when (vm.health.status()) {
                    HealthConnectClient.SDK_AVAILABLE -> {
                        Text(
                            if (data.profile.wearableLinked) L("Đang lấy calo vận động, số bước, giấc ngủ, cân nặng, chiều cao từ Health Connect.", "Reading active calories, steps, sleep, weight and height from Health Connect.")
                            else L("Kết nối qua Health Connect để tự động lấy calo tiêu hao, số bước, giấc ngủ, cân nặng và chiều cao từ Wear OS, Samsung Health, Fitbit, Garmin, Mi Fitness / Zepp…", "Connect through Health Connect to bring in calories burned, steps, sleep, weight and height from Wear OS, Samsung Health, Fitbit, Garmin, Mi Fitness / Zepp…"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (vm.wearableNote.isNotBlank()) Text(vm.wearableNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (data.profile.wearableLinked) {
                                FilledTonalButton(onClick = { vm.syncWearable(announce = true) }, enabled = !vm.wearableBusy) { Text(L("Đồng bộ ngay", "Sync now")) }
                                TextButton(onClick = { requestPermissions.launch(vm.health.permissions) }) { Text(L("Quyền truy cập", "Permissions")) }
                            } else {
                                Button(onClick = { requestPermissions.launch(vm.health.permissions) }) { Text(L("Kết nối thiết bị", "Connect a device")) }
                            }
                            if (vm.wearableBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                        Text(L("Cần cài hoặc cập nhật ứng dụng Health Connect của Google để kết nối đồng hồ.", "Install or update Google's Health Connect app to connect a watch."))
                        Button(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }) { Text(L("Mở CH Play", "Open Play Store")) }
                    }
                    else -> Text(L("Thiết bị này không hỗ trợ Health Connect. Bạn vẫn có thể nhập vận động và cân nặng bằng tay bên dưới.", "This device doesn't support Health Connect. You can still enter activity and weight by hand below."))
                }
            }
        }

        item {
            habitScoreCard(habitScore, habitScoreLoading) {
                habitScoreLoading = true
                vm.viewModelScope.launch {
                    val today = DayNutrition(
                        date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(vm.date),
                        foods = data.entriesOn(vm.date).map { it.label },
                        kcal = Nutrition.kcalEaten(data, vm.date).toInt(),
                        protein_g = Nutrition.proteinEaten(data, vm.date),
                        carbs_g = Nutrition.carbsEaten(data, vm.date),
                        fat_g = Nutrition.fatEaten(data, vm.date),
                    )
                    val target = UserGoal(
                        kcal = 2000,
                        protein_g = 150f,
                        carbs_g = 200f,
                        fat_g = 65f,
                    )
                    habitScore = vm.api.habitScore(today, target)
                    habitScoreLoading = false
                }
            }
        }

        item {
            customGoalsCard(
                selectedGoal = selectedGoal,
                onGoalChange = { selectedGoal = it },
                weeklyRate = weeklyRate,
                onRateChange = { weeklyRate = it },
                customMacros = customMacros,
                isLoading = customMacrosLoading,
                data = data
            ) {
                customMacrosLoading = true
                vm.viewModelScope.launch {
                    val metrics = UserMetrics(
                        weightKg = data.profile.weightKg,
                        heightCm = data.profile.heightCm.toFloat(),
                        age = Calendar.getInstance().get(Calendar.YEAR) - data.profile.birthYear,
                        male = data.profile.male,
                        activityLevel = 1.5f,
                        goal = selectedGoal,
                        weeklyRateKg = if (weeklyRate != 0f) weeklyRate else null,
                    )
                    customMacros = vm.api.macroTargets(metrics)
                    customMacrosLoading = false
                }
            }
        }

        item {
            insightsCard(insights, insightsLoading) {
                insightsLoading = true
                vm.viewModelScope.launch {
                    val summary = buildWeeklySummary(data)
                    insights = vm.api.reportInsights(summary)
                    insightsLoading = false
                }
            }
        }

        item {
            SectionCard(title = L("Thêm vận động", "Add activity")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FoodDb.activities.forEach { a ->
                        Column(
                            Modifier.width(76.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { picking = a }.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(a.emoji, fontSize = 26.sp)
                            Text(a.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
                exercises.forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(e.emoji, fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(L("${e.name} · ${e.minutes} phút", "${e.name} · ${e.minutes} min"), modifier = Modifier.weight(1f))
                        Text("−${e.kcal} kcal", fontWeight = FontWeight.SemiBold, color = Palette.Out)
                        IconButton(onClick = { vm.removeExercise(e.id) }) { Icon(Icons.Default.Close, L("Xóa", "Delete")) }
                    }
                }
            }
        }

    }

    picking?.let { activity ->
        var minutes by remember(activity) { mutableStateOf(30) }
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text("${activity.emoji} ${activity.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 15, 20, 30, 45, 60, 90, 120).forEach { m ->
                            FilterChip(minutes == m, { minutes = m }, label = { Text("$m′") })
                        }
                    }
                    Text(L("≈ ${Nutrition.exerciseKcal(activity.met, data.profile.weightKg, minutes)} kcal với cân nặng ${"%.0f".format(data.profile.weightKg)} kg", "≈ ${Nutrition.exerciseKcal(activity.met, data.profile.weightKg, minutes)} kcal at ${"%.0f".format(data.profile.weightKg)} kg"), fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = { Button(onClick = { vm.addExercise(activity, minutes); picking = null }) { Text(L("Thêm", "Add")) } },
            dismissButton = { TextButton(onClick = { picking = null }) { Text(L("Hủy", "Cancel")) } },
        )
    }

    if (weightDialog) {
        var text by remember { mutableStateOf("%.1f".format(data.profile.weightKg).replace(',', '.')) }
        val kg = text.toFloatOrNull()
        AlertDialog(
            onDismissRequest = { weightDialog = false },
            title = { Text(L("Cân nặng hôm nay", "Today's weight")) },
            text = { NumberField(text, { text = it }, "kg", decimal = true) },
            confirmButton = { Button(enabled = kg != null && kg in 25f..300f, onClick = { vm.logWeight(kg!!); weightDialog = false }) { Text(L("Lưu", "Save")) } },
            dismissButton = { TextButton(onClick = { weightDialog = false }) { Text(L("Hủy", "Cancel")) } },
        )
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun habitScoreCard(
    habitScore: HabitScore?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    SectionCard(title = L("🎯 Điểm ăn uống hôm nay", "🎯 Eating habit score")) {
        if (isLoading) {
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else if (habitScore != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Overall score with color bar
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(L("Tổng điểm", "Overall score"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${habitScore.overall}/100", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(80.dp).background(scoreColor(habitScore.overall), RoundedCornerShape(40.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${habitScore.overall}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // Streak
                if (habitScore.streak > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(L("🔥 Chuỗi ngày tốt", "🔥 Good day streak"), style = MaterialTheme.typography.labelMedium)
                        Text("${habitScore.streak} ${L("ngày", "days")}", fontWeight = FontWeight.SemiBold)
                    }
                }

                // Component scores
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    scoreBar(L("Đa dạng", "Diversity"), habitScore.diversity)
                    scoreBar(L("Cân bằng macro", "Macro balance"), habitScore.macroBalance)
                }

                // Next badge
                if (habitScore.nextBadge != null) {
                    Text(L("Tiếp theo: ${badgeName(habitScore.nextBadge)}", "Next: ${badgeName(habitScore.nextBadge)}"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                FilledTonalButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Text(L("Tải lại", "Refresh"))
                }
            }
        } else {
            FilledTonalButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text(L("Tính điểm", "Calculate score"))
            }
        }
    }
}

@Composable
private fun scoreBar(label: String, score: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            Text("$score/100", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
        Box(
            Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
                .clip(RoundedCornerShape(3.dp))
        ) {
            Box(Modifier.fillMaxWidth(score / 100f).height(6.dp).background(scoreColor(score)))
        }
    }
}

@Composable
private fun scoreColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF4CAF50)
    score >= 60 -> Color(0xFF8BC34A)
    score >= 40 -> Color(0xFFFFB74D)
    else -> Color(0xFFEF5350)
}

private fun badgeName(badge: String?): String = when (badge) {
    "week_warrior" -> "🏆 Week Warrior"
    "month_master" -> "👑 Month Master"
    "food_explorer" -> "🌍 Food Explorer"
    "macro_precision" -> "💯 Macro Precision"
    else -> badge ?: ""
}

@Composable
private fun customGoalsCard(
    selectedGoal: String,
    onGoalChange: (String) -> Unit,
    weeklyRate: Float,
    onRateChange: (Float) -> Unit,
    customMacros: MacroTargets?,
    isLoading: Boolean,
    data: AppData,
    onCalculate: () -> Unit,
) {
    SectionCard(title = L("🎯 Mục tiêu cá nhân", "🎯 Custom goals")) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Goal selector
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(L("Mục tiêu", "Goal"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(
                        "maintain" to L("Duy trì", "Maintain"),
                        "lose" to L("Giảm cân", "Lose weight"),
                        "gain" to L("Tăng cân", "Gain weight"),
                        "gym" to L("Tập gym", "Gym"),
                        "run" to L("Chạy bộ", "Running"),
                    ).forEach { (goal, label) ->
                        FilterChip(
                            selectedGoal == goal,
                            { onGoalChange(goal) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // Weekly rate for weight goals
            if (selectedGoal in listOf("lose", "gain")) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(L("Tốc độ", "Rate"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(L("${String.format("%.1f", weeklyRate)} kg/tuần", "${String.format("%.1f", weeklyRate)} kg/week"), style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(
                        weeklyRate,
                        { onRateChange(it) },
                        valueRange = 0.25f..1.0f,
                        steps = 3
                    )
                }
            }

            // Calculate macros
            if (isLoading) {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else if (customMacros != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(customMacros.explanation, style = MaterialTheme.typography.bodySmall)
                    Line(L("Protein", "Protein"), "${customMacros.proteinG}g")
                    Line(L("Carbs", "Carbs"), "${customMacros.carbsG}g")
                    Line(L("Fat", "Fat"), "${customMacros.fatG}g")
                    Line(L("Kcal", "Kcal"), "${customMacros.kcal}")
                }
            }

            FilledTonalButton(onClick = onCalculate, modifier = Modifier.fillMaxWidth()) {
                Text(L("Tính toán", "Calculate"))
            }
        }
    }
}

@Composable
private fun insightsCard(
    insights: InsightResult?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    SectionCard(title = L("💡 Lời khuyên ăn uống", "💡 Eating insights")) {
        if (isLoading) {
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else if (insights != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (insights.patterns.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(L("Xu hướng", "Patterns"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        insights.patterns.forEach { pattern ->
                            Text("• $pattern", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (insights.strengths.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(L("Điểm mạnh", "Strengths"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        insights.strengths.forEach { strength ->
                            Text("✓ $strength", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
                        }
                    }
                }

                if (insights.improvements.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(L("Cải thiện", "Improvements"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        insights.improvements.forEach { improve ->
                            Text("• $improve", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                FilledTonalButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Text(L("Tải lại", "Refresh"))
                }
            }
        } else {
            FilledTonalButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text(L("Phân tích", "Analyze"))
            }
        }
    }
}

private fun buildWeeklySummary(data: AppData): NutritionSummary {
    val calendar = Calendar.getInstance()
    val endDate = calendar.time
    calendar.add(Calendar.DAY_OF_YEAR, -7)
    val startDate = calendar.time
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    var totalKcal = 0
    var totalProtein = 0f
    var totalCarbs = 0f
    var totalFat = 0f

    val cal = Calendar.getInstance()
    cal.time = startDate
    for (i in 0..6) {
        totalKcal += Nutrition.kcalEaten(data, cal.time).toInt()
        totalProtein += Nutrition.proteinEaten(data, cal.time)
        totalCarbs += Nutrition.carbsEaten(data, cal.time)
        totalFat += Nutrition.fatEaten(data, cal.time)
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }

    return NutritionSummary(
        period = "week",
        startDate = dateFormat.format(startDate),
        endDate = dateFormat.format(endDate),
        daysCount = 7,
        totalDays = 7,
        totalKcal = totalKcal,
        totalProtein = totalProtein,
        totalCarbs = totalCarbs,
        totalFat = totalFat,
        avgKcal = totalKcal.toFloat() / 7,
        avgProtein = totalProtein / 7,
        avgCarbs = totalCarbs / 7,
        avgFat = totalFat / 7,
        mealsPerDay = 3.0f,
        courseBreakdown = mapOf("main" to 0.45f, "soup" to 0.20f, "side" to 0.15f, "dessert" to 0.10f),
    )
}
