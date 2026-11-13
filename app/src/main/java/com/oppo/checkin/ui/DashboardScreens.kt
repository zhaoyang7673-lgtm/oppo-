package com.oppo.checkin.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oppo.checkin.data.AppCandidate
import com.oppo.checkin.data.CheckinConfig
import com.oppo.checkin.data.CheckinResultStatus
import com.oppo.checkin.data.CheckinState
import com.oppo.checkin.data.ExecutionRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

@OptIn(ExperimentalMaterial3Api::class)
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
    appCandidates: List<AppCandidate>,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdateTime: (Int, Int) -> Unit,
    onSelectApp: (AppCandidate) -> Unit,
    onTestNow: () -> Unit,
    onAcceptDisclosureAndOpenSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }
    var showAppSelector by remember { mutableStateOf(false) }
    var showDisclosureDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Automation Status & Today's Result Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (config.isEnabled) "自动签到已开启" else "自动签到未开启",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "今日状态：${todayStatus.label}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = config.isEnabled,
                        onCheckedChange = onToggleEnabled
                    )
                }

                Text(
                    text = "上次成功签到日期：${lastSuccessDate ?: "暂无记录"}",
                    style = MaterialTheme.typography.bodySmall
                )

                if (currentState != CheckinState.IDLE && statusDetail.isNotBlank()) {
                    Text(
                        text = "当前引擎：${currentState.name} — $statusDetail",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 2. Target App & Schedule Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("目标应用与定时计划", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                val appMissingWarning = config.targetPackageName.isNotBlank() && !permissions.targetAppInstalled
                if (appMissingWarning) {
                    Text(
                        text = "未检测到 OPPO 商城，请重新选择应用",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("目标应用", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = if (config.targetAppName.isNotBlank()) {
                                "${config.targetAppName} (${config.targetPackageName})"
                            } else {
                                "尚未选择（请点击右侧选择 OPPO 商城）"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    OutlinedButton(onClick = { showAppSelector = true }) {
                        Text("选择应用")
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("每日执行时间", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = String.format("%02d:%02d（本地时区）", config.scheduleHour, config.scheduleMinute),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    OutlinedButton(onClick = { showTimePicker = true }) {
                        Text("修改时间")
                    }
                }

                val nextText = nextScheduledEpochMs?.let {
                    val dt = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                    dt.format(DATE_TIME_FORMATTER)
                } ?: "未安排"
                Text("下次预计执行：$nextText", style = MaterialTheme.typography.bodySmall)

                if (config.isEnabled && !usingExactAlarm) {
                    Text(
                        text = "提示：当前未获得“精确闹钟”权限，已自动采用非精确调度回退方案，实际触发时间可能会有所延迟。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // 3. Permissions Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("权限状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                PermissionRow(
                    title = "无障碍服务（仅限所选 OPPO 应用）",
                    granted = permissions.accessibilityEnabled && config.accessibilityDisclosureAccepted,
                    buttonText = "前往设置",
                    onClick = {
                        if (config.accessibilityDisclosureAccepted) {
                            onOpenAccessibilitySettings()
                        } else {
                            showDisclosureDialog = true
                        }
                    }
                )

                PermissionRow(
                    title = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "闹钟与提醒（精确调度）"
                    } else {
                        "闹钟与提醒（系统默认支持）"
                    },
                    granted = permissions.exactAlarmGranted,
                    buttonText = "授权设置",
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    onClick = onOpenExactAlarmSettings
                )

                PermissionRow(
                    title = "通知权限（锁屏/受限回退提醒）",
                    granted = permissions.notificationGranted,
                    buttonText = "开启通知",
                    onClick = {
                        if (!permissions.notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            onRequestNotificationPermission()
                        } else {
                            onOpenNotificationSettings()
                        }
                    }
                )
            }
        }

        // 4. Manual Test Button
        Button(
            onClick = onTestNow,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("立即测试签到", style = MaterialTheme.typography.titleMedium)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = config.scheduleHour,
            initialMinute = config.scheduleMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("设置每日签到时间") },
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
        AlertDialog(
            onDismissRequest = { showDisclosureDialog = false },
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
                Button(onClick = {
                    showDisclosureDialog = false
                    onAcceptDisclosureAndOpenSettings()
                }) {
                    Text("同意并前往设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosureDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    buttonText: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (granted) "已授权" else "未开启",
                color = if (granted) Color(0xFF046A38) else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (enabled) {
            OutlinedButton(onClick = onClick) {
                Text(buttonText)
            }
        }
    }
}

@Composable
fun HistoryTab(
    modifier: Modifier = Modifier,
    history: List<ExecutionRecord>,
    onClearAllData: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("近期执行记录（${history.size}）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = { showClearConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("清除本地数据")
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        if (history.isEmpty()) {
            BoxEmptyMessage("暂无执行记录。您可以点击“立即测试签到”或等待定时任务触发。")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history, key = { it.id }) { record ->
                    HistoryRecordCard(record)
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
private fun HistoryRecordCard(record: ExecutionRecord) {
    val timeText = remember(record.timestampEpochMs) {
        Instant.ofEpochMilli(record.timestampEpochMs)
            .atZone(ZoneId.systemDefault())
            .format(DATE_TIME_FORMATTER)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(record.status.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text("${record.triggerSource} · $timeText", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = "启动应用：${if (record.appLaunched) "是" else "否"} | " +
                    "定位按钮：${if (record.buttonLocated) "是" else "否"} | " +
                    "验证结果：${if (record.resultVerified) "是" else "否"}",
                style = MaterialTheme.typography.bodySmall
            )
            if (!record.failureReason.isNullOrBlank()) {
                Text("说明：${record.failureReason}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun SettingsAndGuideTab(
    modifier: Modifier = Modifier,
    config: CheckinConfig,
    appCandidates: List<AppCandidate>,
    onSelectApp: (AppCandidate) -> Unit,
    onSaveLabelConfig: (String, String, String, String, String, Int) -> Unit,
    onOpenAppDetailsSettings: () -> Unit
) {
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
    var showAppSelector by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("界面识别标签配置（逗号分隔）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    label = { Text("已签到状态标签") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = successText,
                    onValueChange = { successText = it },
                    label = { Text("签到成功确认标签") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("单阶段超时时间：${timeoutValue.toInt()} 秒", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = timeoutValue,
                    onValueChange = { timeoutValue = it },
                    valueRange = 5f..45f,
                    steps = 7
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        onSaveLabelConfig(
                            membershipText,
                            pointsText,
                            checkinText,
                            alreadyText,
                            successText,
                            timeoutValue.toInt()
                        )
                    }) {
                        Text("保存标签配置")
                    }
                    OutlinedButton(onClick = { showAppSelector = true }) {
                        Text("更换目标应用")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("OPPO ColorOS 后台与电池设置指南", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "为确保定时任务准时触发并在锁屏时可靠发送通知，建议按以下步骤手动配置系统设置：\n\n" +
                        "1. 允许后台运行：系统设置 → 应用管理 → 找到“OPPO 每日签到助手” → 耗电管理 → 开启“允许完全后台行为”。\n" +
                        "2. 允许自启动：应用详情 → 权限/自启动管理 → 开启“允许自启动”，以便重启后自动恢复定时闹钟。\n" +
                        "3. 允许通知：通知管理 → 开启“允许通知”及“每日签到提醒”渠道，确保锁屏回退通知可见。\n" +
                        "4. 电池优化：在系统“电池优化”列表中，将本应用设为“不优化”或避免激进省电策略将其冻结。\n" +
                        "5. 注意：Android 10+ 限制后台直接拉起其他应用；若设备处于锁屏或系统限制后台启动，本助手会自动发送通知，点击“Start Check-in”即可一键完成。",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = onOpenAppDetailsSettings) {
                    Text("打开本应用系统详情页")
                }
            }
        }
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
                                    Text(item.packageName, style = MaterialTheme.typography.bodySmall)
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

@Composable
private fun BoxEmptyMessage(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
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
