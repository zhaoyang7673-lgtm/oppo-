package com.oppo.checkin.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oppo.checkin.data.AppCandidate
import com.oppo.checkin.data.CheckinConfig
import com.oppo.checkin.data.CheckinResultStatus
import com.oppo.checkin.data.CheckinState
import com.oppo.checkin.data.DiagnosticLogEntry
import com.oppo.checkin.data.ExecutionRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val TIME_ONLY_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss")

private enum class PermissionExplainType {
    ACCESSIBILITY,
    EXACT_ALARM,
    NOTIFICATION
}

@Composable
fun DashboardTab(
    modifier: Modifier = Modifier,
    config: CheckinConfig,
    todayStatus: CheckinResultStatus,
    lastSuccessDate: String?,
    nextScheduledEpochMs: Long?,
    usingExactAlarm: Boolean,
    permissions: PermissionSnapshot,
    currentState: CheckinState,
    statusDetail: String,
    history: List<ExecutionRecord>,
    appCandidates: List<AppCandidate>,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdateTime: (Int, Int) -> Unit,
    onSelectApp: (AppCandidate) -> Unit,
    onTestNow: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateSettings: () -> Unit,
    onAcceptDisclosureAndOpenSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    var showDisclosureDialog by remember { mutableStateOf(false) }
    var explainPermission by remember { mutableStateOf<PermissionExplainType?>(null) }

    val latestRecord = history.firstOrNull()
    val latestTimeShort = remember(latestRecord?.timestampEpochMs) {
        latestRecord?.timestampEpochMs?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(TIME_ONLY_FORMATTER)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ZONE 1 — FLOATING HEADER
        FloatingHeaderSection(
            isEnabled = config.isEnabled,
            currentState = currentState
        )

        // DYNAMIC FLOATING ISLAND FOR LIVE EXECUTION
        DynamicExecutionIsland(
            currentState = currentState,
            statusDetail = statusDetail
        )

        // ZONE 2 — PRIMARY STATUS ISLAND (Hero visual center)
        PrimaryStatusIsland(
            config = config,
            todayStatus = todayStatus,
            currentState = currentState,
            statusDetail = statusDetail,
            lastSuccessDate = lastSuccessDate,
            latestTimeShort = latestTimeShort,
            latestFailureReason = latestRecord?.failureReason,
            onViewDetails = onNavigateHistory
        )

        // ZONE 3 — QUICK CONTROL FLOATING ISLANDS (Capsule buttons)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CapsuleGlassButton(
                label = "立即测试",
                symbol = "▶",
                accentColor = palette.accentBlueViolet,
                onClick = onTestNow,
                modifier = Modifier.weight(1f)
            )
            CapsuleGlassButton(
                label = "执行记录",
                symbol = "🕒",
                onClick = onNavigateHistory,
                modifier = Modifier.weight(1f)
            )
            CapsuleGlassButton(
                label = "任务设置",
                symbol = "⚙",
                onClick = onNavigateSettings,
                modifier = Modifier.weight(1f)
            )
        }

        // ZONE 4 — AUTOMATION PROGRESS TIMELINE & ENVIRONMENT BAR
        AutomationTimelineIsland(
            currentState = currentState,
            todayStatus = todayStatus
        )

        AutomationEnvironmentIsland(
            config = config,
            permissions = permissions,
            usingExactAlarm = usingExactAlarm,
            onTapAccessibility = {
                if (config.accessibilityDisclosureAccepted) {
                    explainPermission = PermissionExplainType.ACCESSIBILITY
                } else {
                    showDisclosureDialog = true
                }
            },
            onTapExactAlarm = { explainPermission = PermissionExplainType.EXACT_ALARM },
            onTapNotification = { explainPermission = PermissionExplainType.NOTIFICATION }
        )

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (showDisclosureDialog) {
        AccessibilityDisclosureDialog(
            onConfirm = {
                showDisclosureDialog = false
                onAcceptDisclosureAndOpenSettings()
            },
            onDismiss = { showDisclosureDialog = false }
        )
    }

    explainPermission?.let { type ->
        PermissionExplanationDialog(
            type = type,
            onDismiss = { explainPermission = null },
            onConfirm = {
                explainPermission = null
                when (type) {
                    PermissionExplainType.ACCESSIBILITY -> {
                        if (config.accessibilityDisclosureAccepted) {
                            onOpenAccessibilitySettings()
                        } else {
                            showDisclosureDialog = true
                        }
                    }
                    PermissionExplainType.EXACT_ALARM -> onOpenExactAlarmSettings()
                    PermissionExplainType.NOTIFICATION -> {
                        if (!permissions.notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            onRequestNotificationPermission()
                        } else {
                            onOpenNotificationSettings()
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun FloatingHeaderSection(
    isEnabled: Boolean,
    currentState: CheckinState
) {
    val palette = LocalLiquidGlassPalette.current
    val statusText = when {
        currentState != CheckinState.IDLE -> "● 正在执行自动签到"
        isEnabled -> "● 自动任务已开启"
        else -> "● 等待下一次执行"
    }
    val dotColor = when {
        currentState != CheckinState.IDLE -> palette.accentBlueViolet
        isEnabled -> palette.calmMintSuccess
        else -> palette.textMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "OPPO 每日签到",
                style = MaterialTheme.typography.headlineMedium,
                color = palette.textPrimary
            )
            Text(
                text = "智能签到助手",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
        }

        FloatingGlassIsland(
            elevation = GlassElevation.CAPSULE_CONTROL,
            cornerRadius = 999.dp,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            AnimatedContent(
                targetState = statusText,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(160)) },
                label = "headerStatusPill"
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = dotColor
                )
            }
        }
    }
}

@Composable
private fun PrimaryStatusIsland(
    config: CheckinConfig,
    todayStatus: CheckinResultStatus,
    currentState: CheckinState,
    statusDetail: String,
    lastSuccessDate: String?,
    latestTimeShort: String?,
    latestFailureReason: String?,
    onViewDetails: () -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    val isRunning = currentState != CheckinState.IDLE

    val glowColor = when {
        isRunning -> palette.accentBlueViolet
        todayStatus == CheckinResultStatus.SUCCESS ||
            todayStatus == CheckinResultStatus.ALREADY_COMPLETED -> palette.calmMintSuccess
        todayStatus == CheckinResultStatus.NEEDS_USER_ACTION ||
            todayStatus == CheckinResultStatus.FAILED ||
            todayStatus == CheckinResultStatus.UNKNOWN ||
            todayStatus == CheckinResultStatus.PERMISSION_MISSING -> palette.warmAmberWarning
        else -> palette.accentBlueViolet.copy(alpha = 0.45f)
    }

    val pulseTransition = rememberInfiniteTransition(label = "heroPulse")
    val orbScale by pulseTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbScale"
    )

    FloatingGlassIsland(
        modifier = Modifier.fillMaxWidth(),
        elevation = GlassElevation.PRIMARY_ISLAND,
        cornerRadius = 32.dp,
        accentGlow = glowColor,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val symbol = when {
                isRunning -> "◉"
                todayStatus == CheckinResultStatus.SUCCESS ||
                    todayStatus == CheckinResultStatus.ALREADY_COMPLETED -> "✓"
                todayStatus == CheckinResultStatus.NEEDS_USER_ACTION ||
                    todayStatus == CheckinResultStatus.FAILED ||
                    todayStatus == CheckinResultStatus.UNKNOWN ||
                    todayStatus == CheckinResultStatus.PERMISSION_MISSING -> "!"
                else -> "☀"
            }

            Box(
                modifier = Modifier
                    .size((52 * if (isRunning) orbScale else 1f).dp)
                    .clip(CircleShape)
                    .background(glowColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = symbol,
                    fontSize = 24.sp,
                    color = glowColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val headline = when {
                isRunning -> "正在执行签到"
                todayStatus == CheckinResultStatus.SUCCESS -> "今日签到成功"
                todayStatus == CheckinResultStatus.ALREADY_COMPLETED -> "今日已签到"
                todayStatus == CheckinResultStatus.NEEDS_USER_ACTION ||
                    todayStatus == CheckinResultStatus.FAILED ||
                    todayStatus == CheckinResultStatus.UNKNOWN ||
                    todayStatus == CheckinResultStatus.PERMISSION_MISSING -> "签到需要你的注意"
                else -> String.format("明天 %02d:%02d", config.scheduleHour, config.scheduleMinute)
            }

            val overline = when {
                isRunning -> "实时进度"
                todayStatus == CheckinResultStatus.WAITING -> "下一次签到"
                else -> "今日执行状态"
            }

            val subtitle = when {
                isRunning -> statusDetail.ifBlank { "正在进入积分商城…" }
                todayStatus == CheckinResultStatus.SUCCESS ->
                    "${latestTimeShort ?: "今日"} 完成 · 上次记录 ${lastSuccessDate ?: "今日"}"
                todayStatus == CheckinResultStatus.ALREADY_COMPLETED ->
                    "无需重复操作"
                todayStatus == CheckinResultStatus.NEEDS_USER_ACTION ||
                    todayStatus == CheckinResultStatus.FAILED ||
                    todayStatus == CheckinResultStatus.UNKNOWN ||
                    todayStatus == CheckinResultStatus.PERMISSION_MISSING ->
                    latestFailureReason ?: "无法确认签到结果"
                config.isEnabled -> "自动执行已准备就绪"
                else -> "定时开关未开启 · 点击下方任务设置可启用"
            }

            Text(
                text = overline,
                style = MaterialTheme.typography.labelMedium,
                color = palette.textMuted
            )

            AnimatedContent(
                targetState = headline,
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
                label = "heroHeadline"
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineLarge,
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center
                )
            }

            AnimatedContent(
                targetState = subtitle,
                transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
                label = "heroSubtitle"
            ) { sub ->
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center
                )
            }

            if (!isRunning && (
                    todayStatus == CheckinResultStatus.NEEDS_USER_ACTION ||
                        todayStatus == CheckinResultStatus.FAILED ||
                        todayStatus == CheckinResultStatus.UNKNOWN ||
                        todayStatus == CheckinResultStatus.PERMISSION_MISSING
                    )
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                CapsuleGlassButton(
                    label = "查看详情",
                    symbol = "→",
                    accentColor = palette.warmAmberWarning,
                    onClick = onViewDetails
                )
            }
        }
    }
}

@Composable
private fun AutomationEnvironmentIsland(
    config: CheckinConfig,
    permissions: PermissionSnapshot,
    usingExactAlarm: Boolean,
    onTapAccessibility: () -> Unit,
    onTapExactAlarm: () -> Unit,
    onTapNotification: () -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    val accessibilityOk = permissions.accessibilityEnabled && config.accessibilityDisclosureAccepted
    val alarmOk = permissions.exactAlarmGranted
    val notificationOk = permissions.notificationGranted

    FloatingGlassIsland(
        modifier = Modifier.fillMaxWidth(),
        elevation = GlassElevation.SECONDARY_ISLAND,
        cornerRadius = 24.dp,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "自动化环境",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textMuted
                )
                Text(
                    text = if (config.targetAppName.isNotBlank()) config.targetAppName else "未选定目标应用",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (config.targetAppName.isNotBlank() && permissions.targetAppInstalled) {
                        palette.textSecondary
                    } else {
                        palette.warmAmberWarning
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EnvironmentChip(
                    label = if (accessibilityOk) "✓ 无障碍服务" else "⚠ 无障碍未开启",
                    isOk = accessibilityOk,
                    onClick = onTapAccessibility,
                    modifier = Modifier.weight(1f)
                )
                EnvironmentChip(
                    label = if (alarmOk) "✓ 定时任务" else "⚠ 精确闹钟未授权",
                    isOk = alarmOk,
                    onClick = onTapExactAlarm,
                    modifier = Modifier.weight(1f)
                )
                EnvironmentChip(
                    label = if (notificationOk) "✓ 通知提醒" else "⚠ 通知未授权",
                    isOk = notificationOk,
                    onClick = onTapNotification,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EnvironmentChip(
    label: String,
    isOk: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLiquidGlassPalette.current
    val statusColor = if (isOk) palette.calmMintSuccess else palette.warmAmberWarning

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(palette.secondaryGlassSurface)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            color = statusColor,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun HistoryTab(
    modifier: Modifier = Modifier,
    history: List<ExecutionRecord>,
    onClearAllData: () -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "执行记录",
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.textPrimary
                )
                Text(
                    text = "近期共 ${history.size} 条本地签到记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
            }
            CapsuleGlassButton(
                label = "清除本地数据",
                symbol = "✕",
                accentColor = palette.warmAmberWarning,
                onClick = { showClearConfirm = true }
            )
        }

        if (history.isEmpty()) {
            FloatingGlassIsland(
                modifier = Modifier.fillMaxWidth(),
                elevation = GlassElevation.SECONDARY_ISLAND
            ) {
                Text(
                    text = "暂无执行记录。点击概览页“立即测试”或等待明日定时触发后将在此展示。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(history, key = { it.id }) { record ->
                    HistoryGlassCard(record)
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清除所有本地记录与设置") },
            text = { Text("此操作将清空所有执行历史、已选应用、定时设置与自定义标签，并关闭定时闹钟。是否继续？") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllData()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun HistoryGlassCard(record: ExecutionRecord) {
    val palette = LocalLiquidGlassPalette.current
    val timeText = remember(record.timestampEpochMs) {
        Instant.ofEpochMilli(record.timestampEpochMs)
            .atZone(ZoneId.systemDefault())
            .format(DATE_TIME_FORMATTER)
    }
    val statusColor = when (record.status) {
        CheckinResultStatus.SUCCESS,
        CheckinResultStatus.ALREADY_COMPLETED -> palette.calmMintSuccess
        CheckinResultStatus.NEEDS_USER_ACTION,
        CheckinResultStatus.FAILED,
        CheckinResultStatus.UNKNOWN,
        CheckinResultStatus.PERMISSION_MISSING -> palette.warmAmberWarning
        else -> palette.accentBlueViolet
    }

    FloatingGlassIsland(
        modifier = Modifier.fillMaxWidth(),
        elevation = GlassElevation.SECONDARY_ISLAND,
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.status.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${record.triggerSource} · $timeText",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textMuted
                )
            }
            Text(
                text = "启动应用：${if (record.appLaunched) "是" else "否"}  ·  " +
                    "定位按钮：${if (record.buttonLocated) "是" else "否"}  ·  " +
                    "验证结果：${if (record.resultVerified) "是" else "否"}",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
            if (!record.failureReason.isNullOrBlank()) {
                Text(
                    text = record.failureReason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAndGuideTab(
    modifier: Modifier = Modifier,
    config: CheckinConfig,
    permissions: PermissionSnapshot,
    usingExactAlarm: Boolean,
    appCandidates: List<AppCandidate>,
    diagnosticLogs: List<DiagnosticLogEntry>,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdateTime: (Int, Int) -> Unit,
    onSelectApp: (AppCandidate) -> Unit,
    onToggleDebugTestMode: (Boolean) -> Unit,
    onSaveLabelConfig: (String, String, String, String, String, Int) -> Unit,
    onAcceptDisclosureAndOpenSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppDetailsSettings: () -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    var showTimePicker by remember { mutableStateOf(false) }
    var showAppSelector by remember { mutableStateOf(false) }
    var showDisclosureDialog by remember { mutableStateOf(false) }
    var showAdvancedLabels by remember { mutableStateOf(false) }
    var explainPermission by remember { mutableStateOf<PermissionExplainType?>(null) }

    var membershipText by remember(config.labelConfig) {
        mutableStateOf(config.labelConfig.membershipLabels.joinToString(", "))
    }
    var pointsText by remember(config.labelConfig) {
        mutableStateOf(config.labelConfig.pointsPageLabels.joinToString(", "))
    }
    var checkinText by remember(config.labelConfig) {
        mutableStateOf(config.labelConfig.checkinButtonLabels.joinToString(", "))
    }
    var alreadyText by remember(config.labelConfig) {
        mutableStateOf(config.labelConfig.alreadyCheckedInLabels.joinToString(", "))
    }
    var successText by remember(config.labelConfig) {
        mutableStateOf(config.labelConfig.successConfirmationLabels.joinToString(", "))
    }
    var timeoutValue by remember(config.labelConfig) {
        mutableFloatStateOf(config.labelConfig.stageTimeoutSeconds.toFloat())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "任务设置",
                style = MaterialTheme.typography.headlineMedium,
                color = palette.textPrimary
            )
            Text(
                text = "管理定时计划、自动化权限与目标应用",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
        }

        // GROUP 1: 签到任务
        FloatingGlassIsland(
            modifier = Modifier.fillMaxWidth(),
            elevation = GlassElevation.SECONDARY_ISLAND,
            cornerRadius = 26.dp
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("签到任务", style = MaterialTheme.typography.labelMedium, color = palette.textMuted)

                GlassSettingRow(
                    title = "每日执行时间",
                    value = String.format("%02d:%02d", config.scheduleHour, config.scheduleMinute),
                    valueColor = palette.accentBlueViolet,
                    onClick = { showTimePicker = true }
                )

                HorizontalDivider(color = palette.secondaryGlassBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("自动签到", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                        Text(
                            text = if (config.isEnabled) "已开启" else "未开启",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (config.isEnabled) palette.calmMintSuccess else palette.textMuted
                        )
                    }
                    Switch(
                        checked = config.isEnabled,
                        onCheckedChange = onToggleEnabled
                    )
                }

                if (config.isEnabled && !usingExactAlarm) {
                    Text(
                        text = "提示：精确闹钟未授权，当前使用非精确回退调度，触发时间可能有轻微延迟。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.warmAmberWarning
                    )
                }
            }
        }

        // GROUP 2: 自动化权限
        FloatingGlassIsland(
            modifier = Modifier.fillMaxWidth(),
            elevation = GlassElevation.SECONDARY_ISLAND,
            cornerRadius = 26.dp
        ) {
            val accOk = permissions.accessibilityEnabled && config.accessibilityDisclosureAccepted
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("自动化权限", style = MaterialTheme.typography.labelMedium, color = palette.textMuted)

                GlassSettingRow(
                    title = "无障碍服务",
                    value = if (accOk) "已授权" else "未开启",
                    valueColor = if (accOk) palette.calmMintSuccess else palette.warmAmberWarning,
                    onClick = {
                        if (config.accessibilityDisclosureAccepted) {
                            explainPermission = PermissionExplainType.ACCESSIBILITY
                        } else {
                            showDisclosureDialog = true
                        }
                    }
                )

                HorizontalDivider(color = palette.secondaryGlassBorder)

                GlassSettingRow(
                    title = "精确闹钟",
                    value = if (permissions.exactAlarmGranted) "已授权" else "未授权",
                    valueColor = if (permissions.exactAlarmGranted) palette.calmMintSuccess else palette.warmAmberWarning,
                    onClick = { explainPermission = PermissionExplainType.EXACT_ALARM }
                )

                HorizontalDivider(color = palette.secondaryGlassBorder)

                GlassSettingRow(
                    title = "通知权限",
                    value = if (permissions.notificationGranted) "已授权" else "未授权",
                    valueColor = if (permissions.notificationGranted) palette.calmMintSuccess else palette.warmAmberWarning,
                    onClick = { explainPermission = PermissionExplainType.NOTIFICATION }
                )
            }
        }

        // GROUP 3: 应用管理
        FloatingGlassIsland(
            modifier = Modifier.fillMaxWidth(),
            elevation = GlassElevation.SECONDARY_ISLAND,
            cornerRadius = 26.dp
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("应用管理", style = MaterialTheme.typography.labelMedium, color = palette.textMuted)

                GlassSettingRow(
                    title = "目标应用",
                    value = if (config.targetAppName.isNotBlank()) config.targetAppName else "点击选择 OPPO 商城",
                    valueColor = if (config.targetAppName.isNotBlank() && permissions.targetAppInstalled) {
                        palette.textPrimary
                    } else {
                        palette.warmAmberWarning
                    },
                    onClick = { showAppSelector = true }
                )

                if (config.targetPackageName.isNotBlank() && !permissions.targetAppInstalled) {
                    Text(
                        text = "未检测到 OPPO 商城，请重新选择应用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.warmAmberWarning
                    )
                }

                HorizontalDivider(color = palette.secondaryGlassBorder)

                GlassSettingRow(
                    title = "ColorOS 后台与自启动设置",
                    value = "打开应用详情",
                    valueColor = palette.accentBlueViolet,
                    onClick = onOpenAppDetailsSettings
                )

                Text(
                    text = "建议在系统设置中开启“允许完全后台行为”与“允许自启动”，并将电池优化设为“不优化”。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
            }
        }

        // GROUP 4: 诊断与高级界面标签配置
        FloatingGlassIsland(
            modifier = Modifier.fillMaxWidth(),
            elevation = GlassElevation.SECONDARY_ISLAND,
            cornerRadius = 26.dp
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("路径验证诊断模式", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                        Text(
                            text = "记录每个状态的节点锚点与校验结果",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                    }
                    Switch(
                        checked = config.debugTestModeEnabled,
                        onCheckedChange = onToggleDebugTestMode
                    )
                }

                if (config.debugTestModeEnabled) {
                    if (diagnosticLogs.isEmpty()) {
                        Text(
                            text = "暂无诊断记录，点击概览页“立即测试”后将显示校验详情。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textMuted
                        )
                    } else {
                        diagnosticLogs.take(8).forEach { entry ->
                            HorizontalDivider(color = palette.secondaryGlassBorder)
                            Text(
                                text = "STATE: ${entry.state.name} (${entry.durationMs}ms)\n" +
                                    "锚点: ${entry.detectedAnchor ?: "无"} · 按钮: ${entry.buttonText ?: "无"} · 卡片校验: ${entry.hierarchyValidated ?: "N/A"}\n" +
                                    "结论: ${entry.note}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textSecondary
                            )
                        }
                    }
                }

                HorizontalDivider(color = palette.secondaryGlassBorder)

                GlassSettingRow(
                    title = "自定义界面识别标签与超时",
                    value = if (showAdvancedLabels) "收起" else "展开",
                    valueColor = palette.accentBlueViolet,
                    onClick = { showAdvancedLabels = !showAdvancedLabels }
                )

                if (showAdvancedLabels) {
                    OutlinedTextField(
                        value = membershipText,
                        onValueChange = { membershipText = it },
                        label = { Text("会员标签") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pointsText,
                        onValueChange = { pointsText = it },
                        label = { Text("积分入口标签") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = checkinText,
                        onValueChange = { checkinText = it },
                        label = { Text("签到按钮标签") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = alreadyText,
                        onValueChange = { alreadyText = it },
                        label = { Text("已签到按钮标签") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = successText,
                        onValueChange = { successText = it },
                        label = { Text("签到成功提示标签") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "单阶段超时时间：${timeoutValue.toInt()} 秒",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                    Slider(
                        value = timeoutValue,
                        onValueChange = { timeoutValue = it },
                        valueRange = 5f..45f,
                        steps = 7
                    )
                    CapsuleGlassButton(
                        label = "保存标签配置",
                        symbol = "✓",
                        accentColor = palette.calmMintSuccess,
                        onClick = {
                            onSaveLabelConfig(
                                membershipText,
                                pointsText,
                                checkinText,
                                alreadyText,
                                successText,
                                timeoutValue.toInt()
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = config.scheduleHour,
            initialMinute = config.scheduleMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("设置每日执行时间") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateTime(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showAppSelector) {
        AppSelectorDialog(
            candidates = appCandidates,
            currentPackage = config.targetPackageName,
            onSelect = {
                onSelectApp(it)
                showAppSelector = false
            },
            onDismiss = { showAppSelector = false }
        )
    }

    if (showDisclosureDialog) {
        AccessibilityDisclosureDialog(
            onConfirm = {
                showDisclosureDialog = false
                onAcceptDisclosureAndOpenSettings()
            },
            onDismiss = { showDisclosureDialog = false }
        )
    }

    explainPermission?.let { type ->
        PermissionExplanationDialog(
            type = type,
            onDismiss = { explainPermission = null },
            onConfirm = {
                explainPermission = null
                when (type) {
                    PermissionExplainType.ACCESSIBILITY -> {
                        if (config.accessibilityDisclosureAccepted) {
                            onOpenAccessibilitySettings()
                        } else {
                            showDisclosureDialog = true
                        }
                    }
                    PermissionExplainType.EXACT_ALARM -> onOpenExactAlarmSettings()
                    PermissionExplainType.NOTIFICATION -> {
                        if (!permissions.notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            onRequestNotificationPermission()
                        } else {
                            onOpenNotificationSettings()
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun GlassSettingRow(
    title: String,
    value: String,
    valueColor: Color,
    onClick: () -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = palette.textPrimary
        )
        Text(
            text = "$value  ›",
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AccessibilityDisclosureDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("无障碍服务隐私与用途声明") },
        text = {
            Text(
                "在开启无障碍服务前，请您确认以下说明：\n\n" +
                    "1. 本服务仅读取您在应用内选定的单个 OPPO 商城应用的可见界面标签。\n" +
                    "2. 唯一用途为自动完成每日会员积分签到（进入“会员”→“我的积分”→点击一次“签到”并验证结果）。\n" +
                    "3. 不会读取密码、短信或任何私人消息。\n" +
                    "4. 不会截取屏幕图像。\n" +
                    "5. 本应用无联网权限，不会向任何服务器上传数据。\n" +
                    "6. 运行时严格校验包名，绝不与无关应用交互。"
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("同意并前往设置")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun PermissionExplanationDialog(
    type: PermissionExplainType,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val (title, body) = when (type) {
        PermissionExplainType.ACCESSIBILITY -> "关于无障碍服务权限" to (
            "• 为什么需要：用于在 OPPO 商城前台打开时识别“会员”、“我的积分”和“签到”按钮。\n" +
                "• 权限边界：严格锁定在您选定的 OPPO 商城包名内，绝不读取任何其他应用内容。\n" +
                "• 如何开启：点击下方按钮跳转系统“无障碍 → 已下载的应用”，开启“OPPO 每日签到助手”。"
            )
        PermissionExplainType.EXACT_ALARM -> "关于精确闹钟权限" to (
            "• 为什么需要：确保每天在您设定的时间（如 09:00）准时唤醒签到任务。\n" +
                "• 权限边界：仅用于每日一次的本地定时触发，不消耗额外后台电量。\n" +
                "• 如何开启：点击下方按钮前往系统“闹钟与提醒”页面并开启授权。"
            )
        PermissionExplainType.NOTIFICATION -> "关于通知提醒权限" to (
            "• 为什么需要：当手机处于锁屏状态或系统限制后台启动时，向您发送“Start Check-in”一键签到通知，并汇报每日签到结果。\n" +
                "• 权限边界：无任何推广或无关推送。\n" +
                "• 如何开启：点击下方按钮授权系统通知权限。"
            )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("前往开启")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后")
            }
        }
    )
}

@Composable
private fun AppSelectorDialog(
    candidates: List<AppCandidate>,
    currentPackage: String,
    onSelect: (AppCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择目标 OPPO 商城应用") },
        text = {
            if (candidates.isEmpty()) {
                Text("未发现可启动的外部应用。")
            } else {
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(candidates, key = { it.packageName }) { item ->
                        val bitmap = remember(item.packageName) {
                            AppDiscovery.loadAppIcon(context, item.packageName)?.toBitmapOrNull()
                        }
                        Surface(
                            color = if (item.packageName == currentPackage) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item) }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = item.appName,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (item.isLikelyOppoApp) "${item.appName}（推荐）" else item.appName,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(item.packageName, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

private fun Drawable.toBitmapOrNull(): Bitmap? {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val width = intrinsicWidth.takeIf { it > 0 } ?: 64
    val height = intrinsicHeight.takeIf { it > 0 } ?: 64
    return try {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bmp
    } catch (_: Exception) {
        null
    }
}
