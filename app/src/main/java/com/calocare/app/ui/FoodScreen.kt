package com.calocare.app.ui

import com.calocare.app.data.FoodPhotos
import com.calocare.app.servingLabel
import com.calocare.app.label
import com.calocare.app.L
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.calocare.app.net.DetectedFood
import com.calocare.app.net.Substitution
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.text.Normalizer
import kotlin.math.roundToInt

/** Lower-case and strip Vietnamese diacritics so "pho" finds "Phở". */
private fun fold(s: String) = Normalizer.normalize(s.lowercase().replace('đ', 'd'), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

private val categories get() = listOf(
    "all" to L("Tất cả", "All"), "main" to L("Món chính", "Mains"), "protein" to L("Đạm", "Protein"), "veg" to L("Rau & canh", "Veg & soups"),
    "fruit" to L("Trái cây", "Fruit"), "drink" to L("Đồ uống", "Drinks"), "snack" to L("Ăn vặt", "Snacks"), "mine" to L("Của tôi", "Mine"),
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
        if (granted) camera = true else vm.messages.tryEmit(L("Cần quyền camera để chụp bữa ăn. Bạn vẫn có thể chọn ảnh từ thư viện.", "Camera permission is needed to photograph meals. You can still choose a photo from the gallery."))
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
        val quick = FoodDb.quickIds(profile.region).withIndex().associate { it.value to it.index }
        val edited = data.customFoods.map { it.id }.toSet()
        data.customFoods + vm.library.filterNot { it.id in edited }.sortedWith(
            compareBy<Food> { quick[it.id] ?: Int.MAX_VALUE }.thenBy { if (it.region == profile.region) 0 else if (it.region == Region.ALL) 1 else 2 }
        )
    }
    val searching = query.isNotBlank()
    val shown = if (searching) fold(query).let { q -> allFoods.filter { fold(it.name).contains(q) || fold(it.label).contains(q) } } else allFoods.filter { inCategory(it, category) }
    val frequent = if (searching) emptyList() else vm.frequentFoods(data, vm.meal)
    // Dishes that share words with the query without containing it: "bún bò" finds "Bún bò Huế" above, "Bò kho" here.
    val similar = if (!searching) emptyList() else shown.map { it.id }.toSet().let { found ->
        FoodMatcher.ranked(query, allFoods).filter { it.second >= 0.3f && it.first.id !in found }.take(6).map { it.first }
    }

    Column(Modifier.padding(top = padding.calculateTopPadding())) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(L("Ăn uống", "Food"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            DateSwitcher(vm)
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Meal.all.forEach { m ->
                val kcal = todays.filter { it.meal == m }.totals().kcal.roundToInt()
                // Four equal tabs with the name always readable; the meal's calories sit underneath.
                val selected = vm.meal == m
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .clickable { vm.meal = m }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    Text(Meal.labels[m] ?: m, color = tint, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge)
                    Text(if (kcal > 0) "$kcal kcal" else "—", color = tint.copy(alpha = 0.75f), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; vm.clearOnline() },
                placeholder = { Text(L("Tìm món…", "Search foods…"), maxLines = 1) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searching) IconButton(onClick = { query = ""; vm.clearOnline() }) { Icon(Icons.Default.Close, L("Xóa", "Clear")) }
                        MicButton(onText = { query = it; vm.clearOnline() }, onUnavailable = { vm.messages.tryEmit(L("Máy chưa có nhận dạng giọng nói.", "Speech recognition isn't available on this phone.")) })
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
                    vm.hasAi -> vm.messages.tryEmit(L("DeepSeek chưa đọc được ảnh. Chụp ảnh bữa ăn cần API key của Anthropic.", "DeepSeek can't read photos. Meal photos need an Anthropic API key."))
                    else -> onNeedAiKey()
                }
            }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.CameraAlt, L("Chụp ảnh bữa ăn", "Photograph a meal"))
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
                    wide("freq_title") { Text(L("⭐ Món quen của bạn", "⭐ Your usual foods"), fontWeight = FontWeight.SemiBold) }
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
                    Text(L("Đang chọn cho bữa ${Meal.labels[vm.meal]?.lowercase()} · chạm = +1 phần · giữ để sửa món", "Adding to ${Meal.labels[vm.meal]?.lowercase()} · tap = +1 serving · hold to edit"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            tiles(shown, "f_")

            if (!searching && category == "mine") {
                wide("new") { OutlinedButton(onClick = { editing = blankFood(vm, "") }) { Icon(Icons.Default.Add, null); Text(L(" Tạo món của tôi", " Create my food")) } }
            }

            if (searching) {
                if (similar.isNotEmpty()) {
                    wide("similar_title") { Text(L("Món gần giống", "Similar foods"), fontWeight = FontWeight.SemiBold) }
                    tiles(similar, "sim_")
                }
                wide("new_dish") {
                    NewDishCard(
                        vm, query, known = shown.isNotEmpty(),
                        onCreate = { editing = blankFood(vm, query.trim()) }, onEdit = { editing = it.copy(custom = true) }, onNeedAiKey = onNeedAiKey,
                    )
                }
                if (vm.onlineResults.isNotEmpty()) wide("online_title") { Text(L("🌐 Sản phẩm trên Open Food Facts", "🌐 Products on Open Food Facts"), fontWeight = FontWeight.SemiBold) }
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
            title = { Text(L("Cảnh báo dị ứng", "Allergy warning")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${food.emoji} ${food.label}", fontWeight = FontWeight.SemiBold)
                    warnings.forEach { WarningLine(it) }
                }
            },
            confirmButton = { TextButton(onClick = { vm.addFood(food, 1f, warnings); vm.pendingDanger = null }) { Text(L("Vẫn thêm", "Add anyway"), color = Palette.Danger) } },
            dismissButton = { Button(onClick = { vm.pendingDanger = null }) { Text(L("Không thêm", "Don't add")) } },
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
    Food(id = vm.newCustomId(), name = name, kcal = 0, serving = L("1 phần", "1 serving"), region = Region.ALL, custom = true)

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
    SectionCard(title = if (known) L("Không phải món bạn tìm?", "Not what you're looking for?") else L("Món mới: “${query.trim()}”", "New food: “${query.trim()}”")) {
        Text(
            L("AI gợi ý calo từ tên món. Bạn cũng có thể mô tả: “bún 100g, thịt 100g, rau một ít” hoặc “tương đương 1 tô phở”.", "The AI estimates calories from the name. You can also describe it: “pasta 100g, chicken 100g, some veg” or “about the size of a burger”."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (vm.hasAi) vm.estimateWithAi(query) else onNeedAiKey() }, enabled = !vm.onlineBusy) { Text(L("✨ AI gợi ý calo", "✨ AI calorie estimate")) }
            FilledTonalButton(onClick = { vm.searchOnline(query) }, enabled = !vm.onlineBusy) { Text(L("🌐 Tìm sản phẩm", "🌐 Find product")) }
            OutlinedButton(onClick = onCreate) { Text(L("＋ Tự nhập", "＋ Enter manually")) }
        }
        if (vm.onlineBusy) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(L("  Đang tính…", "  Working…"))
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
                        Text(food.label, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("${food.kcal} kcal / ${food.servingLabel}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            L("Đạm ${food.protein.roundToInt()}g · Tinh bột ${food.carbs.roundToInt()}g · Béo ${food.fat.roundToInt()}g · AI ước tính", "Protein ${food.protein.roundToInt()}g · Carbs ${food.carbs.roundToInt()}g · Fat ${food.fat.roundToInt()}g · AI estimate"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Nutrition.warnings(food.tags, vm.data.value.profile).forEach { WarningLine(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.tapFood(food) }) { Text(L("＋ Thêm vào bữa ${Meal.labels[vm.meal]?.lowercase()}", "＋ Add to ${Meal.labels[vm.meal]?.lowercase()}")) }
                    OutlinedButton(onClick = { onEdit(food) }) { Text(L("✏️ Sửa rồi lưu", "✏️ Edit, then save")) }
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
            FoodImage(food.emoji, food.imageUrl ?: FoodPhotos.url(food.id), 56.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                food.label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp,
            )
            Text("${food.kcal} kcal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(food.servingLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        title = L("${Meal.emoji[vm.meal]} Bữa ${Meal.labels[vm.meal]?.lowercase()} · ${totals.kcal.roundToInt()} kcal", "${Meal.emoji[vm.meal]} ${Meal.labels[vm.meal]} · ${totals.kcal.roundToInt()} kcal"),
        trailing = { if (entries.size >= 2) TextButton(onClick = { showOrder = !showOrder }) { Text(if (showOrder) L("Ẩn thứ tự", "Hide order") else L("Thứ tự ăn", "Eating order")) } },
    ) {
        entries.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                FoodImage(e.emoji, e.imageUrl ?: FoodPhotos.url(e.foodId), 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text("${e.totalKcal.roundToInt()} kcal · ${e.servingLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Nutrition.warnings(e.tags, data.profile).firstOrNull()?.let { WarningLine(it) }
                }
                FilledTonalIconButton(onClick = { vm.changeQty(e.id, -0.5f) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Remove, L("Bớt nửa phần", "Half a serving less"), Modifier.size(18.dp)) }
                Text(AppViewModel.fmtQty(e.qty), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(36.dp))
                FilledTonalIconButton(onClick = { vm.changeQty(e.id, 0.5f) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Add, L("Thêm nửa phần", "Half a serving more"), Modifier.size(18.dp)) }
            }
        }
        Text(
            L("Đạm ${totals.protein.roundToInt()}g · Tinh bột ${totals.carbs.roundToInt()}g · Béo ${totals.fat.roundToInt()}g", "Protein ${totals.protein.roundToInt()}g · Carbs ${totals.carbs.roundToInt()}g · Fat ${totals.fat.roundToInt()}g"),
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
        Text(L("🍽️ Nên ăn theo thứ tự", "🍽️ Best eaten in this order"), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
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
    var substitution by remember { mutableStateOf<Substitution?>(null) }
    var loadingSubstitution by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FoodImage(food.emoji, food.imageUrl ?: FoodPhotos.url(food.id), 48.dp)
                Spacer(Modifier.width(12.dp))
                Text(food.label, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${food.kcal} kcal / ${food.servingLabel}", fontWeight = FontWeight.SemiBold)
                Text(L("Đạm ${food.protein.roundToInt()}g · Tinh bột ${food.carbs.roundToInt()}g · Béo ${food.fat.roundToInt()}g", "Protein ${food.protein.roundToInt()}g · Carbs ${food.carbs.roundToInt()}g · Fat ${food.fat.roundToInt()}g"))
                Text(L("Nhóm: ${Course.labels[food.course] ?: food.course}", "Group: ${Course.labels[food.course] ?: food.course}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val allergens = food.tags.mapNotNull { Tag.allergens[it] }
                if (allergens.isNotEmpty()) Text(L("Chất gây dị ứng: ${allergens.joinToString()}", "Allergens: ${allergens.joinToString()}"), style = MaterialTheme.typography.bodySmall)
                warnings.forEach { WarningLine(it) }

                // Substitution suggestion (only for built-in foods)
                if (!food.custom) {
                    if (loadingSubstitution) {
                        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp)
                            Text(L("  Tìm thay thế…", "  Finding swap…"), style = MaterialTheme.typography.bodySmall)
                        }
                    } else if (substitution != null) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(L("💡 Thay thế khỏe hơn", "💡 Healthier swap"), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            Text(substitution!!.name, fontWeight = FontWeight.SemiBold)
                            Text(L("${substitution!!.kcal} kcal · Đạm ${substitution!!.protein_g.toInt()}g · Bột ${substitution!!.carbs_g.toInt()}g · Béo ${substitution!!.fat_g.toInt()}g",
                                "${substitution!!.kcal} kcal · Protein ${substitution!!.protein_g.toInt()}g · Carbs ${substitution!!.carbs_g.toInt()}g · Fat ${substitution!!.fat_g.toInt()}g"),
                                style = MaterialTheme.typography.labelSmall)
                            if (substitution!!.benefits.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    substitution!!.benefits.forEach { benefit ->
                                        Text("✓ $benefit", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    } else {
                        FilledTonalButton(onClick = {
                            loadingSubstitution = true
                            vm.viewModelScope.launch {
                                val detectedFood = DetectedFood(
                                    name = food.name,
                                    kcal = food.kcal,
                                    protein_g = food.protein,
                                    carbs_g = food.carbs,
                                    fat_g = food.fat,
                                    fiber_g = null,
                                    tags = food.tags.toList(),
                                )
                                substitution = vm.api.substitute(detectedFood)
                                loadingSubstitution = false
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(L("💡 Thay thế khỏe hơn", "💡 Find healthier swap"))
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.5f, 1f, 2f).forEach { q ->
                        FilledTonalButton(onClick = { vm.addFood(food, q, warnings); onDismiss() }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                            Text(L("+${AppViewModel.fmtQty(q)} phần", "+${AppViewModel.fmtQty(q)} serving"))
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onEdit) { Text(L("✏️ Sửa thông tin", "✏️ Edit")) } },
        dismissButton = {
            Row {
                // Deleting an edited built-in food brings the original values back.
                if (mine) TextButton(onClick = { vm.deleteCustomFood(food.id); onDismiss() }) {
                    Text(if (vm.library.any { it.id == food.id }) L("Khôi phục gốc", "Restore original") else L("Xóa", "Delete"), color = Palette.Danger)
                }
                TextButton(onClick = onDismiss) { Text(L("Đóng", "Close")) }
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
        title = { Text(L("Sửa thông tin món", "Edit food")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(emoji, { emoji = it.take(4) }, label = { Text(L("Biểu tượng", "Icon")) }, singleLine = true, modifier = Modifier.width(96.dp))
                    OutlinedTextField(name, { name = it }, label = { Text(L("Tên món", "Name")) }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(serving, { serving = it }, label = { Text(L("Khẩu phần", "Serving")) }, singleLine = true, modifier = Modifier.weight(1f))
                    NumberField(kcal, { kcal = it }, "kcal", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(protein, { protein = it; follow(it, carbs, fat) }, L("Đạm g", "Protein g"), Modifier.weight(1f))
                    NumberField(carbs, { carbs = it; follow(protein, it, fat) }, L("Bột g", "Carbs g"), Modifier.weight(1f))
                    NumberField(fat, { fat = it; follow(protein, carbs, it) }, L("Béo g", "Fat g"), Modifier.weight(1f))
                }
                Text(L("Đổi đạm, bột, béo thì calo tự tính lại. Không rành số liệu? Dùng nút dưới đây.", "Change protein, carbs or fat and the calories update. Not sure of the numbers? Use the button below."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = { ingredients = true }, enabled = name.isNotBlank() && vm.hasAi, modifier = Modifier.fillMaxWidth()) {
                    Text(L("🧮 Tính theo thành phần (thịt, rau… bao nhiêu gam)", "🧮 Work it out from ingredients (grams of meat, veg…)"))
                }
                Text(L("Nhóm món (để gợi ý thứ tự ăn)", "Group (for eating-order tips)"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Course.all.forEach { c -> FilterChip(course == c, { course = c }, label = { Text(Course.labels[c] ?: c) }) }
                }
                Text(L("Món này có chứa (để cảnh báo sức khỏe)", "This food contains (for health warnings)"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val options = Tag.allergens.map { it.key to it.value.substringBefore(" (").substringBefore(" /") } + listOf(
                        Tag.HIGH_SUGAR to L("Nhiều đường", "High sugar"), Tag.HIGH_SODIUM to L("Nhiều muối", "High salt"), Tag.HIGH_CARB to L("Nhiều tinh bột", "High carb"),
                        Tag.FRIED to L("Chiên rán", "Fried"), Tag.HIGH_PURINE to L("Nhiều purin", "High purine"), Tag.MEAT to L("Thịt", "Meat"), Tag.PORK to L("Thịt heo", "Pork"), Tag.ALCOHOL to L("Rượu bia", "Alcohol"),
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
                        name = name.trim(), emoji = emoji.ifBlank { "🍽️" }, serving = serving.ifBlank { L("1 phần", "1 serving") },
                        kcal = kcal.toIntOrNull() ?: 0, protein = protein.toFloatOrNull() ?: 0f, carbs = carbs.toFloatOrNull() ?: 0f,
                        fat = fat.toFloatOrNull() ?: 0f, course = course, tags = tags, custom = true,
                    )
                )
            }) { Text(L("Lưu", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Hủy", "Cancel")) } },
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
