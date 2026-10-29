package com.calocare.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.calocare.app.AppViewModel
import com.calocare.app.L
import com.calocare.app.data.AppData
import com.calocare.app.net.NutritionReport
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AnalyticsScreen(vm: AppViewModel, data: AppData, padding: PaddingValues) {
    var period by remember { mutableStateOf("week") }
    var report by remember { mutableStateOf<NutritionReport?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun generate() {
        loading = true
        vm.viewModelScope.launch {
            report = vm.api.nutritionReport(data.recentDays(if (period == "week") 7 else 30), period)
            loading = false
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L("📊 Phân tích dinh dưỡng", "📊 Nutrition analytics"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { generate() }) { Icon(Icons.Default.Refresh, L("Tải lại", "Refresh")) }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("week" to L("Tuần", "Week"), "month" to L("Tháng", "Month")).forEach { (p, label) ->
                    FilterChip(period == p, { period = p; report = null }, label = { Text(label) })
                }
            }
        }

        val current = report
        when {
            loading -> item {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            current == null -> item {
                FilledTonalButton(onClick = { generate() }, modifier = Modifier.fillMaxWidth()) {
                    Text(L("Tạo báo cáo", "Generate report"))
                }
            }
            else -> {
                item {
                    SectionCard(title = L("🎯 Trung bình ngày", "🎯 Daily average")) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Line(L("Năng lượng", "Energy"), "${current.avgKcal.roundToInt()} kcal")
                            Line(L("Đạm", "Protein"), "${current.avgProtein.roundToInt()}g")
                            Line(L("Tinh bột", "Carbs"), "${current.avgCarbs.roundToInt()}g")
                            Line(L("Béo", "Fat"), "${current.avgFat.roundToInt()}g")
                            Text(
                                L("Thấp nhất ${current.minKcal} kcal · Cao nhất ${current.maxKcal} kcal", "Lowest ${current.minKcal} kcal · Highest ${current.maxKcal} kcal"),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    SectionCard(title = L("🥗 Cấu trúc năng lượng", "🥗 Energy split")) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val split = current.macroBreakdown
                            MacroBar(L("Đạm", "Protein"), (split["protein"] ?: 0f), Color(0xFF4CAF50))
                            MacroBar(L("Tinh bột", "Carbs"), (split["carbs"] ?: 0f), Color(0xFF2196F3))
                            MacroBar(L("Béo", "Fat"), (split["fat"] ?: 0f), Color(0xFFFF9800))
                        }
                    }
                }
                item {
                    SectionCard(title = L("📈 Nhất quán", "📈 Consistency")) {
                        val score = current.consistencyScore.coerceIn(0f, 100f).roundToInt()
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(L("Điểm nhất quán", "Consistency score"), modifier = Modifier.weight(1f))
                                Text("$score%", fontWeight = FontWeight.SemiBold)
                            }
                            ProgressBar(score / 100f, scoreColor(score))
                            Text(
                                when {
                                    score >= 90 -> L("Rất ổn định", "Very consistent")
                                    score >= 70 -> L("Khá ổn định, có chút dao động", "Fairly steady, with some variation")
                                    score >= 50 -> L("Nên ổn định hơn", "Could be more steady")
                                    else -> L("Cố gắng giữ calo đều mỗi ngày", "Try to keep your daily intake even")
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroBar(label: String, fraction: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            Text("${(fraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
        ProgressBar(fraction, color)
    }
}

@Composable
private fun ProgressBar(fraction: Float, color: Color) {
    Box(
        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).background(color))
    }
}

private fun scoreColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF4CAF50)
    score >= 60 -> Color(0xFF8BC34A)
    score >= 40 -> Color(0xFFFFB74D)
    else -> Color(0xFFEF5350)
}
