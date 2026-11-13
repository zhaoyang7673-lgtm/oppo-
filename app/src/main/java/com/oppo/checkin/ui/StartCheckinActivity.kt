package com.oppo.checkin.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.oppo.checkin.CheckinApplication
import com.oppo.checkin.data.CheckinResultStatus
import com.oppo.checkin.service.CheckinAccessibilityService

/**
 * Trampoline Activity launched when the user taps the Path B fallback notification
 * or its "Start Check-in" action button.
 */
class StartCheckinActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = CheckinApplication.getContainer(this)
        val repository = container.repository
        val stateMachine = container.stateMachine
        val config = repository.config.value

        if (config.targetPackageName.isBlank() ||
            !AppDiscovery.isPackageInstalled(this, config.targetPackageName)
        ) {
            Toast.makeText(this, "未检测到 OPPO 商城，请重新选择应用", Toast.LENGTH_LONG).show()
            openMainActivity()
            finish()
            return
        }

        if (!config.accessibilityDisclosureAccepted ||
            !CheckinAccessibilityService.isServiceEnabled(this)
        ) {
            Toast.makeText(this, "请先在助手中开启无障碍服务", Toast.LENGTH_LONG).show()
            openMainActivity()
            finish()
            return
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(config.targetPackageName)
        if (launchIntent == null) {
            repository.recordExecution(
                status = CheckinResultStatus.FAILED,
                appLaunched = false,
                buttonLocated = false,
                resultVerified = false,
                failureReason = "无法获取目标应用启动入口",
                triggerSource = "通知手动启动"
            )
            openMainActivity()
            finish()
            return
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        stateMachine.startSession(
            targetPackage = config.targetPackageName,
            labelConfig = config.labelConfig,
            triggerSource = "通知手动启动"
        )
        startActivity(launchIntent)
        finish()
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }
}
