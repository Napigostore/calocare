package com.calocare.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.calocare.app.AppViewModel
import com.calocare.app.data.AppData
import com.calocare.app.data.Course
import com.calocare.app.data.Food
import com.calocare.app.data.FoodDb
import com.calocare.app.data.FoodMatcher
import com.calocare.app.data.LogEntry
import com.calocare.app.data.Meal
import com.calocare.app.data.Nutrition
import com.calocare.app.data.Region
import com.calocare.app.data.Tag
import com.calocare.app.data.entriesOn
import com.calocare.app.data.totals
import java.text.Normalizer
import kotlin.math.roundToInt

/** Lower-case and strip Vietnamese diacritics so "pho" finds "Phở". */
private fun fold(s: String) = Normalizer.normalize(s.lowercase().replace('đ', 'd'), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

private val categories = listOf(
    "all" to "Tất cả", "main" to "Món chính", "protein" to "Đạm", "veg" to "Rau & canh",
    "fruit" to "Trái cây", "drink" to "Đồ uống", "snack" to "Ăn vặt", "mine" to "Của tôi",
)

private fun inCategory(food: Food, category: String) = when (category) {
    "all" -> true
    "main" -> food.course == Course.MAIN
    "protein" -> food.course == Course.PROTEIN
    "veg" -> food.course == Course.VEG || food.course == Course.SOUP
    "fruit" -> food.course == Course.FRUIT
    "drink" -> food.course == Course.DRINK
    "snack" -> food.course == Course.SNACK || food.course == Course.DESSERT
    else -> food.custom
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoodScreen(vm: AppViewModel, data: AppData, padding: PaddingValues, onNeedAiKey: () -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("all") }
    var detail by remember { mutableStateOf<Food?>(null) }
    var editing by remember { mutableStateOf<Food?>(null) }
    var camera by remember { mutableStateOf(false) }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) camera = true else vm.messages.tryEmit("Cần quyền camera để chụp bữa ăn. Bạn vẫn có thể chọn ảnh từ thư viện.")
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.analyzePhoto(uri)
    }

    val profile = data.profile
    val todays = data.entriesOn(vm.date)
    val mealEntries = todays.filter { it.meal == vm.meal }
    val counts = mealEntries.groupBy { it.foodId }.mapValues { (_, list) -> list.sumOf { it.qty.toDouble() }.toFloat() }

    // Local dishes first, then the ones shared by every region, then the rest.
    val allFoods = remember(data.customFoods, profile.region, vm.library) {
        val quick = FoodDb.quickIds.withIndex().associate { it.value to it.index }
        val edited = data.customFoods.map { it.id }.toSet()
        data.customFoods + vm.library.filterNot { it.id in edited }.sortedWith(
            compareBy<Food> { quick[it.id] ?: Int.MAX_VALUE }.thenBy { if (it.region == profile.region) 0 else if (it.region == Region.ALL) 1 else 2 }
        )
    }
    val searching = query.isNotBlank()
    val shown = if (searching) fold(query).let { q -> allFoods.filter { fold(it.name).contains(q) } } else allFoods.filter { inCategory(it, category) }
    val frequent = if (searching) emptyList() else vm.frequentFoods(data, vm.meal)
    // Dishes that share words with the query without containing it: "bún bò" finds "Bún bò Huế" above, "Bò kho" here.
    val similar = if (!searching) emptyList() else shown.map { it.id }.toSet().let { found ->
        FoodMatcher.ranked(query, allFoods).filter { it.second >= 0.3f && it.first.id !in found }.take(6).map { it.first }
    }

    Column(Modifier.padding(top = padding.calculateTopPadding())) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Nhật ký ăn uống", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            DateSwitcher(vm)
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Meal.all.forEach { m ->
                val kcal = todays.filter { it.meal == m }.totals().kcal.roundToInt()
                FilterChip(
                    selected = vm.meal == m, onClick = { vm.meal = m },
                    label = { Text("${Meal.emoji[m]} ${Meal.labels[m]}" + if (kcal > 0) " $kcal" else "", maxLines = 1) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; vm.clearOnline() },
                placeholder = { Text("Tìm hoặc nói tên món…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searching) IconButton(onClick = { query = ""; vm.clearOnline() }) { Icon(Icons.Default.Close, "Xóa") }
                        MicButton(onText = { query = it; vm.clearOnline() }, onUnavailable = { vm.messages.tryEmit("Máy chưa có nhận dạng giọng nói.") })
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.searchOnline(query) }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = {
                when {
                    vm.hasPhotoAi ->
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camera = true
                        else cameraPermission.launch(Manifest.permission.CAMERA)
                    vm.hasAi -> vm.messages.tryEmit("DeepSeek chưa đọc được ảnh. Chụp ảnh bữa ăn cần API key của Anthropic.")
                    else -> onNeedAiKey()
                }
            }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.CameraAlt, "Chụp ảnh bữa ăn")
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            fun tiles(foods: List<Food>, keyPrefix: String) = items(foods, key = { keyPrefix + it.id }) { food ->
                FoodTile(
                    food = food, count = counts[food.id] ?: 0f,
                    flagged = Nutrition.warnings(food.tags, profile).let { w -> if (w.any { it.danger }) 2 else if (w.isNotEmpty()) 1 else 0 },
                    onTap = { vm.tapFood(food) }, onLongPress = { detail = food },
                )
            }

            if (!searching) {
                if (mealEntries.isNotEmpty()) wide("log") { MealLog(vm, data, mealEntries) }
                if (frequent.isNotEmpty()) {
                    wide("freq_title") { Text("⭐ Món quen của bạn", fontWeight = FontWeight.SemiBold) }
                    tiles(frequent, "freq_")
                }
                wide("cats") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { (id, label) ->
                            FilterChip(selected = category == id, onClick = { category = id }, label = { Text(label) })
                        }
                    }
                }
                wide("hint") {
                    Text("Đang chọn cho bữa ${Meal.labels[vm.meal]?.lowercase()} · chạm = +1 phần · giữ để sửa món", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            tiles(shown, "f_")

            if (!searching && category == "mine") {
                wide("new") { OutlinedButton(onClick = { editing = blankFood(vm, "") }) { Icon(Icons.Default.Add, null); Text(" Tạo món của tôi") } }
            }

            if (searching) {
                if (similar.isNotEmpty()) {
                    wide("similar_title") { Text("Món gần giống", fontWeight = FontWeight.SemiBold) }
                    tiles(similar, "sim_")
                }
                wide("new_dish") {
                    NewDishCard(
                        vm, query, known = shown.isNotEmpty(),
                        onCreate = { editing = blankFood(vm, query.trim()) }, onEdit = { editing = it.copy(custom = true) }, onNeedAiKey = onNeedAiKey,
                    )
                }
                if (vm.onlineResults.isNotEmpty()) wide("online_title") { Text("🌐 Sản phẩm trên Open Food Facts", fontWeight = FontWeight.SemiBold) }
                tiles(vm.onlineResults, "online_")
            }
        }
    }

    detail?.let { food ->
        FoodDetailDialog(
            food = food, vm = vm, data = data, onDismiss = { detail = null },
            onEdit = { detail = null; editing = food.copy(custom = true) },
        )
    }
    editing?.let { food -> FoodEditDialog(vm, food, onDismiss = { editing = null }, onSave = { vm.saveCustomFood(it); editing = null }) }

    vm.pendingDanger?.let { (food, warnings) ->
        AlertDialog(
            onDismissRequest = { vm.pendingDanger = null },
            icon = { Text("⛔", fontSize = 32.sp) },
            title = { Text("Cảnh báo dị ứng") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${food.emoji} ${food.name}", fontWeight = FontWeight.SemiBold)
                    warnings.forEach { WarningLine(it) }
                }
            },
            confirmButton = { TextButton(onClick = { vm.addFood(food, 1f, warnings); vm.pendingDanger = null }) { Text("Vẫn thêm", color = Palette.Danger) } },
            dismissButton = { Button(onClick = { vm.pendingDanger = null }) { Text("Không thêm") } },
        )
    }

    if (camera) {
        MealCamera(
            onCaptured = { camera = false; vm.analyzeCapture(it) },
            onGallery = { camera = false; pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onClose = { camera = false },
            onError = { camera = false; vm.messages.tryEmit(it) },
        )
    }
}

private fun blankFood(vm: AppViewModel, name: String) =
    Food(id = vm.newCustomId(), name = name, kcal = 0, serving = "1 phần", region = Region.ALL, custom = true)

private fun LazyGridScope.wide(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

/**
 * For a dish the app does not have yet: the AI suggests its calories from the name (or a description
 * with ingredients), the user adds it as it is or adjusts it first. Online product search and manual
 * entry are the other two ways in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewDishCard(vm: AppViewModel, query: String, known: Boolean, onCreate: () -> Unit, onEdit: (Food) -> Unit, onNeedAiKey: () -> Unit) {
    SectionCard(title = if (known) "Không phải món bạn tìm?" else "Món mới: “${query.trim()}”") {
        Text(
            "AI gợi ý calo từ tên món. Bạn cũng có thể mô tả: “bún 100g, thịt 100g, rau một ít” hoặc “tương đương 1 tô phở”.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (vm.hasAi) vm.estimateWithAi(query) else onNeedAiKey() }, enabled = !vm.onlineBusy) { Text("✨ AI gợi ý calo") }
            FilledTonalButton(onClick = { vm.searchOnline(query) }, enabled = !vm.onlineBusy) { Text("🌐 Tìm sản phẩm") }
            OutlinedButton(onClick = onCreate) { Text("＋ Tự nhập") }
        }
        if (vm.onlineBusy) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("  Đang tính…")
        }
        vm.aiSuggestion?.let { food ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(food.emoji, fontSize = 30.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(food.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("${food.kcal} kcal / ${food.serving}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "Đạm ${food.protein.roundToInt()}g · Tinh bột ${food.carbs.roundToInt()}g · Béo ${food.fat.roundToInt()}g · AI ước tính",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Nutrition.warnings(food.tags, vm.data.value.profile).forEach { WarningLine(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.tapFood(food) }) { Text("＋ Thêm vào bữa ${Meal.labels[vm.meal]?.lowercase()}") }
                    OutlinedButton(onClick = { onEdit(food) }) { Text("✏️ Sửa rồi lưu") }
                }
            }
        }
        if (vm.onlineNote.isNotBlank()) Text(vm.onlineNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FoodTile(food: Food, count: Float, flagged: Int, onTap: () -> Unit, onLongPress: () -> Unit) {
    val selected = count > 0f
    Box {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                .combinedClickable(onClick = onTap, onLongClick = onLongPress)
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FoodImage(food.emoji, food.imageUrl, 56.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                food.name, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp,
            )
            Text("${food.kcal} kcal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(food.serving, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 7.dp, vertical = 2.dp),
            ) { Text("×${AppViewModel.fmtQty(count)}", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
        }
        if (flagged > 0) Text(if (flagged == 2) "⛔" else "⚠️", fontSize = 14.sp, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
    }
}

/** What is already logged for the selected meal, with quantity steppers and the suggested eating order. */
@Composable
private fun MealLog(vm: AppViewModel, data: AppData, entries: List<LogEntry>) {
    var showOrder by rememberSaveable { mutableStateOf(false) }
    val totals = entries.totals()
    SectionCard(
        title = "${Meal.emoji[vm.meal]} Bữa ${Meal.labels[vm.meal]?.lowercase()} · ${totals.kcal.roundToInt()} kcal",
        trailing = { if (entries.size >= 2) TextButton(onClick = { showOrder = !showOrder }) { Text(if (showOrder) "Ẩn thứ tự" else "Thứ tự ăn") } },
    ) {
        entries.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                FoodImage(e.emoji, e.imageUrl, 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text("${e.totalKcal.roundToInt()} kcal · ${e.serving}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Nutrition.warnings(e.tags, data.profile).firstOrNull()?.let { WarningLine(it) }
                }
                FilledTonalIconButton(onClick = { vm.changeQty(e.id, -0.5f) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Remove, "Bớt nửa phần", Modifier.size(18.dp)) }
                Text(AppViewModel.fmtQty(e.qty), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(36.dp))
                FilledTonalIconButton(onClick = { vm.changeQty(e.id, 0.5f) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Add, "Thêm nửa phần", Modifier.size(18.dp)) }
            }
        }
        Text(
            "Đạm ${totals.protein.roundToInt()}g · Tinh bột ${totals.carbs.roundToInt()}g · Béo ${totals.fat.roundToInt()}g",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showOrder && entries.size >= 2) EatingOrder(entries, data)
    }
}

@Composable
fun EatingOrder(entries: List<LogEntry>, data: AppData) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.tertiaryContainer).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("🍽️ Nên ăn theo thứ tự", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
        Nutrition.eatingOrder(entries, data.profile).forEachIndexed { i, step ->
            Text("${i + 1}. ${step.emoji} ${step.name}", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(step.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(start = 18.dp))
        }
    }
}

@Composable
private fun FoodDetailDialog(food: Food, vm: AppViewModel, data: AppData, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val warnings = Nutrition.warnings(food.tags, data.profile)
    val mine = food.custom && data.customFoods.any { it.id == food.id }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FoodImage(food.emoji, food.imageUrl, 48.dp)
                Spacer(Modifier.width(12.dp))
                Text(food.name, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${food.kcal} kcal / ${food.serving}", fontWeight = FontWeight.SemiBold)
                Text("Đạm ${food.protein.roundToInt()}g · Tinh bột ${food.carbs.roundToInt()}g · Béo ${food.fat.roundToInt()}g")
                Text("Nhóm: ${Course.labels[food.course] ?: food.course}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val allergens = food.tags.mapNotNull { Tag.allergens[it] }
                if (allergens.isNotEmpty()) Text("Chất gây dị ứng: ${allergens.joinToString()}", style = MaterialTheme.typography.bodySmall)
                warnings.forEach { WarningLine(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.5f, 1f, 2f).forEach { q ->
                        FilledTonalButton(onClick = { vm.addFood(food, q, warnings); onDismiss() }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                            Text("+${AppViewModel.fmtQty(q)} phần")
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onEdit) { Text("✏️ Sửa thông tin") } },
        dismissButton = {
            Row {
                // Deleting an edited built-in food brings the original values back.
                if (mine) TextButton(onClick = { vm.deleteCustomFood(food.id); onDismiss() }) {
                    Text(if (vm.library.any { it.id == food.id }) "Khôi phục gốc" else "Xóa", color = Palette.Danger)
                }
                TextButton(onClick = onDismiss) { Text("Đóng") }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoodEditDialog(vm: AppViewModel, initial: Food, onDismiss: () -> Unit, onSave: (Food) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var serving by remember { mutableStateOf(initial.serving) }
    var kcal by remember { mutableStateOf(if (initial.kcal > 0) initial.kcal.toString() else "") }
    var protein by remember { mutableStateOf(if (initial.protein > 0) initial.protein.roundToInt().toString() else "") }
    var carbs by remember { mutableStateOf(if (initial.carbs > 0) initial.carbs.roundToInt().toString() else "") }
    var fat by remember { mutableStateOf(if (initial.fat > 0) initial.fat.roundToInt().toString() else "") }
    var course by remember { mutableStateOf(initial.course) }
    var tags by remember { mutableStateOf(initial.tags) }
    var ingredients by remember { mutableStateOf(false) }
    val valid = name.isNotBlank() && (kcal.toIntOrNull() ?: 0) > 0

    // Energy is 4 kcal per gram of protein and carbohydrate and 9 per gram of fat, so when a macro changes
    // the calories are recalculated rather than left for the user to reconcile. Alcohol adds energy of its own.
    fun follow(p: String, c: String, f: String) {
        val total = (4f * (p.toFloatOrNull() ?: 0f) + 4f * (c.toFloatOrNull() ?: 0f) + 9f * (f.toFloatOrNull() ?: 0f)).roundToInt()
        if (total > 0 && Tag.ALCOHOL !in tags) kcal = total.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sửa thông tin món") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(emoji, { emoji = it.take(4) }, label = { Text("Biểu tượng") }, singleLine = true, modifier = Modifier.width(96.dp))
                    OutlinedTextField(name, { name = it }, label = { Text("Tên món") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(serving, { serving = it }, label = { Text("Khẩu phần") }, singleLine = true, modifier = Modifier.weight(1f))
                    NumberField(kcal, { kcal = it }, "kcal", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(protein, { protein = it; follow(it, carbs, fat) }, "Đạm g", Modifier.weight(1f))
                    NumberField(carbs, { carbs = it; follow(protein, it, fat) }, "Bột g", Modifier.weight(1f))
                    NumberField(fat, { fat = it; follow(protein, carbs, it) }, "Béo g", Modifier.weight(1f))
                }
                Text("Đổi đạm, bột, béo thì calo tự tính lại. Không rành số liệu? Dùng nút dưới đây.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = { ingredients = true }, enabled = name.isNotBlank() && vm.hasAi, modifier = Modifier.fillMaxWidth()) {
                    Text("🧮 Tính theo thành phần (thịt, rau… bao nhiêu gam)")
                }
                Text("Nhóm món (để gợi ý thứ tự ăn)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Course.all.forEach { c -> FilterChip(course == c, { course = c }, label = { Text(Course.labels[c] ?: c) }) }
                }
                Text("Món này có chứa (để cảnh báo sức khỏe)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val options = Tag.allergens.map { it.key to it.value.substringBefore(" (").substringBefore(" /") } + listOf(
                        Tag.HIGH_SUGAR to "Nhiều đường", Tag.HIGH_SODIUM to "Nhiều muối", Tag.HIGH_CARB to "Nhiều tinh bột",
                        Tag.FRIED to "Chiên rán", Tag.HIGH_PURINE to "Nhiều purin", Tag.MEAT to "Thịt", Tag.PORK to "Thịt heo", Tag.ALCOHOL to "Rượu bia",
                    )
                    options.forEach { (tag, label) ->
                        FilterChip(tag in tags, { tags = if (tag in tags) tags - tag else tags + tag }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = {
                onSave(
                    initial.copy(
                        name = name.trim(), emoji = emoji.ifBlank { "🍽️" }, serving = serving.ifBlank { "1 phần" },
                        kcal = kcal.toIntOrNull() ?: 0, protein = protein.toFloatOrNull() ?: 0f, carbs = carbs.toFloatOrNull() ?: 0f,
                        fat = fat.toFloatOrNull() ?: 0f, course = course, tags = tags, custom = true,
                    )
                )
            }) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
    if (ingredients) {
        IngredientEditor(
            vm, name, "$name: $serving",
            onApply = {
                kcal = it.kcal.toString(); protein = it.protein.roundToInt().toString(); carbs = it.carbs.roundToInt().toString(); fat = it.fat.roundToInt().toString()
                serving = it.portion
                ingredients = false
            },
            onDismiss = { ingredients = false },
        )
    }
}

@Composable
fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, decimal: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.replace(',', '.').filter { it.isDigit() || (decimal && it == '.') }.take(6)) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}
