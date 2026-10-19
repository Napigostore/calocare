package com.calocare.app.ui

import com.calocare.app.data.FoodPhotos
import com.calocare.app.label
import com.calocare.app.L
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calocare.app.AppViewModel
import com.calocare.app.data.AppData
import com.calocare.app.data.Condition
import com.calocare.app.data.Diet
import com.calocare.app.data.Goal
import com.calocare.app.data.LogEntry
import com.calocare.app.data.Meal
import com.calocare.app.data.MenuPlanner
import com.calocare.app.data.Nutrition
import com.calocare.app.data.Region
import java.time.LocalDate

@Composable
fun MenuScreen(vm: AppViewModel, data: AppData, padding: PaddingValues, onCoach: () -> Unit) {
    val profile = data.profile
    var goal by rememberSaveable { mutableStateOf(profile.goal) }
    // A new menu every day; "Đổi thực đơn" reshuffles.
    var seed by rememberSaveable { mutableIntStateOf(LocalDate.now().dayOfYear) }

    // Preview another goal with that goal's calorie target, without changing the profile.
    val preview = profile.copy(goal = goal)
    val target = Nutrition.targetKcal(preview)
    val macros = Nutrition.macroTargets(preview, target)
    val plan = remember(preview, seed) { MenuPlanner.plan(preview, goal, target, seed) }
    val total = plan.sumOf { it.kcal }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(L("Thực đơn gợi ý", "Suggested menu"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(L("Món ăn ${Region.labels[profile.region]} · đã lọc theo sức khỏe của bạn", "${Region.labels[profile.region]} dishes · filtered for your health"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Goal.labels.forEach { (id, label) ->
                    FilterChip(goal == id, { goal = id }, label = { Text("${Goal.emoji[id]} $label") })
                }
            }
        }
        item {
            SectionCard(
                title = L("≈ $total kcal · mục tiêu $target", "≈ $total kcal · target $target"),
                trailing = { TextButton(onClick = { seed++ }) { Icon(Icons.Default.Refresh, null); Text(L(" Đổi thực đơn", " New menu")) } },
            ) {
                Text(L("Đạm ${macros.protein}g · Tinh bột ${macros.carbs}g · Béo ${macros.fat}g mỗi ngày", "Protein ${macros.protein}g · Carbs ${macros.carbs}g · Fat ${macros.fat}g a day"), style = MaterialTheme.typography.bodyMedium)
                val filters = profile.conditions.mapNotNull { Condition.labels[it] } +
                    (if (profile.allergies.isNotEmpty()) listOf(L("dị ứng", "allergies")) else emptyList()) +
                    (if (profile.diet != Diet.NONE) listOf(Diet.labels[profile.diet] ?: "") else emptyList())
                if (filters.isNotEmpty()) Text(L("🛡️ Đã loại món không hợp: ${filters.joinToString()}", "🛡️ Excluded unsuitable dishes: ${filters.joinToString()}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        items(plan, key = { it.meal }) { meal ->
            SectionCard(
                title = "${Meal.emoji[meal.meal]} ${Meal.labels[meal.meal]} · ${meal.kcal} kcal",
                trailing = {
                    if (meal.items.isNotEmpty()) FilledTonalButton(
                        onClick = { vm.addMeal(meal.items.map { it.food to it.qty }, meal.meal) },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) { Text(L("＋ Nhật ký", "＋ Log")) }
                },
            ) {
                if (meal.items.isEmpty()) {
                    Text(L("Chưa có món phù hợp với các giới hạn của bạn. Hãy hỏi chuyên gia AI để có gợi ý riêng.", "No dishes fit your restrictions yet. Ask the AI expert for personal suggestions."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Items are listed in the order they should be eaten: soup and vegetables, protein, starch, then fruit.
                val asEntries = meal.items.mapIndexed { i, it ->
                    LogEntry(i.toLong(), "", meal.meal, it.food.id, it.food.name, it.food.emoji, kcal = it.food.kcal, course = it.food.course, tags = it.food.tags, qty = it.qty, serving = it.food.serving)
                }
                val order = Nutrition.eatingOrder(asEntries, profile)
                order.forEachIndexed { i, step ->
                    val item = meal.items.first { it.food.name == step.name }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(20.dp))
                        FoodImage(item.food.emoji, item.food.imageUrl ?: FoodPhotos.url(item.food.id), 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${item.food.label} × ${AppViewModel.fmtQty(item.qty)}", fontWeight = FontWeight.Medium)
                            Text(step.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${(item.food.kcal * item.qty).toInt()}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }

        item {
            SectionCard(title = L("${Goal.emoji[goal]} Nguyên tắc cho ${Goal.labels[goal]?.lowercase()}", "${Goal.emoji[goal]} Rules for ${Goal.labels[goal]?.lowercase()}")) {
                Nutrition.goalGuides[goal].orEmpty().forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                FilledTonalButton(onClick = onCoach) { Text(L("✨ Nhờ AI lập thực đơn riêng cho tôi", "✨ Ask AI for a menu just for me")) }
            }
        }
    }
}
