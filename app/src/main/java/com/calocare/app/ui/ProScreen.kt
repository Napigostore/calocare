package com.calocare.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calocare.app.AppViewModel
import com.calocare.app.L
import com.calocare.app.billing.ProBilling
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** What CaloCare Pro adds, the two plans with Google Play's prices, and the purchase button. */
@Composable
fun ProDialog(vm: AppViewModel, onClose: () -> Unit) {
    val billing = vm.billing
    val context = LocalContext.current
    val q = vm.quota
    LaunchedEffect(Unit) { billing.refresh() }

    FullScreenDialog("✨ CaloCare Pro", onClose = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (q?.pro == true) {
                Text(L("Bạn đang dùng CaloCare Pro 🎉", "You're on CaloCare Pro 🎉"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (q.pro_until > 0) Text(L("Gia hạn hoặc hết hạn: ", "Renews or ends: ") + DateFormat.getDateInstance().format(Date(q.pro_until)))
                Text(L("📷 Còn ${q.photos_left_today} ảnh · 💬 ${q.chats_left_today} câu hỏi hôm nay", "📷 ${q.photos_left_today} photos · 💬 ${q.chats_left_today} questions left today"))
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MANAGE_URL))) }) { Text(L("Quản lý gói trên Google Play", "Manage subscription on Google Play")) }
                return@Column
            }

            Text(L("Chụp mọi bữa ăn, AI tính calo cho bạn", "Snap every meal and let AI count the calories"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Comparison(q?.free_photos_per_day ?: 2, q?.free_chats_per_day ?: 5)

            var picked by remember { mutableStateOf("yearly") }
            val plans = billing.plans
            if (plans.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp))
                    Text(L("Đang tải giá từ Google Play…", "Loading prices from Google Play…"))
                }
            }
            val monthly = plans.firstOrNull { it.basePlan == "monthly" }
            plans.forEach { plan ->
                val yearly = plan.basePlan == "yearly"
                val saving = if (yearly && monthly != null && monthly.priceMicros > 0) (100 - plan.priceMicros * 100.0 / (monthly.priceMicros * 12)).roundToInt() else 0
                val selected = picked == plan.basePlan
                Card(
                    Modifier.fillMaxWidth().clickable { picked = plan.basePlan },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (yearly) L("Theo năm", "Yearly") else L("Theo tháng", "Monthly"), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (saving > 0) Text(L("Tiết kiệm $saving%", "Save $saving%"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Text(plan.price + if (yearly) L(" / năm", " / year") else L(" / tháng", " / month"), style = MaterialTheme.typography.titleMedium)
                        if (plan.freeTrialDays > 0) Text(L("Dùng thử miễn phí ${plan.freeTrialDays} ngày", "${plan.freeTrialDays}-day free trial"), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            val plan = plans.firstOrNull { it.basePlan == picked } ?: plans.firstOrNull()
            Button(
                onClick = { (context as? Activity)?.let { activity -> plan?.let { billing.buy(activity, it) } } },
                enabled = plan != null && !billing.busy, modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (plan != null && plan.freeTrialDays > 0) L("Dùng thử miễn phí", "Start free trial") else L("Nâng cấp Pro", "Upgrade to Pro"), modifier = Modifier.padding(vertical = 6.dp))
            }
            billing.note?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium) }
            Text(
                L("Gói tự gia hạn cho tới khi bạn hủy. Hủy bất cứ lúc nào trong Google Play → Thanh toán và gói thuê bao, ít nhất 24 giờ trước kỳ gia hạn. " +
                    "Ghi món bằng tay, thực đơn, biểu đồ và đồng hồ luôn miễn phí.",
                    "Renews automatically until you cancel. Cancel any time in Google Play → Payments & subscriptions, at least 24 hours before renewal. " +
                    "Manual logging, menus, charts and watch sync always stay free."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { billing.refresh() }) { Text(L("Khôi phục giao dịch", "Restore purchase")) }
        }
    }
}

@Composable
private fun Comparison(freePhotos: Int, freeChats: Int) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Text("", Modifier.weight(1.6f))
                Text(L("Miễn phí", "Free"), Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                Text("Pro", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            listOf(
                Triple(L("📷 Ảnh AI mỗi ngày", "📷 AI photos a day"), "$freePhotos", L("Thoải mái*", "Plenty*")),
                Triple(L("💬 Câu hỏi coach mỗi ngày", "💬 Coach questions a day"), "$freeChats", "100"),
                Triple(L("🧮 Tính theo thành phần", "🧮 Ingredient breakdown"), L("Theo lượt", "Uses chats"), "✓"),
                Triple(L("✍️ Ghi món, thực đơn, biểu đồ", "✍️ Logging, menus, charts"), "✓", "✓"),
            ).forEach { (what, free, pro) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(what, Modifier.weight(1.6f), style = MaterialTheme.typography.bodyMedium)
                    Text(free, Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text(pro, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(L("* Tới 40 ảnh mỗi ngày, đủ cho mọi bữa ăn.", "* Up to 40 photos a day, enough for every meal."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val MANAGE_URL = "https://play.google.com/store/account/subscriptions?sku=${ProBilling.SUBSCRIPTION}&package=com.calocare.app"
