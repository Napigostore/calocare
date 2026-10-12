package com.calocare.app.ui

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

private val activityLevels = listOf(
    1.2f to "Ít vận động (ngồi nhiều)",
    1.375f to "Nhẹ (tập 1–3 buổi/tuần)",
    1.55f to "Vừa (tập 3–5 buổi/tuần)",
    1.725f to "Nặng (tập 6–7 buổi/tuần)",
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
        OutlinedTextField(name, { name = it }, label = { Text("Tên gọi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(male, { male = true }, label = { Text("♂ Nam") })
            FilterChip(!male, { male = false }, label = { Text("♀ Nữ") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(birthYear, { birthYear = it }, "Năm sinh", Modifier.weight(1f))
            NumberField(height, { height = it }, "Cao (cm)", Modifier.weight(1f))
            NumberField(weight, { weight = it }, "Nặng (kg)", Modifier.weight(1f), decimal = true)
        }
        Text("Mức vận động", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            activityLevels.forEach { (factor, label) -> FilterChip(activity == factor, { activity = factor }, label = { Text(label) }) }
        }
        Text("Mục tiêu", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Goal.labels.forEach { (id, label) -> FilterChip(goal == id, { goal = id }, label = { Text("${Goal.emoji[id]} $label") }) }
        }
        if (goal == Goal.LOSE || goal == Goal.GAIN) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(targetWeight, { targetWeight = it }, "Cân nặng mục tiêu (kg)", Modifier.weight(1f), decimal = true)
            }
            Text("Tốc độ mỗi tuần", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.25f to "0,25 kg · nhẹ nhàng", 0.5f to "0,5 kg · khuyên dùng", 0.75f to "0,75 kg · nhanh").forEach { (r, label) ->
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
        Text("Bệnh lý cần lưu ý", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Condition.labels.forEach { (id, label) ->
                FilterChip(id in profile.conditions, { onChange { p -> p.copy(conditions = p.conditions.toggle(id)) } }, label = { Text(label) })
            }
        }
        Text("Dị ứng thực phẩm", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag.allergens.forEach { (id, label) ->
                FilterChip(id in profile.allergies, { onChange { p -> p.copy(allergies = p.allergies.toggle(id)) } }, label = { Text(label) })
            }
        }
        Text("Chế độ ăn", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Diet.labels.forEach { (id, label) ->
                FilterChip(profile.diet == id, { onChange { p -> p.copy(diet = id) } }, label = { Text(label) })
            }
        }
        Text("Ẩm thực vùng", style = MaterialTheme.typography.labelLarge)
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
            Text("Chuyên gia dinh dưỡng và thể thao trong túi bạn. Cho mình biết vài thông tin để tính mục tiêu calo phù hợp.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SectionCard(title = "Về bạn") {
                BodyForm(
                    data.profile, "Bắt đầu",
                    extra = {
                        Text("Sức khỏe & ăn kiêng (có thể bỏ qua)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
            SectionCard(title = "Chỉ số của bạn") {
                Text("🎯 Mục tiêu nạp: $target kcal/ngày", fontWeight = FontWeight.SemiBold)
                if (p.kcalAdjust != 0) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Gồm ${if (p.kcalAdjust > 0) "+" else ""}${p.kcalAdjust} kcal điều chỉnh theo cân nặng thực tế", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = { vm.updateProfile { it.copy(kcalAdjust = 0) } }) { Text("Đặt lại") }
                }
                Text("BMR ${Nutrition.bmr(p).roundToInt()} kcal · TDEE ${Nutrition.tdee(p).roundToInt()} kcal · BMI ${"%.1f".format(Nutrition.bmi(p))} (${Nutrition.bmiLabel(Nutrition.bmi(p))})")
                Text("Đạm ${macros.protein}g · Tinh bột ${macros.carbs}g · Béo ${macros.fat}g", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("BMR tính theo công thức Mifflin-St Jeor; BMI phân loại theo ngưỡng châu Á.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            SectionCard(title = "🛡️ Chế độ sức khỏe") {
                Text("App sẽ cảnh báo khi bạn chọn hoặc chụp món có chất không phù hợp, và loại chúng khỏi thực đơn gợi ý.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HealthForm(p) { vm.updateProfile(it) }
            }
        }

        item {
            SectionCard(title = "Thông tin cơ thể & mục tiêu") {
                BodyForm(p, "Lưu thay đổi") { saved ->
                    vm.updateProfile { cur -> merge(cur, saved) }
                    vm.messages.tryEmit("Đã lưu hồ sơ")
                }
            }
        }

        item { AiKeyCard(vm) }

        item {
            var confirmWipe by remember { mutableStateOf(false) }
            SectionCard(title = "Quyền riêng tư & dữ liệu") {
                Text("Dữ liệu của bạn chỉ nằm trên thiết bị này.", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onPrivacy) { Text("Chính sách quyền riêng tư") }
                    androidx.compose.material3.TextButton(onClick = { confirmWipe = true }) { Text("Xóa toàn bộ dữ liệu", color = Palette.Danger) }
                }
                Text(
                    "Dữ liệu sản phẩm đóng gói: Open Food Facts (openfoodfacts.org), giấy phép ODbL; hình ảnh sản phẩm CC BY-SA.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (confirmWipe) androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmWipe = false },
                title = { Text("Xóa toàn bộ dữ liệu?") },
                text = { Text("Hồ sơ, nhật ký ăn uống, cân nặng, món tự tạo và API key sẽ bị xóa khỏi thiết bị. Không thể khôi phục.") },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmWipe = false; vm.wipeAllData() }) { Text("Xóa", color = Palette.Danger) } },
                dismissButton = { Button(onClick = { confirmWipe = false }) { Text("Giữ lại") } },
            )
        }

        item {
            Text(
                "CaloCare đưa ra ước tính và gợi ý tham khảo, không thay thế chẩn đoán hay chỉ định của bác sĩ. Nếu bạn có bệnh nền, đang mang thai hoặc dùng thuốc, hãy hỏi ý kiến bác sĩ trước khi thay đổi chế độ ăn.",
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

    SectionCard(title = "✨ Trợ lý AI") {
        if (vm.hostedConfigured) {
            Text(
                "AI đã bật sẵn: chụp ảnh bữa ăn tính calo, ước tính món chưa có và chat với coach.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (vm.usingHosted) {
                val q = vm.quota
                if (q != null) {
                    Text("📷 Còn ${q.photos_left_today} ảnh hôm nay" + if (q.trial_photos_left > 0) " · ${q.trial_photos_left} ảnh dùng thử" else "", fontWeight = FontWeight.Medium)
                    Text("💬 Còn ${q.chats_left_today} câu hỏi" + if (q.trial_chats_left > 0) " dùng thử" else " hôm nay", fontWeight = FontWeight.Medium)
                }
                Text(
                    "Dùng thử 100 ảnh (tối đa 30 ảnh/ngày) và 100 câu hỏi. Sau đó mỗi ngày có 6 ảnh và 10 câu hỏi.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!advanced) androidx.compose.material3.TextButton(onClick = { advanced = true }) { Text("Nâng cao: dùng API key riêng") }
        }
        if (advanced) {
            Text(
                (if (vm.hostedConfigured) "Dùng API key riêng thì không bị giới hạn lượt. " else "Nhập API key để bật AI. ") +
                    "Chỉ key của Anthropic (sk-ant-…) mới thay cho AI có sẵn. Để trống ô này và bấm Lưu để quay về AI có sẵn. Key chỉ lưu trên máy này.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                key, { key = it.trim() }, label = { Text("API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { vm.saveApiKey(key); vm.messages.tryEmit(if (key.isBlank()) "Đã xóa API key" else "Đã lưu API key") }, enabled = key != vm.apiKey) { Text("Lưu key") }
                Text(
                    when {
                        vm.apiKey.isBlank() -> if (vm.hostedConfigured) "Đang dùng AI có sẵn" else "Chưa bật"
                        AiClient.isClaudeKey(vm.apiKey) -> "✅ Claude (key riêng)"
                        vm.hostedConfigured -> "Đang dùng AI có sẵn (key DeepSeek không cần)"
                        else -> "✅ DeepSeek (không ảnh)"
                    },
                    modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
