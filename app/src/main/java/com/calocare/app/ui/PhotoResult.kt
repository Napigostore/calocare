package com.calocare.app.ui

import com.calocare.app.servingLabel
import com.calocare.app.label
import com.calocare.app.L
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calocare.app.AppViewModel
import com.calocare.app.PhotoItem
import com.calocare.app.PhotoState
import com.calocare.app.data.AppData
import com.calocare.app.data.FoodMatcher
import com.calocare.app.data.LogEntry
import com.calocare.app.data.Meal
import com.calocare.app.data.Nutrition
import kotlin.math.roundToInt

/** Mic button: the phone's own speech recognition, in Vietnamese. The spoken text is handed to [onText]. */
@Composable
fun MicButton(onText: (String) -> Unit, onUnavailable: () -> Unit, modifier: Modifier = Modifier) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onText)
        }
    }
    IconButton(
        modifier = modifier,
        onClick = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (com.calocare.app.Lang.en) java.util.Locale.getDefault().toLanguageTag() else "vi-VN")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, L("Nói tên món hoặc mô tả món ăn", "Say the food's name or describe it"))
            // Not every phone has a speech recogniser installed.
            runCatching { launcher.launch(intent) }.onFailure { onUnavailable() }
        },
    ) { Icon(Icons.Default.Mic, L("Nói tên món", "Speak"), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp)) }
}

/** Result of the meal photo analysis: the user checks dishes, fixes names, sizes and amounts, then saves to the diary. */
@Composable
fun PhotoDialog(vm: AppViewModel, data: AppData, state: PhotoState) {
    // The dish being corrected; Unit-like marker `adding` opens the same dialog for a dish the AI missed.
    var editing by remember { mutableStateOf<PhotoItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    val picked = state.items.filter { it.checked }

    FullScreenDialog(L("📷 Bữa ăn của bạn", "📷 Your meal"), onClose = { vm.photo = null }) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Image(
                    state.bitmap.asImageBitmap(), contentDescription = L("Ảnh bữa ăn", "Meal photo"), contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp)),
                )
            }
            if (state.loading) item {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text(L("   AI đang nhận diện món ăn…", "   The AI is identifying the food…"))
                }
            }
            state.error?.let { item { Text(it, color = Palette.Danger) } }

            if (picked.isNotEmpty()) item { MealSummary(state, picked) }

            val dangers = picked.flatMap { p -> Nutrition.warnings(p.item.tags.toSet(), data.profile).filter { it.danger }.map { p.item.name to it } }
            if (dangers.isNotEmpty()) item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(14.dp)) {
                    Text(L("⛔ Cảnh báo sức khỏe", "⛔ Health warning"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    dangers.forEach { (name, w) -> Text("$name: ${w.text}", color = MaterialTheme.colorScheme.onErrorContainer) }
                }
            }

            items(state.items) { p -> DishCard(vm, data, p, onEdit = { editing = p }) }

            if (!state.loading) item {
                // Always there: the AI can miss a dish, or recognise nothing at all.
                FilledTonalButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(28.dp))
                    Text(if (state.items.isEmpty()) L("  Tự thêm món (gõ hoặc nói)", "  Add items (type or speak)") else L("  Thêm món AI bỏ sót", "  Add an item the AI missed"), style = MaterialTheme.typography.titleMedium)
                }
            }
            if (state.advice.isNotBlank()) item { Text("🩺 ${state.advice}", style = MaterialTheme.typography.bodyMedium) }
            if (picked.size >= 2) item {
                EatingOrder(
                    picked.mapIndexed { i, p -> LogEntry(i.toLong(), "", vm.meal, "", p.item.name, p.item.emoji, kcal = p.item.kcal, course = p.item.course, tags = p.item.tags.toSet(), qty = p.amount) },
                    data,
                )
            }
        }
        if (!state.loading && state.items.isNotEmpty()) {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Meal.all.forEach { m -> FilterChip(vm.meal == m, { vm.meal = m }, label = { Text(Meal.labels[m] ?: m) }) }
                    }
                    val total = picked.sumOf { it.kcal }
                    Button(onClick = { vm.commitPhoto() }, enabled = total > 0, modifier = Modifier.fillMaxWidth()) {
                        Text(L("Thêm vào nhật ký · $total kcal", "Add to log · $total kcal"))
                    }
                }
            }
        }
    }
    if (adding || editing != null) DishDialog(vm, data, editing) { adding = false; editing = null }
}

/** The whole tray at a glance: what it is, total energy, a health grade, the nutrients, and what is good or bad about it. */
@Composable
private fun MealSummary(state: PhotoState, picked: List<PhotoItem>) {
    fun total(of: (PhotoItem) -> Float) = picked.sumOf { (of(it) * it.amount).toDouble() }.roundToInt()
    SectionCard {
        if (state.title.isNotBlank()) Text(state.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${picked.sumOf { it.kcal }}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(" kcal", modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(L("${picked.size} món", "${picked.size} item(s)"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.score in 0..100) {
                val (letter, label, color) = when {
                    state.score >= 80 -> Triple("A", L("Rất tốt", "Very good"), Palette.In)
                    state.score >= 65 -> Triple("B", L("Tốt", "Good"), Color(0xFF7CB342))
                    state.score >= 50 -> Triple("C", L("Trung bình", "Average"), Palette.Carbs)
                    state.score >= 35 -> Triple("D", L("Chưa tốt", "Poor"), Palette.Out)
                    else -> Triple("E", L("Kém", "Bad"), Palette.Danger)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                        Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                    }
                    Text("$label · ${state.score}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Row {
            Nutrient(L("Đạm", "Protein"), "${total { it.item.protein }}g", Modifier.weight(1f))
            Nutrient(L("Tinh bột", "Carbs"), "${total { it.item.carbs }}g", Modifier.weight(1f))
            Nutrient(L("Chất béo", "Fat"), "${total { it.item.fat }}g", Modifier.weight(1f))
        }
        // Fibre, sugar and sodium are the AI's estimate even for dishes whose calories come from the app's table.
        if (picked.any { it.item.fiber > 0f || it.item.sugar > 0f || it.item.sodium > 0f }) Row {
            Nutrient(L("Chất xơ", "Fibre"), "${total { it.item.fiber }}g", Modifier.weight(1f))
            Nutrient(L("Đường", "Sugar"), "${total { it.item.sugar }}g", Modifier.weight(1f))
            Nutrient("Natri", "${total { it.item.sodium }}mg", Modifier.weight(1f))
        }
        state.pros.forEach { Text("＋ $it", style = MaterialTheme.typography.bodySmall, color = Palette.In) }
        state.cons.forEach { Text("－ $it", style = MaterialTheme.typography.bodySmall, color = Palette.Warn) }
    }
}

@Composable
private fun Nutrient(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DishCard(vm: AppViewModel, data: AppData, p: PhotoItem, onEdit: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(p.checked, { p.checked = it })
            Text(p.item.emoji, fontSize = 28.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
                Text(p.item.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${p.item.kcal} kcal / ${p.item.portion}" + if (p.item.grams > 0) L(" · trong ảnh ≈ ${p.item.grams}g", " · in photo ≈ ${p.item.grams}g") else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (p.fromTable) L("✓ Calo theo bảng món ăn của app", "✓ Calories from the app's food table") else L("≈ AI ước tính · chắc ${(p.item.confidence.coerceIn(0f, 1f) * 100).roundToInt()}%", "≈ AI estimate · ${(p.item.confidence.coerceIn(0f, 1f) * 100).roundToInt()}% sure"),
                    style = MaterialTheme.typography.labelSmall, color = if (p.fromTable) MaterialTheme.colorScheme.primary else Palette.Warn,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${p.kcal} kcal", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                // The AI can mistake one dish for another: a clear way to say what it really is.
                FilledTonalIconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Edit, L("Sửa món", "Edit item"), Modifier.size(22.dp)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(L("Cỡ", "Size"), style = MaterialTheme.typography.bodySmall)
            // A large egg is not a small egg: the size scales the calories of one serving.
            PhotoItem.SIZES.forEach { (id, label) -> FilterChip(p.size == id, { p.size = id }, enabled = p.checked, label = { Text(label) }) }
            Spacer(Modifier.weight(1f))
            FilledTonalIconButton(onClick = { p.qty = (p.qty - 0.5f).coerceAtLeast(0.5f) }, enabled = p.checked, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Remove, L("Bớt nửa phần", "Half a serving less"), Modifier.size(18.dp)) }
            Text("× ${AppViewModel.fmtQty(p.qty)}", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
            FilledTonalIconButton(onClick = { p.qty = (p.qty + 0.5f).coerceAtMost(20f) }, enabled = p.checked, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Add, L("Thêm nửa phần", "Half a serving more"), Modifier.size(18.dp)) }
        }
        if (p.learned) Text(L("Số lượng theo thói quen của bạn", "Amount based on your habits"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (p.alternatives.isNotEmpty()) {
            // One tap instead of typing when the AI picked the wrong look-alike.
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(L("Hay là:", "Or maybe:"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                p.alternatives.forEach { name -> AssistChip(onClick = { vm.pickAlternative(p, name) }, label = { Text(name) }) }
            }
        }
        Nutrition.warnings(p.item.tags.toSet(), data.profile).forEach { WarningLine(it) }
    }
}

/**
 * Says what a dish is, by typing or by voice. With [target] it corrects that dish; without, it adds one
 * the AI missed. Three ways in: pick a food the app knows, describe the dish and let the AI work out
 * the calories (a name, a list of ingredients with amounts, or "tương đương 1 tô phở"), or just rename.
 */
@Composable
private fun DishDialog(vm: AppViewModel, data: AppData, target: PhotoItem?, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(target?.item?.name ?: "") }
    var ingredients by remember { mutableStateOf(false) }
    val foods = remember(data.customFoods) { vm.foods(data) }
    val matches = remember(text, foods) { FoodMatcher.ranked(text, foods).take(5).map { it.first } }
    AlertDialog(
        onDismissRequest = { if (!vm.describing) onDismiss() },
        title = { Text(if (target == null) L("Thêm món", "Add item") else L("Đây là món gì?", "What is this?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    text, { text = it }, label = { Text(L("Tên món hoặc mô tả", "Name or description")) }, maxLines = 4, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { MicButton(onText = { text = it }, onUnavailable = { vm.messages.tryEmit(L("Máy chưa có nhận dạng giọng nói.", "Speech recognition isn't available on this phone.")) }) },
                )
                Text(
                    L("Ví dụ: “bánh khọt 5 cái” · “hủ tiếu: thịt 100g, rau một ít, bún 100g, nước xương 400ml” · “tương đương 1 tô phở”", "E.g. “5 chicken wings” · “salad: chicken 100g, avocado half, dressing 2 tbsp” · “about the size of a burger”"),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                matches.forEach { food ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !vm.describing) { if (target != null) vm.correctPhotoItem(target, food) else vm.addPhotoFood(food); onDismiss() }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FoodImage(food.emoji, food.imageUrl, 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(food.label, fontWeight = FontWeight.Medium)
                            Text("${food.kcal} kcal / ${food.servingLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (target != null) {
                    // For "it is this dish, but not this much of it": adjust what went in, by weight.
                    FilledTonalButton(onClick = { ingredients = true }, enabled = !vm.describing && vm.hasAi, modifier = Modifier.fillMaxWidth()) {
                        Text(L("🧮 Chỉnh theo thành phần (thịt, rau… bao nhiêu gam)", "🧮 Adjust by ingredients (grams of meat, veg…)"))
                    }
                }
                Button(
                    onClick = { vm.describeDish(text, target, onDismiss) }, enabled = text.isNotBlank() && !vm.describing && vm.hasAi,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (vm.describing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Text(if (vm.describing) L("  Đang tính…", "  Working…") else L("✨ AI tính calo theo mô tả này", "✨ AI calories from this description"))
                }
            }
        },
        confirmButton = {
            if (target != null) TextButton(enabled = text.isNotBlank() && !vm.describing, onClick = { vm.renamePhotoItem(target, text); onDismiss() }) { Text(L("Chỉ đổi tên", "Rename only")) }
        },
        dismissButton = { TextButton(enabled = !vm.describing, onClick = onDismiss) { Text(L("Hủy", "Cancel")) } },
    )
    if (ingredients && target != null) {
        IngredientEditor(
            vm, target.item.name, "${target.item.name}: ${AppViewModel.fmtQty(target.amount)} × ${target.item.portion}",
            onApply = { vm.applyIngredients(target, it); ingredients = false; onDismiss() }, onDismiss = { ingredients = false },
        )
    }
}
