package com.calocare.app.ui

import com.calocare.app.L
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.calocare.app.AppViewModel
import com.calocare.app.data.AppData
import com.calocare.app.data.Nutrition
import com.calocare.app.net.DayNutrition
import com.calocare.app.net.NutritionReport
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AnalyticsScreen(vm: AppViewModel, data: AppData, padding: PaddingValues) {
    var period by remember { mutableStateOf("week") }
    var report by remember { mutableStateOf<NutritionReport?>(null) }
    var loading by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L("📊 Phân tích dinh dưỡng", "📊 Nutrition analytics"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    loading = true
                    vm.viewModelScope.launch {
                        val entries = mutableListOf<DayNutrition>()
                        val daysCount = if (period == "week") 7 else if (period == "month") 30 else 1
                        val calendar = Calendar.getInstance()
                        for (i in 0 until daysCount) {
                            val date = calendar.time
                            entries.add(DayNutrition(
                                date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date),
                                foods = emptyList(),
                                kcal = Nutrition.kcalEaten(data, date).toInt(),
                                protein_g = Nutrition.proteinEaten(data, date),
                                carbs_g = Nutrition.carbsEaten(data, date),
                                fat_g = Nutrition.fatEaten(data, date),
                            ))
                            calendar.add(Calendar.DAY_OF_YEAR, -1)
                        }
                        report = vm.api.nutritionReport(entries.reversed(), period)
                        loading = false
                    }
                }) {
                    Icon(Icons.Default.Refresh, L("Tải lại", "Refresh"))
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("week" to L("Tuần", "Week"), "month" to L("Tháng", "Month")).forEach { (p, label) ->
                    FilterChip(period == p, { period = p; report = null }, label = { Text(label) })
                }
            }
        }

        if (loading) {
            item {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        } else if (report != null) {
            item {
                SectionCard(title = L("🎯 Trung bình ngày", "🎯 Daily average")) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Line(L("Năng lượng", "Energy"), "${report!!.avgKcal.roundToInt()} kcal")
                        Line(L("Đạm", "Protein"), "${report!!.avgProtein.roundToInt()}g")
                        Line(L("Tinh bột", "Carbs"), "${report!!.avgCarbs.roundToInt()}g")
                        Line(L("Béo", "Fat"), "${report!!.avgFat.roundToInt()}g")

                        Spacer(Modifier.padding(4.dp))

                        Text(L("Min: ${report!!.minKcal} kcal, Max: ${report!!.maxKcal} kcal", "Min: ${report!!.minKcal} kcal, Max: ${report!!.maxKcal} kcal"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                SectionCard(title = L("🥗 Cấu trúc Macro", "🥗 Macro composition")) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val breakdown = report!!.macroBreakdown
                        val protein = (breakdown["protein"] ?: 0f) * 100
                        val carbs = (breakdown["carbs"] ?: 0f) * 100
                        val fat = (breakdown["fat"] ?: 0f) * 100

                        macroBar(L("Đạm", "Protein"), protein.toInt(), Color(0xFF4CAF50))
                        macroBar(L("Tinh bột", "Carbs"), carbs.toInt(), Color(0xFF2196F3))
                        macroBar(L("Béo", "Fat"), fat.toInt(), Color(0xFFFF9800))
                    }
                }
            }

            item {
                SectionCard(title = L("📈 Nhất quán", "📈 Consistency")) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val score = report!!.consistencyScore.coerceIn(0f, 100f).roundToInt()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(L("Điểm nhất quán", "Consistency score"), modifier = Modifier.weight(1f))
                            Text("$score%", fontWeight = FontWeight.SemiBold)
                        }

                        // Simple progress bar
                        androidx.compose.foundation.background(
                            when {
                                score >= 80 -> Color(0xFF4CAF50)
                                score >= 60 -> Color(0xFF8BC34A)
                                score >= 40 -> Color(0xFFFFB74D)
                                else -> Color(0xFFEF5350)
                            },
                            androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                        ).run {
                            this.fillMaxWidth(score / 100f).padding(vertical = 4.dp)
                        }

                        Text(
                            when {
                                score >= 90 -> L("Rất tốt! Bạn giữ calo nhất quán", "Excellent! Your intake is very consistent")
                                score >= 70 -> L("Tốt. Có một chút biến động", "Good. Some variation is normal")
                                score >= 50 -> L("Bạn cần ổn định hơn", "You could be more stable")
                                else -> L("Cố gắng giữ calo hàng ngày", "Try to keep your daily intake more stable")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            item {
                FilledTonalButton(onClick = {
                    loading = true
                    vm.viewModelScope.launch {
                        val entries = mutableListOf<DayNutrition>()
                        val daysCount = if (period == "week") 7 else if (period == "month") 30 else 1
                        val calendar = Calendar.getInstance()
                        for (i in 0 until daysCount) {
                            val date = calendar.time
                            entries.add(DayNutrition(
                                date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date),
                                foods = emptyList(),
                                kcal = Nutrition.kcalEaten(data, date).toInt(),
                                protein_g = Nutrition.proteinEaten(data, date),
                                carbs_g = Nutrition.carbsEaten(data, date),
                                fat_g = Nutrition.fatEaten(data, date),
                            ))
                            calendar.add(Calendar.DAY_OF_YEAR, -1)
                        }
                        report = vm.api.nutritionReport(entries.reversed(), period)
                        loading = false
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(L("Tạo báo cáo", "Generate report"))
                }
            }
        }
    }
}

@Composable
private fun macroBar(label: String, percent: Int, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            Text("$percent%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
        androidx.compose.foundation.background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
            .run {
                this.fillMaxWidth().padding(vertical = 4.dp)
            }
        androidx.compose.foundation.background(color, androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
            .fillMaxWidth(percent / 100f).padding(vertical = 4.dp)
    }
}
