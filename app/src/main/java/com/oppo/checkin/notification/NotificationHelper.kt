package com.oppo.checkin.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.oppo.checkin.R
import com.oppo.checkin.data.CheckinResultStatus
import com.oppo.checkin.ui.MainActivity
import com.oppo.checkin.ui.StartCheckinActivity

class NotificationHelper(private val context: Context) {

    init {
        createChannels()
    }

    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Path B safe notification fallback.
     * Never uses full-screen intent or lock-screen bypass.
     */
    fun showManualFallbackNotification(reason: String? = null) {
        if (!hasNotificationPermission()) return

        val startIntent = Intent(context, StartCheckinActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val startPendingIntent = PendingIntent.getActivity(
            context,
            REQ_START_CHECKIN,
            startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val message = "It's time for your daily OPPO points check-in."
        val expandedText = if (!reason.isNullOrBlank()) {
            "$message\n原因：$reason"
        } else {
            message
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("OPPO Daily Check-in")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(startPendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Start Check-in",
                startPendingIntent
            )
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_REMINDER, notification)
    }

    fun showResultNotification(status: CheckinResultStatus, detail: String? = null) {
        if (!hasNotificationPermission()) return

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            REQ_OPEN_DASHBOARD,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "OPPO 每日签到：${status.label}"
        val body = detail?.takeIf { it.isNotBlank() } ?: when (status) {
            CheckinResultStatus.SUCCESS -> "今日 OPPO 商城会员积分签到已完成并验证。"
            CheckinResultStatus.ALREADY_COMPLETED -> "今日已完成过签到，未重复点击。"
            CheckinResultStatus.NEEDS_USER_ACTION -> "需要您手动完成当前页面操作。"
            CheckinResultStatus.PERMISSION_MISSING -> "缺少必要权限，请打开应用检查权限状态。"
            CheckinResultStatus.UNKNOWN -> "已点击签到，但未检测到明确成功提示，请手动确认。"
            CheckinResultStatus.FAILED -> "自动签到未能完成，请打开应用查看日志。"
            else -> status.label
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_RESULT, notification)
    }

    private fun createChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDER,
            "每日签到提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "当设备锁屏或系统限制后台启动时，提醒您手动发起 OPPO 签到。"
        }
        val resultChannel = NotificationChannel(
            CHANNEL_RESULT,
            "签到结果通知",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "通知每日自动签到的执行结果或异常状态。"
        }
        manager.createNotificationChannel(reminderChannel)
        manager.createNotificationChannel(resultChannel)
    }

    companion object {
        const val CHANNEL_REMINDER = "oppo_checkin_reminder"
        const val CHANNEL_RESULT = "oppo_checkin_result"
        private const val NOTIF_ID_REMINDER = 1001
        private const val NOTIF_ID_RESULT = 1002
        private const val REQ_START_CHECKIN = 2001
        private const val REQ_OPEN_DASHBOARD = 2002
    }
}
