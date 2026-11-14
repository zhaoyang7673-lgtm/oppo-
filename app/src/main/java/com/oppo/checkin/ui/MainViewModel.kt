package com.oppo.checkin.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.oppo.checkin.CheckinApplication
import com.oppo.checkin.data.AppCandidate
import com.oppo.checkin.data.CheckinConfig
import com.oppo.checkin.data.CheckinRepository
import com.oppo.checkin.data.CheckinResultStatus
import com.oppo.checkin.data.CheckinState
import com.oppo.checkin.data.DiagnosticLogEntry
import com.oppo.checkin.data.ExecutionRecord
import com.oppo.checkin.data.UiLabelConfig
import com.oppo.checkin.service.CheckinAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionSnapshot(
    val accessibilityEnabled: Boolean = false,
    val exactAlarmGranted: Boolean = false,
    val notificationGranted: Boolean = false,
    val targetAppInstalled: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = CheckinApplication.getContainer(application)
    private val repository = container.repository
    private val alarmScheduler = container.alarmScheduler
    private val notificationHelper = container.notificationHelper
    private val stateMachine = container.stateMachine

    val config: StateFlow<CheckinConfig> = repository.config
    val history: StateFlow<List<ExecutionRecord>> = repository.history
    val diagnosticLogs: StateFlow<List<DiagnosticLogEntry>> = repository.diagnosticLogs
    val lastSuccessDate: StateFlow<String?> = repository.lastSuccessDate
    val todayStatus: StateFlow<CheckinResultStatus> = repository.todayStatus
    val nextScheduledEpochMs: StateFlow<Long?> = repository.nextScheduledEpochMs
    val usingExactAlarm: StateFlow<Boolean> = repository.usingExactAlarm
    val currentState: StateFlow<CheckinState> = stateMachine.state
    val statusDetail: StateFlow<String> = stateMachine.statusDetail

    private val _permissions = MutableStateFlow(PermissionSnapshot())
    val permissions: StateFlow<PermissionSnapshot> = _permissions.asStateFlow()

    private val _appCandidates = MutableStateFlow<List<AppCandidate>>(emptyList())
    val appCandidates: StateFlow<List<AppCandidate>> = _appCandidates.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    init {
        refreshStatus()
        refreshInstalledApps()
    }

    fun refreshStatus() {
        val ctx = getApplication<Application>()
        val cfg = repository.config.value
        repository.refreshTodayStatus()
        _permissions.value = PermissionSnapshot(
            accessibilityEnabled = CheckinAccessibilityService.isServiceEnabled(ctx),
            exactAlarmGranted = alarmScheduler.canScheduleExactAlarms(),
            notificationGranted = notificationHelper.hasNotificationPermission(),
            targetAppInstalled = AppDiscovery.isPackageInstalled(ctx, cfg.targetPackageName)
        )
    }

    fun refreshInstalledApps() {
        val ctx = getApplication<Application>()
        _appCandidates.value = AppDiscovery.discoverLaunchableApps(ctx)
    }

    fun setAutomationEnabled(enabled: Boolean) {
        val cfg = repository.config.value
        if (enabled && cfg.targetPackageName.isBlank()) {
            _uiMessage.value = "请先选择目标 OPPO 商城应用"
            return
        }
        repository.updateConfig { it.copy(isEnabled = enabled) }
        if (enabled) {
            val outcome = alarmScheduler.scheduleNextDailyAlarm()
            _uiMessage.value = if (outcome.isExact) {
                "已开启每日定时签到（精确闹钟）"
            } else {
                "已开启每日定时签到（非精确回退模式，时间可能有延迟）"
            }
        } else {
            alarmScheduler.cancelDailyAlarm()
            _uiMessage.value = "已关闭每日自动签到"
        }
    }

    fun setDebugTestMode(enabled: Boolean) {
        repository.updateConfig { it.copy(debugTestModeEnabled = enabled) }
        if (!enabled) {
            repository.clearDiagnosticLogs()
        }
        _uiMessage.value = if (enabled) "已开启路径验证诊断模式" else "已关闭路径验证诊断模式"
    }

    fun updateScheduleTime(hour: Int, minute: Int) {
        repository.updateConfig {
            it.copy(scheduleHour = hour.coerceIn(0, 23), scheduleMinute = minute.coerceIn(0, 59))
        }
        if (repository.config.value.isEnabled) {
            alarmScheduler.scheduleNextDailyAlarm()
        }
        _uiMessage.value = String.format("已更新每日执行时间为 %02d:%02d", hour, minute)
    }

    fun selectTargetApp(candidate: AppCandidate) {
        repository.updateConfig {
            it.copy(
                targetPackageName = candidate.packageName,
                targetAppName = candidate.appName
            )
        }
        CheckinAccessibilityService.refreshIsolationIfConnected()
        refreshStatus()
        _uiMessage.value = "已选择目标应用：${candidate.appName}"
    }

    fun acceptAccessibilityDisclosure() {
        repository.updateConfig { it.copy(accessibilityDisclosureAccepted = true) }
    }

    fun updateLabelConfig(
        membershipCsv: String,
        pointsCsv: String,
        checkinCsv: String,
        alreadyCsv: String,
        successCsv: String,
        timeoutSeconds: Int
    ) {
        val defaults = UiLabelConfig()
        val updated = repository.config.value.labelConfig.copy(
            membershipLabels = CheckinRepository.parseCsv(membershipCsv) ?: defaults.membershipLabels,
            pointsPageLabels = CheckinRepository.parseCsv(pointsCsv) ?: defaults.pointsPageLabels,
            checkinButtonLabels = CheckinRepository.parseCsv(checkinCsv) ?: defaults.checkinButtonLabels,
            alreadyCheckedInLabels = CheckinRepository.parseCsv(alreadyCsv) ?: defaults.alreadyCheckedInLabels,
            successConfirmationLabels = CheckinRepository.parseCsv(successCsv) ?: defaults.successConfirmationLabels,
            stageTimeoutSeconds = timeoutSeconds.coerceIn(5, 45),
            maxNavigationRetries = 2
        )
        repository.updateConfig { it.copy(labelConfig = updated) }
        _uiMessage.value = "已保存界面识别标签与超时设置"
    }

    fun testAutomationNow() {
        val ctx = getApplication<Application>()
        val cfg = repository.config.value

        if (cfg.targetPackageName.isBlank()) {
            _uiMessage.value = "请先选择目标 OPPO 商城应用"
            return
        }
        if (!AppDiscovery.isPackageInstalled(ctx, cfg.targetPackageName)) {
            _uiMessage.value = "未检测到 OPPO 商城，请重新选择应用"
            refreshStatus()
            return
        }
        if (!cfg.accessibilityDisclosureAccepted || !CheckinAccessibilityService.isServiceEnabled(ctx)) {
            _uiMessage.value = "请先阅读隐私说明并开启无障碍服务"
            refreshStatus()
            return
        }

        val launchIntent = ctx.packageManager.getLaunchIntentForPackage(cfg.targetPackageName)
        if (launchIntent == null) {
            _uiMessage.value = "无法获取目标应用启动入口"
            return
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        stateMachine.startSession(
            targetPackage = cfg.targetPackageName,
            labelConfig = cfg.labelConfig,
            triggerSource = "手动测试"
        )
        ctx.startActivity(launchIntent)
    }

    fun clearAllLocalData() {
        alarmScheduler.cancelDailyAlarm()
        stateMachine.cancelSession()
        repository.clearAllLocalData()
        CheckinAccessibilityService.refreshIsolationIfConnected()
        refreshStatus()
        _uiMessage.value = "已清除所有本地记录与配置"
    }

    fun consumeUiMessage() {
        _uiMessage.value = null
    }
}
