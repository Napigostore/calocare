package com.calocare.app.ui

import com.calocare.app.L
import android.content.Intent
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(vm: AppViewModel, data: AppData, padding: PaddingValues) {
    val context = LocalContext.current
    val out = Nutrition.kcalOut(data, vm.date)
    val exercises = data.exercisesOn(vm.date)
    var picking by remember { mutableStateOf<FoodDb.Activity?>(null) }
    var weightDialog by remember { mutableStateOf(false) }
    var range by rememberSaveable { mutableIntStateOf(7) }

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
