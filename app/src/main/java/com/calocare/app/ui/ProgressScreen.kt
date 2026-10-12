package com.calocare.app.ui

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
        else vm.messages.tryEmit("Chưa cấp quyền đọc dữ liệu sức khỏe.")
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tiến độ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { weightDialog = true }) { Text("⚖️ Cân hôm nay") }
            }
        }

        progressCharts(vm, data, range) { range = it }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vận động trong ngày", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                DateSwitcher(vm)
            }
        }

        item {
            SectionCard(title = "🔥 Calo tiêu hao: ${out.total} kcal") {
                Line("Trao đổi chất cơ bản (BMR)", "${out.bmr}")
                Line(if (out.fromWearable) "Vận động (từ đồng hồ ⌚)" else "Sinh hoạt hằng ngày (ước tính)", "${out.active}")
                if (out.exercise > 0) Line("Tập luyện tự nhập", "${out.exercise}")
                if (out.fromWearable && out.steps > 0) Line("Số bước", "${out.steps}")
                if (out.fromWearable && out.exercise > 0) {
                    Text("Lưu ý: nếu đồng hồ đã ghi buổi tập này thì không cần nhập tay, tránh tính hai lần.", style = MaterialTheme.typography.bodySmall, color = Palette.Warn)
                }
            }
        }

        item {
            SectionCard(title = "⌚ Đồng hồ & vòng tay thông minh") {
                when (vm.health.status()) {
                    HealthConnectClient.SDK_AVAILABLE -> {
                        Text(
                            if (data.profile.wearableLinked) "Đang lấy calo vận động, số bước, giấc ngủ, cân nặng, chiều cao từ Health Connect."
                            else "Kết nối qua Health Connect để tự động lấy calo tiêu hao, số bước, giấc ngủ, cân nặng và chiều cao từ Wear OS, Samsung Health, Fitbit, Garmin, Mi Fitness / Zepp…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (vm.wearableNote.isNotBlank()) Text(vm.wearableNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (data.profile.wearableLinked) {
                                FilledTonalButton(onClick = { vm.syncWearable(announce = true) }, enabled = !vm.wearableBusy) { Text("Đồng bộ ngay") }
                                TextButton(onClick = { requestPermissions.launch(vm.health.permissions) }) { Text("Quyền truy cập") }
                            } else {
                                Button(onClick = { requestPermissions.launch(vm.health.permissions) }) { Text("Kết nối thiết bị") }
                            }
                            if (vm.wearableBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                        Text("Cần cài hoặc cập nhật ứng dụng Health Connect của Google để kết nối đồng hồ.")
                        Button(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }) { Text("Mở CH Play") }
                    }
                    else -> Text("Thiết bị này không hỗ trợ Health Connect. Bạn vẫn có thể nhập vận động và cân nặng bằng tay bên dưới.")
                }
            }
        }

        item {
            SectionCard(title = "Thêm vận động") {
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
                        Text("${e.name} · ${e.minutes} phút", modifier = Modifier.weight(1f))
                        Text("−${e.kcal} kcal", fontWeight = FontWeight.SemiBold, color = Palette.Out)
                        IconButton(onClick = { vm.removeExercise(e.id) }) { Icon(Icons.Default.Close, "Xóa") }
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
                    Text("≈ ${Nutrition.exerciseKcal(activity.met, data.profile.weightKg, minutes)} kcal với cân nặng ${"%.0f".format(data.profile.weightKg)} kg", fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = { Button(onClick = { vm.addExercise(activity, minutes); picking = null }) { Text("Thêm") } },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Hủy") } },
        )
    }

    if (weightDialog) {
        var text by remember { mutableStateOf("%.1f".format(data.profile.weightKg).replace(',', '.')) }
        val kg = text.toFloatOrNull()
        AlertDialog(
            onDismissRequest = { weightDialog = false },
            title = { Text("Cân nặng hôm nay") },
            text = { NumberField(text, { text = it }, "kg", decimal = true) },
            confirmButton = { Button(enabled = kg != null && kg in 25f..300f, onClick = { vm.logWeight(kg!!); weightDialog = false }) { Text("Lưu") } },
            dismissButton = { TextButton(onClick = { weightDialog = false }) { Text("Hủy") } },
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
