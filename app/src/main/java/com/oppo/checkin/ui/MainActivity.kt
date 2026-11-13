package com.oppo.checkin.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val OppoGreen = Color(0xFF046A38)
private val OppoLightGreen = Color(0xFFE6F4EC)

private val AppColorScheme = lightColorScheme(
    primary = OppoGreen,
    onPrimary = Color.White,
    primaryContainer = OppoLightGreen,
    onPrimaryContainer = Color(0xFF00210E),
    secondary = Color(0xFF4F6354),
    background = Color(0xFFF7FBF7),
    surface = Color.White
)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = AppColorScheme) {
                OppoCheckinApp(
                    viewModel = viewModel,
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenExactAlarmSettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        }
                    },
                    onOpenNotificationSettings = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        }
                        startActivity(intent)
                    },
                    onOpenAppDetailsSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus()
        viewModel.refreshInstalledApps()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OppoCheckinApp(
    viewModel: MainViewModel,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppDetailsSettings: () -> Unit
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val lastSuccessDate by viewModel.lastSuccessDate.collectAsStateWithLifecycle()
    val todayStatus by viewModel.todayStatus.collectAsStateWithLifecycle()
    val nextScheduledEpochMs by viewModel.nextScheduledEpochMs.collectAsStateWithLifecycle()
    val usingExactAlarm by viewModel.usingExactAlarm.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val currentState by viewModel.currentState.collectAsStateWithLifecycle()
    val statusDetail by viewModel.statusDetail.collectAsStateWithLifecycle()
    val appCandidates by viewModel.appCandidates.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshStatus()
    }

    LaunchedEffect(uiMessage) {
        val msg = uiMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeUiMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OPPO 每日签到助手") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "概览") },
                    label = { Text("概览") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = "执行记录") },
                    label = { Text("执行记录") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "设置与指南") },
                    label = { Text("设置与指南") }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> DashboardTab(
                modifier = Modifier.padding(innerPadding),
                config = config,
                todayStatus = todayStatus,
                lastSuccessDate = lastSuccessDate,
                nextScheduledEpochMs = nextScheduledEpochMs,
                usingExactAlarm = usingExactAlarm,
                permissions = permissions,
                currentState = currentState,
                statusDetail = statusDetail,
                appCandidates = appCandidates,
                onToggleEnabled = { viewModel.setAutomationEnabled(it) },
                onUpdateTime = { h, m -> viewModel.updateScheduleTime(h, m) },
                onSelectApp = { viewModel.selectTargetApp(it) },
                onTestNow = { viewModel.testAutomationNow() },
                onAcceptDisclosureAndOpenSettings = {
                    viewModel.acceptAccessibilityDisclosure()
                    onOpenAccessibilitySettings()
                },
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onOpenNotificationSettings()
                    }
                },
                onOpenNotificationSettings = onOpenNotificationSettings
            )

            1 -> HistoryTab(
                modifier = Modifier.padding(innerPadding),
                history = history,
                onClearAllData = { viewModel.clearAllLocalData() }
            )

            2 -> SettingsAndGuideTab(
                modifier = Modifier.padding(innerPadding),
                config = config,
                appCandidates = appCandidates,
                onSelectApp = { viewModel.selectTargetApp(it) },
                onSaveLabelConfig = { m, p, c, a, s, t ->
                    viewModel.updateLabelConfig(m, p, c, a, s, t)
                },
                onOpenAppDetailsSettings = onOpenAppDetailsSettings
            )
        }
    }
}
