package com.calocare.app.ui

import com.calocare.app.L
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.calocare.app.AppViewModel
import com.calocare.app.data.AppData
import com.calocare.app.net.AiClient
import com.calocare.app.data.Condition
import com.calocare.app.data.Diet
import com.calocare.app.data.Goal
import com.calocare.app.data.Nutrition
import com.calocare.app.data.Profile
import com.calocare.app.data.Region
import com.calocare.app.data.Tag
import java.time.LocalDate
import kotlin.math.roundToInt

private val activityLevels get() = listOf(
    1.2f to L("Ít vận động (ngồi nhiều)", "Sedentary (mostly sitting)"),
    1.375f to L("Nhẹ (tập 1–3 buổi/tuần)", "Light (1–3 workouts a week)"),
    1.55f to L("Vừa (tập 3–5 buổi/tuần)", "Moderate (3–5 workouts a week)"),
    1.725f to L("Nặng (tập 6–7 buổi/tuần)", "Heavy (6–7 workouts a week)"),
)

/** Body numbers and goal; shared by the first-run screen and the profile tab. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BodyForm(profile: Profile, saveLabel: String, extra: @Composable () -> Unit = {}, onSave: (Profile) -> Unit) {
    var name by rememberSaveable { mutableStateOf(profile.name) }
    var male by rememberSaveable { mutableStateOf(profile.male) }
    var birthYear by rememberSaveable { mutableStateOf(profile.birthYear.toString()) }
    // Height and weight can change underneath us when the watch syncs.
    var height by rememberSaveable(profile.heightCm) { mutableStateOf(profile.heightCm.roundToInt().toString()) }
    var weight by rememberSaveable(profile.weightKg) { mutableStateOf(num(profile.weightKg)) }
    var activity by rememberSaveable { mutableStateOf(profile.activity) }
    var goal by rememberSaveable { mutableStateOf(profile.goal) }
    var targetWeight by rememberSaveable { mutableStateOf(num(profile.targetWeightKg)) }
    var rate by rememberSaveable { mutableStateOf(profile.weeklyRateKg) }

    val year = birthYear.toIntOrNull()
    val h = height.toFloatOrNull()
    val w = weight.toFloatOrNull()
    val valid = year != null && year in 1925..(LocalDate.now().year - 10) && h != null && h in 100f..230f && w != null && w in 25f..300f

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text(L("Tên gọi", "Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(male, { male = true }, label = { Text(L("♂ Nam", "♂ Male")) })
            FilterChip(!male, { male = false }, label = { Text(L("♀ Nữ", "♀ Female")) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(birthYear, { birthYear = it }, L("Năm sinh", "Birth year"), Modifier.weight(1f))
            NumberField(height, { height = it }, L("Cao (cm)", "Height (cm)"), Modifier.weight(1f))
            NumberField(weight, { weight = it }, L("Nặng (kg)", "Weight (kg)"), Modifier.weight(1f), decimal = true)
        }
        Text(L("Mức vận động", "Activity level"), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            activityLevels.forEach { (factor, label) -> FilterChip(activity == factor, { activity = factor }, label = { Text(label) }) }
        }
        Text(L("Mục tiêu", "Goal"), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Goal.labels.forEach { (id, label) -> FilterChip(goal == id, { goal = id }, label = { Text("${Goal.emoji[id]} $label") }) }
        }
        if (goal == Goal.LOSE || goal == Goal.GAIN) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(targetWeight, { targetWeight = it }, L("Cân nặng mục tiêu (kg)", "Target weight (kg)"), Modifier.weight(1f), decimal = true)
            }
            Text(L("Tốc độ mỗi tuần", "Rate per week"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.25f to L("0,25 kg · nhẹ nhàng", "0.25 kg · gentle"), 0.5f to L("0,5 kg · khuyên dùng", "0.5 kg · recommended"), 0.75f to L("0,75 kg · nhanh", "0.75 kg · fast")).forEach { (r, label) ->
                    FilterChip(rate == r, { rate = r }, label = { Text(label, maxLines = 1) })
                }
            }
        }
        extra()
        Button(
            enabled = valid, modifier = Modifier.fillMaxWidth(),
            onClick = {
                onSave(
                    profile.copy(
                        onboarded = true, name = name.trim(), male = male, birthYear = year!!, heightCm = h!!, weightKg = w!!,
                        activity = activity, goal = goal, targetWeightKg = targetWeight.toFloatOrNull() ?: w, weeklyRateKg = rate,
                    )
                )
            },
        ) { Text(saveLabel) }
    }
}

private fun num(v: Float) = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v).replace(',', '.')

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HealthForm(profile: Profile, onChange: ((Profile) -> Profile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(L("Bệnh lý cần lưu ý", "Health conditions"), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Condition.labels.forEach { (id, label) ->
                FilterChip(id in profile.conditions, { onChange { p -> p.copy(conditions = p.conditions.toggle(id)) } }, label = { Text(label) })
            }
        }
        Text(L("Dị ứng thực phẩm", "Food allergies"), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag.allergens.forEach { (id, label) ->
                FilterChip(id in profile.allergies, { onChange { p -> p.copy(allergies = p.allergies.toggle(id)) } }, label = { Text(label) })
            }
        }
        Text(L("Chế độ ăn", "Diet"), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Diet.labels.forEach { (id, label) ->
                FilterChip(profile.diet == id, { onChange { p -> p.copy(diet = id) } }, label = { Text(label) })
            }
        }
        Text(L("Ẩm thực vùng", "Cuisine"), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Region.labels.forEach { (id, label) ->
                FilterChip(profile.region == id, { onChange { p -> p.copy(region = id) } }, label = { Text(label) })
            }
        }
    }
}

private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

@Composable
fun OnboardingScreen(vm: AppViewModel, data: AppData) {
    LazyColumn(
        Modifier.padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("🥗 CaloCare", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(L("Chuyên gia dinh dưỡng và thể thao trong túi bạn. Cho mình biết vài thông tin để tính mục tiêu calo phù hợp.", "A dietitian and sports coach in your pocket. Tell me a little about you to work out your calorie target."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SectionCard(title = L("Về bạn", "About you")) {
                BodyForm(
                    data.profile, L("Bắt đầu", "Get started"),
                    extra = {
                        Text(L("Sức khỏe & ăn kiêng (có thể bỏ qua)", "Health & diet (optional)"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        HealthForm(data.profile) { vm.updateProfile(it) }
                    },
                ) { saved -> vm.updateProfile { cur -> merge(cur, saved) } }
            }
        }
    }
}

/** The body form edits a snapshot; keep whatever the health chips changed in the meantime. */
private fun merge(current: Profile, saved: Profile) = saved.copy(
    conditions = current.conditions, allergies = current.allergies, diet = current.diet,
    region = current.region, wearableLinked = current.wearableLinked,
    kcalAdjust = current.kcalAdjust, kcalAdjustDate = current.kcalAdjustDate,
)

@Composable
fun ProfileScreen(vm: AppViewModel, data: AppData, padding: PaddingValues, onPrivacy: () -> Unit) {
    val p = data.profile
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
                item {
            val target = Nutrition.targetKcal(p)
            val macros = Nutrition.macroTargets(p, target)
            SectionCard(title = L("Chỉ số của bạn", "Your numbers")) {
                Text(L("🎯 Mục tiêu nạp: $target kcal/ngày", "🎯 Daily target: $target kcal"), fontWeight = FontWeight.SemiBold)
                if (p.kcalAdjust != 0) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(L("Gồm ${if (p.kcalAdjust > 0) "+" else ""}${p.kcalAdjust} kcal điều chỉnh theo cân nặng thực tế", "Includes ${if (p.kcalAdjust > 0) "+" else ""}${p.kcalAdjust} kcal adjusted to your actual weight"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = { vm.updateProfile { it.copy(kcalAdjust = 0) } }) { Text(L("Đặt lại", "Reset")) }
                }
                Text("BMR ${Nutrition.bmr(p).roundToInt()} kcal · TDEE ${Nutrition.tdee(p).roundToInt()} kcal · BMI ${"%.1f".format(Nutrition.bmi(p))} (${Nutrition.bmiLabel(Nutrition.bmi(p))})")
                Text(L("Đạm ${macros.protein}g · Tinh bột ${macros.carbs}g · Béo ${macros.fat}g", "Protein ${macros.protein}g · Carbs ${macros.carbs}g · Fat ${macros.fat}g"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(L("BMR tính theo công thức Mifflin-St Jeor; BMI phân loại theo ngưỡng châu Á.", "BMR uses the Mifflin-St Jeor equation; BMI uses the WHO categories."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            SectionCard(title = L("🛡️ Chế độ sức khỏe", "🛡️ Health mode")) {
                Text(L("App sẽ cảnh báo khi bạn chọn hoặc chụp món có chất không phù hợp, và loại chúng khỏi thực đơn gợi ý.", "The app warns you when a food you pick or photograph doesn't suit you, and leaves it out of suggested menus."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HealthForm(p) { vm.updateProfile(it) }
            }
        }

        item {
            SectionCard(title = L("Thông tin cơ thể & mục tiêu", "Body & goal")) {
                BodyForm(p, L("Lưu thay đổi", "Save changes")) { saved ->
                    vm.updateProfile { cur -> merge(cur, saved) }
                    vm.messages.tryEmit(L("Đã lưu hồ sơ", "Profile saved"))
                }
            }
        }

        item { AiKeyCard(vm) }

        item {
            var confirmWipe by remember { mutableStateOf(false) }
            SectionCard(title = L("Quyền riêng tư & dữ liệu", "Privacy & data")) {
                Text(L("Dữ liệu của bạn chỉ nằm trên thiết bị này.", "Your data stays on this device."), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onPrivacy) { Text(L("Chính sách quyền riêng tư", "Privacy policy")) }
                    androidx.compose.material3.TextButton(onClick = { confirmWipe = true }) { Text(L("Xóa toàn bộ dữ liệu", "Delete all data"), color = Palette.Danger) }
                }
                Text(
                    L("Dữ liệu sản phẩm đóng gói: Open Food Facts (openfoodfacts.org), giấy phép ODbL; hình ảnh sản phẩm CC BY-SA.", "Packaged product data: Open Food Facts (openfoodfacts.org), ODbL licence; product images CC BY-SA."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (confirmWipe) androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmWipe = false },
                title = { Text(L("Xóa toàn bộ dữ liệu?", "Delete all data?")) },
                text = { Text(L("Hồ sơ, nhật ký ăn uống, cân nặng, món tự tạo và API key sẽ bị xóa khỏi thiết bị. Không thể khôi phục.", "Your profile, food log, weights, custom foods and API key will be deleted from this device. This can't be undone.")) },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmWipe = false; vm.wipeAllData() }) { Text(L("Xóa", "Delete"), color = Palette.Danger) } },
                dismissButton = { Button(onClick = { confirmWipe = false }) { Text(L("Giữ lại", "Keep")) } },
            )
        }

        item {
            Text(
                L("CaloCare đưa ra ước tính và gợi ý tham khảo, không thay thế chẩn đoán hay chỉ định của bác sĩ. Nếu bạn có bệnh nền, đang mang thai hoặc dùng thuốc, hãy hỏi ý kiến bác sĩ trước khi thay đổi chế độ ăn.", "CaloCare gives estimates and general suggestions; it does not replace your doctor's diagnosis or advice. If you have a medical condition, are pregnant or take medication, talk to your doctor before changing your diet."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AiKeyCard(vm: AppViewModel) {
    var key by remember { mutableStateOf(vm.apiKey) }
    var advanced by remember { mutableStateOf(!vm.hostedConfigured || vm.apiKey.isNotBlank()) }
    androidx.compose.runtime.LaunchedEffect(vm.usingHosted) { vm.refreshQuota() }

    SectionCard(title = L("✨ Trợ lý AI", "✨ AI assistant")) {
        if (vm.hostedConfigured) {
            Text(
                L("AI đã bật sẵn: chụp ảnh bữa ăn tính calo, ước tính món chưa có và chat với coach.", "AI is on: meal photos to calories, estimates for new foods, and chat with the coach."),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (vm.usingHosted) {
                val q = vm.quota
                if (q != null) {
                    Text(L("📷 Còn ${q.photos_left_today} ảnh hôm nay", "📷 ${q.photos_left_today} photos left today") + if (q.trial_photos_left > 0) L(" · ${q.trial_photos_left} ảnh dùng thử", " · ${q.trial_photos_left} trial photos") else "", fontWeight = FontWeight.Medium)
                    Text(L("💬 Còn ${q.chats_left_today} câu hỏi", "💬 ${q.chats_left_today} questions left") + if (q.trial_chats_left > 0) L(" dùng thử", " (trial)") else L(" hôm nay", " today"), fontWeight = FontWeight.Medium)
                }
                Text(
                    L("Dùng thử 100 ảnh (tối đa 30 ảnh/ngày) và 100 câu hỏi. Sau đó mỗi ngày có 6 ảnh và 10 câu hỏi.", "Trial: 100 photos (up to 30 a day) and 100 questions. After that, 6 photos and 10 questions a day."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!advanced) androidx.compose.material3.TextButton(onClick = { advanced = true }) { Text(L("Nâng cao: dùng API key riêng", "Advanced: use your own API key")) }
        }
        if (advanced) {
            Text(
                (if (vm.hostedConfigured) L("Dùng API key riêng thì không bị giới hạn lượt. ", "With your own API key there are no limits. ") else L("Nhập API key để bật AI. ", "Enter an API key to turn on AI. ")) +
                    L("Chỉ key của Anthropic (sk-ant-…) mới thay cho AI có sẵn. Để trống ô này và bấm Lưu để quay về AI có sẵn. Key chỉ lưu trên máy này.",
                        "Only an Anthropic key (sk-ant-…) replaces the built-in AI. Clear this field and tap Save to go back to the built-in AI. The key stays on this phone."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                key, { key = it.trim() }, label = { Text("API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { vm.saveApiKey(key); vm.messages.tryEmit(if (key.isBlank()) L("Đã xóa API key", "API key removed") else L("Đã lưu API key", "API key saved")) }, enabled = key != vm.apiKey) { Text(L("Lưu key", "Save key")) }
                Text(
                    when {
                        vm.apiKey.isBlank() -> if (vm.hostedConfigured) L("Đang dùng AI có sẵn", "Using the built-in AI") else L("Chưa bật", "Off")
                        AiClient.isClaudeKey(vm.apiKey) -> L("✅ Claude (key riêng)", "✅ Claude (own key)")
                        vm.hostedConfigured -> L("Đang dùng AI có sẵn (key DeepSeek không cần)", "Using the built-in AI (DeepSeek key not needed)")
                        else -> L("✅ DeepSeek (không ảnh)", "✅ DeepSeek (no photos)")
                    },
                    modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
