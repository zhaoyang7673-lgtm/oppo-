package com.oppo.checkin.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.oppo.checkin.CheckinApplication
import com.oppo.checkin.automation.StateAction
import com.oppo.checkin.automation.UiNodeSnapshot

/**
 * Strictly isolated AccessibilityService for OPPO Daily Check-in.
 * - Dynamically restricts [AccessibilityServiceInfo.packageNames] to the single user-selected OPPO package.
 * - Performs 3-point runtime package verification before reading any node or clicking.
 * - Never captures screenshots, never logs full UI trees, and never performs gesture simulation.
 */
class CheckinAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        applyPackageIsolation()
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onInterrupt() {
        // No-op: state machine handles timeouts cleanly.
    }

    fun applyPackageIsolation() {
        val container = CheckinApplication.getContainer(applicationContext)
        val configuredPkg = container.repository.config.value.targetPackageName.trim()
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.packageNames = if (configuredPkg.isNotEmpty()) {
            arrayOf(configuredPkg)
        } else {
            arrayOf(UNMATCHABLE_PACKAGE)
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val container = CheckinApplication.getContainer(applicationContext)
        val config = container.repository.config.value
        val targetPkg = config.targetPackageName.trim()
        if (targetPkg.isEmpty() || !config.accessibilityDisclosureAccepted) return

        // Runtime Check 1: Event package must strictly match the configured OPPO package.
        val eventPkg = event.packageName?.toString()
        if (eventPkg != targetPkg) return

        // Runtime Check 3: Only process events during an active check-in session for this package.
        val stateMachine = container.stateMachine
        if (!stateMachine.isActiveSessionFor(targetPkg)) return

        // Runtime Check 2: Active root window must strictly belong to the same target package.
        val rootNode = rootInActiveWindow ?: return
        if (rootNode.packageName?.toString() != targetPkg) return

        val nodeMap = ArrayList<AccessibilityNodeInfo>(64)
        val snapshot = buildSnapshot(rootNode, nodeMap, depth = 0)

        when (val action = stateMachine.processUiTree(targetPkg, snapshot)) {
            is StateAction.ClickNode -> {
                val targetNode = nodeMap.getOrNull(action.nodeId)
                if (targetNode != null &&
                    targetNode.packageName?.toString() == targetPkg &&
                    targetNode.isVisibleToUser &&
                    targetNode.isEnabled
                ) {
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }

            StateAction.None -> Unit
        }
    }

    private fun buildSnapshot(
        node: AccessibilityNodeInfo,
        nodeMap: MutableList<AccessibilityNodeInfo>,
        depth: Int
    ): UiNodeSnapshot {
        val id = nodeMap.size
        nodeMap.add(node)

        val children = if (depth < MAX_TREE_DEPTH) {
            val count = node.childCount
            buildList(count) {
                for (i in 0 until count) {
                    val child = node.getChild(i) ?: continue
                    add(buildSnapshot(child, nodeMap, depth + 1))
                }
            }
        } else {
            emptyList()
        }

        return UiNodeSnapshot(
            nodeId = id,
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            viewIdResourceName = node.viewIdResourceName,
            isClickable = node.isClickable,
            isEnabled = node.isEnabled,
            isVisibleToUser = node.isVisibleToUser,
            children = children
        )
    }

    companion object {
        private const val UNMATCHABLE_PACKAGE = "com.oppo.checkin.unconfigured.sentinel"
        private const val MAX_TREE_DEPTH = 24

        @Volatile
        private var instance: CheckinAccessibilityService? = null

        fun refreshIsolationIfConnected() {
            instance?.applyPackageIsolation()
        }

        fun isServiceEnabled(context: Context): Boolean {
            val expectedComponent = ComponentName(context, CheckinAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            return enabledServices.split(":")
                .mapNotNull { ComponentName.unflattenFromString(it) }
                .any { it == expectedComponent }
        }
    }
}
