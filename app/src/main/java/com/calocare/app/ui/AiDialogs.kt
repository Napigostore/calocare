package com.calocare.app.ui

import com.calocare.app.label
import com.calocare.app.L
import androidx.compose.foundation.Image
import com.calocare.app.data.FoodMatcher
import com.calocare.app.PhotoItem
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.remember
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.calocare.app.AppViewModel
import com.calocare.app.PhotoState
import com.calocare.app.data.AppData
import com.calocare.app.data.Meal
import com.calocare.app.data.Nutrition
import com.calocare.app.data.entriesOn
import kotlin.math.roundToInt

@Composable
fun FullScreenDialog(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.systemBarsPadding().imePadding()) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, L("Đóng", "Close")) }
                }
                content()
            }
        }
    }
}

private val starters get() = listOf(
    L("Gợi ý thực đơn ngày mai cho tôi", "Suggest a menu for tomorrow"),
    L("Tối nay tôi nên ăn gì?", "What should I eat tonight?"),
    L("Nhận xét tuần ăn uống của tôi", "Review my eating this week"),
    L("Lịch tập phù hợp mục tiêu của tôi", "A workout plan for my goal"),
    L("Ăn gì trước và sau khi tập?", "What to eat before and after training?"),
)

/** Coach tab: proactive advice from the user's own numbers on top, AI chat underneath. */
@Composable
fun CoachScreen(vm: AppViewModel, data: AppData, padding: PaddingValues, onNeedAiKey: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.chat.size, vm.chatBusy) {
        if (vm.chat.isNotEmpty()) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
    }
    val send = { text: String -> if (vm.hasAi) vm.sendChat(text) else onNeedAiKey() }

    Column(Modifier.padding(padding).consumeWindowInsets(padding).imePadding()) {
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Coach", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item {
                SectionCard(title = L("🩺 Hôm nay bạn nên…", "🩺 Today you should…")) {
                    val today = java.time.LocalDate.now()
                    val flagged = data.entriesOn(today).flatMap { e -> Nutrition.warnings(e.tags, data.profile).map { e.name to it } }.distinct()
                    flagged.take(3).forEach { (name, w) -> WarningLine(w.copy(text = "$name: ${w.text}")) }
                    Nutrition.tips(data, today).take(4).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            item { NextMealCard(vm, data) }
            if (vm.chat.isEmpty()) item {
                Text(
                    if (vm.hasAi) L("Hỏi thêm bất cứ điều gì — mình đã xem hồ sơ, mục tiêu và nhật ký 7 ngày của bạn.", "Ask me anything — I've looked at your profile, goal and last 7 days of logs.")
                    else L("Các lời khuyên trên tính từ số liệu của bạn, không cần mạng. Để trò chuyện với coach AI, bật Trợ lý AI trong Hồ sơ.", "These tips are worked out from your numbers, offline. To chat with the AI coach, turn on the AI assistant in Profile."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
            }
            items(vm.chat) { turn ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (turn.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(
                        turn.text,
                        color = if (turn.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (turn.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            if (vm.chatBusy) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(L("  Đang soạn câu trả lời…", "  Writing a reply…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (vm.chat.isEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                starters.forEach { s -> AssistChip(onClick = { send(s) }, label = { Text(s) }) }
            }
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input, { input = it }, placeholder = { Text(L("Hỏi coach…", "Ask the coach…")) }, maxLines = 4,
                shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = { send(input); if (vm.hasAi) input = "" }, enabled = input.isNotBlank() && !vm.chatBusy) {
                Icon(Icons.AutoMirrored.Filled.Send, L("Gửi", "Send"))
            }
        }
        Text(
            L("Thông tin mang tính tham khảo, không thay thế tư vấn của bác sĩ.", "For information only; not a substitute for your doctor's advice."),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        )
    }
}
