package com.calocare.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calocare.app.data.AppData
import com.calocare.app.ui.CaloTheme
import com.calocare.app.ui.CoachScreen
import com.calocare.app.ui.FullScreenDialog
import com.calocare.app.ui.ProgressScreen
import com.calocare.app.ui.DashboardScreen
import com.calocare.app.ui.FoodScreen
import com.calocare.app.ui.MenuScreen
import com.calocare.app.ui.OnboardingScreen
import com.calocare.app.ui.PhotoDialog
import com.calocare.app.ui.PrivacyContent
import com.calocare.app.ui.ProfileScreen
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val privacyRequested = intent?.action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" ||
            intent?.action == "android.intent.action.VIEW_PERMISSION_USAGE"
        setContent {
            CaloTheme {
                val vm: AppViewModel = viewModel()
                val data by vm.data.collectAsStateWithLifecycle()
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        data.profile.onboarded -> MainScreen(vm, data, privacyRequested)
                        privacyRequested -> Box(Modifier.systemBarsPadding()) { PrivacyContent() }
                        else -> Box(Modifier.systemBarsPadding()) { OnboardingScreen(vm, data) }
                    }
                }
            }
        }
    }
}

private class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Hôm nay", Icons.Default.Home),
    Tab("Ăn uống", Icons.Default.Restaurant),
    Tab("Thực đơn", Icons.Default.MenuBook),
    Tab("Tiến độ", Icons.Default.Insights),
    Tab("Coach", Icons.Default.SupportAgent),
)

@Composable
private fun MainScreen(vm: AppViewModel, data: AppData, showPrivacy: Boolean) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var profile by rememberSaveable { mutableStateOf(false) }
    // Health Connect opens the app with this intent when the user taps "privacy policy" on its permission screen.
    var privacy by rememberSaveable { mutableStateOf(showPrivacy) }
    var needKey by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        // collectLatest: a new "+1 …" replaces the one on screen instead of queueing behind it.
        vm.messages.collectLatest { snackbar.showSnackbar(it, duration = SnackbarDuration.Short) }
    }
    // Pull fresh watch data whenever the app comes back to the foreground.
    LifecycleResumeEffect(data.profile.wearableLinked) {
        if (data.profile.wearableLinked) vm.syncWearable()
        onPauseOrDispose { }
    }

    val openCoach = { tab = 4 }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) }, label = { Text(t.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            0 -> DashboardScreen(vm, data, padding, onCoach = openCoach, onAddFood = { tab = 1 }, onProfile = { profile = true })
            1 -> FoodScreen(vm, data, padding, onNeedAiKey = { needKey = true })
            2 -> MenuScreen(vm, data, padding, onCoach = openCoach)
            3 -> ProgressScreen(vm, data, padding)
            else -> CoachScreen(vm, data, padding, onNeedAiKey = { needKey = true })
        }
    }

    vm.photo?.let { PhotoDialog(vm, data, it) }
    if (profile) FullScreenDialog("Hồ sơ", onClose = { profile = false }) {
        ProfileScreen(vm, data, PaddingValues(0.dp), onPrivacy = { privacy = true })
    }
    if (privacy) FullScreenDialog("Chính sách quyền riêng tư", onClose = { privacy = false }) { PrivacyContent() }
    if (needKey) {
        AlertDialog(
            onDismissRequest = { needKey = false },
            title = { Text("✨ Cần bật trợ lý AI") },
            text = { Text("Tính năng này dùng AI. Hãy nhập API key trong Hồ sơ → Trợ lý AI. Mọi tính năng còn lại vẫn dùng được mà không cần key.") },
            confirmButton = { Button(onClick = { needKey = false; profile = true }) { Text("Mở Hồ sơ") } },
            dismissButton = { TextButton(onClick = { needKey = false }) { Text("Để sau") } },
        )
    }
}
