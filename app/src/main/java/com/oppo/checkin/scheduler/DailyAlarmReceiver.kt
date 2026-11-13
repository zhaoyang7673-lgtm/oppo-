package com.oppo.checkin.scheduler

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import com.oppo.checkin.CheckinApplication
import com.oppo.checkin.data.CheckinResultStatus
import com.oppo.checkin.service.CheckinAccessibilityService
import java.time.ZonedDateTime

class DailyAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val container = CheckinApplication.getContainer(context)
        val repository = container.repository
        val alarmScheduler = container.alarmScheduler
        val notificationHelper = container.notificationHelper
        val stateMachine = container.stateMachine

        // Always schedule the next one-shot daily alarm first (strictly after current moment).
        alarmScheduler.rescheduleIfEnabled(ZonedDateTime.now().plusSeconds(1))

        val config = repository.config.value
        if (!config.isEnabled) return

        // Idempotency guard: do not run again if today's check-in already succeeded.
        if (repository.hasCompletedToday()) {
            return
        }

        if (config.targetPackageName.isBlank()) {
            repository.recordExecution(
                status = CheckinResultStatus.PERMISSION_MISSING,
                appLaunched = false,
                buttonLocated = false,
                resultVerified = false,
                failureReason = "尚未选择目标 OPPO 商城应用",
                triggerSource = "定时"
            )
            notificationHelper.showResultNotification(
                CheckinResultStatus.PERMISSION_MISSING,
                "尚未选择目标 OPPO 商城应用，请在主界面选择后重试。"
            )
            return
        }

        if (!isPackageInstalled(context, config.targetPackageName)) {
            repository.recordExecution(
                status = CheckinResultStatus.FAILED,
                appLaunched = false,
                buttonLocated = false,
                resultVerified = false,
                failureReason = "未检测到 OPPO 商城，请重新选择应用",
                triggerSource = "定时"
            )
            notificationHelper.showResultNotification(
                CheckinResultStatus.FAILED,
                "未检测到 OPPO 商城，请重新选择应用"
            )
            return
        }

        if (!config.accessibilityDisclosureAccepted ||
            !CheckinAccessibilityService.isServiceEnabled(context)
        ) {
            repository.recordExecution(
                status = CheckinResultStatus.PERMISSION_MISSING,
                appLaunched = false,
                buttonLocated = false,
                resultVerified = false,
                failureReason = "无障碍服务未开启或未同意隐私声明",
                triggerSource = "定时"
            )
            notificationHelper.showManualFallbackNotification("无障碍服务未开启，点击通知前往签到")
            return
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        val isInteractive = powerManager.isInteractive
        val isLocked = keyguardManager.isKeyguardLocked || keyguardManager.isDeviceLocked

        // Path B: Device locked or screen off -> never bypass lock screen; send safe notification.
        if (!isInteractive || isLocked) {
            repository.recordExecution(
                status = CheckinResultStatus.NEEDS_USER_ACTION,
                appLaunched = false,
                buttonLocated = false,
                resultVerified = false,
                failureReason = "设备处于锁屏或息屏状态，已发送手动签到通知",
                triggerSource = "定时"
            )
            notificationHelper.showManualFallbackNotification("设备处于锁屏或息屏状态，需要手动点击启动")
            return
        }

        // Path A: Best-effort automatic launch via supported OS APIs when unlocked & interactive.
        val launchIntent = context.packageManager.getLaunchIntentForPackage(config.targetPackageName)
        if (launchIntent == null) {
            repository.recordExecution(
                status = CheckinResultStatus.FAILED,
                appLaunched = false,
                buttonLocated = false,
                resultVerified = false,
                failureReason = "无法获取目标应用的启动入口",
                triggerSource = "定时"
            )
            notificationHelper.showManualFallbackNotification("无法获取应用启动入口")
            return
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            stateMachine.startSession(
                targetPackage = config.targetPackageName,
                labelConfig = config.labelConfig,
                triggerSource = "定时"
            )
            context.startActivity(launchIntent)
        } catch (_: SecurityException) {
            stateMachine.abortToManualFallback("系统后台启动受限，已发送手动签到通知")
        } catch (_: Exception) {
            stateMachine.abortToManualFallback("后台无法启动目标应用，已发送手动签到通知")
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
