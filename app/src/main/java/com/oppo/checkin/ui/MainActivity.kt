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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LiquidGlassTheme {
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
    val diagnosticLogs by viewModel.diagnosticLogs.collectAsStateWithLifecycle()
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
    val palette = LocalLiquidGlassPalette.current

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

    AmbientGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                FloatingBottomDock(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    accentColor = palette.accentBlueViolet
                )
            }
        ) { innerPadding ->
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                },
                label = "tabTransition",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) { tab ->
                when (tab) {
                    0 -> DashboardTab(
                        config = config,
                        todayStatus = todayStatus,
                        lastSuccessDate = lastSuccessDate,
                        nextScheduledEpochMs = nextScheduledEpochMs,
                        usingExactAlarm = usingExactAlarm,
                        permissions = permissions,
                        currentState = currentState,
                        statusDetail = statusDetail,
                        history = history,
                        appCandidates = appCandidates,
                        onToggleEnabled = { viewModel.setAutomationEnabled(it) },
                        onUpdateTime = { h, m -> viewModel.updateScheduleTime(h, m) },
                        onSelectApp = { viewModel.selectTargetApp(it) },
                        onTestNow = { viewModel.testAutomationNow() },
                        onNavigateHistory = { selectedTab = 1 },
                        onNavigateSettings = { selectedTab = 2 },
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
                        history = history,
                        onClearAllData = { viewModel.clearAllLocalData() }
                    )

                    else -> SettingsAndGuideTab(
                        config = config,
                        permissions = permissions,
                        usingExactAlarm = usingExactAlarm,
                        appCandidates = appCandidates,
                        diagnosticLogs = diagnosticLogs,
                        onToggleEnabled = { viewModel.setAutomationEnabled(it) },
                        onUpdateTime = { h, m -> viewModel.updateScheduleTime(h, m) },
                        onSelectApp = { viewModel.selectTargetApp(it) },
                        onToggleDebugTestMode = { viewModel.setDebugTestMode(it) },
                        onSaveLabelConfig = { m, p, c, a, s, t ->
                            viewModel.updateLabelConfig(m, p, c, a, s, t)
                        },
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
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onOpenAppDetailsSettings = onOpenAppDetailsSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingBottomDock(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        FloatingGlassIsland(
            modifier = Modifier.fillMaxWidth(),
            elevation = GlassElevation.SECONDARY_ISLAND,
            cornerRadius = 999.dp,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CapsuleGlassButton(
                    label = "概览",
                    symbol = "◉",
                    accentColor = if (selectedTab == 0) accentColor else null,
                    onClick = { onSelectTab(0) },
                    modifier = Modifier.weight(1f)
                )
                CapsuleGlassButton(
                    label = "执行记录",
                    symbol = "🕒",
                    accentColor = if (selectedTab == 1) accentColor else null,
                    onClick = { onSelectTab(1) },
                    modifier = Modifier.weight(1f)
                )
                CapsuleGlassButton(
                    label = "任务设置",
                    symbol = "⚙",
                    accentColor = if (selectedTab == 2) accentColor else null,
                    onClick = { onSelectTab(2) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
