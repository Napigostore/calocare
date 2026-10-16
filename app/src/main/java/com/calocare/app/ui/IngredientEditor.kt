package com.calocare.app.ui

import com.calocare.app.L
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calocare.app.AppViewModel
import com.calocare.app.net.Ingredient
import kotlin.math.roundToInt

/** What a dish adds up to once its ingredients and their weights are settled. */
class DishTotals(val kcal: Int, val protein: Float, val carbs: Float, val fat: Float, val portion: String)

private fun weight(grams: Int): String = if (grams >= 100 && grams % 50 == 0) L("${grams}g (${AppViewModel.fmtQty(grams / 100f)} lạng)", "${grams}g") else "${grams}g"

/**
 * Lets someone who knows roughly what went into a dish — "about 3 lạng of duck, some bamboo shoots" —
 * get its calories without knowing any nutrition: the AI proposes the ingredients with their weights,
 * the user only moves the weights, and energy and macros follow.
 *
 * [description] is what the AI starts from: the dish name, with the amount if known.
 */
@Composable
fun IngredientEditor(vm: AppViewModel, title: String, description: String, onApply: (DishTotals) -> Unit, onDismiss: () -> Unit) {
    val rows = remember { mutableStateListOf<Ingredient>() }
    var loading by remember { mutableStateOf(true) }
    var extra by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(description) {
        vm.breakdown(description)?.let { rows.addAll(it) }
        loading = false
    }

    val kcal = rows.sumOf { it.kcal.toDouble() }.roundToInt()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title.ifBlank { L("Thành phần món ăn", "Ingredients") }) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    L("Chỉ cần chỉnh số gam từng thành phần cho giống phần bạn ăn. Calo tự tính lại.", "Just adjust the grams of each ingredient to match your portion. Calories update automatically."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (loading) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(L("  AI đang tách món thành các thành phần…", "  The AI is splitting the dish into ingredients…"))
                }
                if (!loading && rows.isEmpty()) Text(L("Chưa có thành phần nào. Thêm ở ô bên dưới, ví dụ “thịt vịt 300g”.", "No ingredients yet. Add one below, e.g. “chicken breast 200g”."), color = MaterialTheme.colorScheme.onSurfaceVariant)

                rows.forEachIndexed { i, row ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(row.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("${row.kcal.roundToInt()} kcal", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { rows.removeAt(i) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, L("Bỏ ${row.name}", "Remove ${row.name}"), Modifier.size(18.dp)) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Small amounts (herbs, oil) move in 10 g steps, the rest in half-lạng steps.
                            val step = if (row.grams <= 50) 10 else 50
                            FilledTonalIconButton(onClick = { rows[i] = row.copy(grams = (row.grams - step).coerceAtLeast(5)) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Remove, L("Bớt", "Less"), Modifier.size(18.dp)) }
                            Text(weight(row.grams), textAlign = TextAlign.Center, fontWeight = FontWeight.Medium, modifier = Modifier.width(150.dp))
                            FilledTonalIconButton(onClick = { rows[i] = row.copy(grams = (row.grams + (if (row.grams < 50) 10 else 50)).coerceAtMost(3000)) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Add, L("Thêm", "More"), Modifier.size(18.dp)) }
                        }
                    }
                }

                if (rows.isNotEmpty()) {
                    HorizontalDivider()
                    Text(L("Tổng: $kcal kcal · ${rows.sumOf { it.grams }}g", "Total: $kcal kcal · ${rows.sumOf { it.grams }}g"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        L("Đạm", "Protein") + " ${rows.sumOf { it.protein.toDouble() }.roundToInt()}g · " + L("Tinh bột", "Carbs") + " ${rows.sumOf { it.carbs.toDouble() }.roundToInt()}g · " +
                            L("Béo", "Fat") + " ${rows.sumOf { it.fat.toDouble() }.roundToInt()}g",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    extra, { extra = it }, label = { Text(L("Thêm thành phần", "Add ingredient")) }, placeholder = { Text(L("măng 2 lạng", "rice 150g")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { MicButton(onText = { extra = it }, onUnavailable = { vm.messages.tryEmit(L("Máy chưa có nhận dạng giọng nói.", "Speech recognition isn't available on this phone.")) }) },
                )
                FilledTonalButton(enabled = extra.isNotBlank() && !adding, onClick = { adding = true }) {
                    if (adding) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(if (adding) L("  Đang tính…", "  Working…") else L("＋ Thêm", "＋ Add"))
                }
                if (adding) LaunchedEffect(Unit) {
                    vm.breakdown(extra)?.let { rows.addAll(it); extra = "" }
                    adding = false
                }
            }
        },
        confirmButton = {
            Button(enabled = rows.isNotEmpty() && kcal > 0, onClick = {
                onApply(
                    DishTotals(
                        kcal, rows.sumOf { it.protein.toDouble() }.toFloat(), rows.sumOf { it.carbs.toDouble() }.toFloat(), rows.sumOf { it.fat.toDouble() }.toFloat(),
                        // The portion text records what the numbers are for, so the entry explains itself later.
                        rows.joinToString { "${it.name} ${it.grams}g" },
                    )
                )
            }) { Text(L("Dùng $kcal kcal", "Use $kcal kcal")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Hủy", "Cancel")) } },
    )
}
