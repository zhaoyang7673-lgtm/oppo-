package com.oppo.checkin.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.oppo.checkin.CheckinApplication
import com.oppo.checkin.automation.StateAction
import com.oppo.checkin.automation.UiNodeSnapshot

/**
 * Strictly isolated AccessibilityService for OPPO Daily Check-in.
 *
 * CRITICAL V2.3 FIX:
 * - Do NOT set `serviceInfo.packageNames` to a sentinel or static single-package array!
 *   On ColorOS, setting `serviceInfo.packageNames` causes `AccessibilityWindowManager` to drop
 *   window-change tracking across activity launches, making `rootInActiveWindow` and `windows`
 *   permanently return `null` when transitioning from our app into OPPO Mall.
 * - Instead, keep `serviceInfo.packageNames = null` so the OS keeps the window cache fresh, and
 *   enforce strict runtime package isolation (`node.packageName == targetPkg`) before inspecting
 *   any window or performing any click.
 */
class CheckinAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            val container = CheckinApplication.getContainer(applicationContext)
            val activePkg = container.stateMachine.getActiveTargetPackage()
            if (activePkg != null) {
                inspectActiveTargetWindow(activePkg)
                mainHandler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        applyServiceFlags()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onInterrupt() {
        mainHandler.removeCallbacksAndMessages(null)
    }

    fun applyServiceFlags() {
        val info = serviceInfo ?: AccessibilityServiceInfo()
        // Keep packageNames null so ColorOS updates the active window cache across app switches!
        // Runtime package isolation is strictly enforced in onAccessibilityEvent & inspectActiveTargetWindow.
        info.packageNames = null
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.flags = info.flags or
            AccessibilityServiceInfo.DEFAULT or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
        serviceInfo = info
    }

    fun triggerSessionPolling() {
        applyServiceFlags()
        mainHandler.removeCallbacks(pollRunnable)
        mainHandler.postDelayed(pollRunnable, 350L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val container = CheckinApplication.getContainer(applicationContext)
        val config = container.repository.config.value
        val targetPkg = config.targetPackageName.trim()
        if (targetPkg.isEmpty() || !config.accessibilityDisclosureAccepted) return

        // Runtime Check 1: Only act during an active check-in session for targetPkg.
        if (!container.stateMachine.isActiveSessionFor(targetPkg)) return

        // Runtime Check 2: Only process events when the event or active window belongs to targetPkg.
        val eventPkg = event.packageName?.toString()
        if (eventPkg != null && eventPkg != targetPkg && !isWebViewOrSystemOverlay(eventPkg)) {
            return
        }

        inspectActiveTargetWindow(targetPkg)
    }

    private fun inspectActiveTargetWindow(targetPkg: String) {
        val container = CheckinApplication.getContainer(applicationContext)
        val stateMachine = container.stateMachine
        if (!stateMachine.isActiveSessionFor(targetPkg)) return

        val candidateRoots = findTargetRootNodes(targetPkg)
        if (candidateRoots.isEmpty()) return

        val nodeMap = ArrayList<AccessibilityNodeInfo>(160)
        val mergedChildren = candidateRoots.map { rootNode ->
            rootNode.refresh()
            buildSnapshot(rootNode, nodeMap, depth = 1)
        }
        val virtualRoot = UiNodeSnapshot(
            nodeId = -1,
            children = mergedChildren
        )

        when (val action = stateMachine.processUiTree(targetPkg, virtualRoot)) {
            is StateAction.ClickNode -> {
                val targetNode = nodeMap.getOrNull(action.nodeId)
                if (targetNode != null && targetNode.isEnabled) {
                    performReliableClick(targetNode, targetPkg)
                }
            }

            is StateAction.ScrollForward -> {
                val scrollNode = nodeMap.getOrNull(action.nodeId)
                scrollNode?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }

            StateAction.TapBottomCenterDockFallback -> {
                performBottomCenterDockTap()
            }

            StateAction.None -> Unit
        }
    }

    /**
     * Collects all interactive window roots belonging to [targetPkg] (including embedded WebViews
     * hosted inside the target application window).
     */
    private fun findTargetRootNodes(targetPkg: String): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            val allWindows = windows
            if (!allWindows.isNullOrEmpty()) {
                for (window in allWindows) {
                    val r = window.root ?: continue
                    val pkg = r.packageName?.toString()
                    if (pkg == targetPkg) {
                        roots.add(r)
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback to rootInActiveWindow below
        }

        val activeRoot = rootInActiveWindow
        if (activeRoot != null && activeRoot.packageName?.toString() == targetPkg) {
            if (roots.none { it == activeRoot }) {
                roots.add(0, activeRoot)
            }
        }
        return roots
    }

    /**
     * Performs BOTH native AccessibilityNodeInfo.ACTION_CLICK (on the node or its clickable parent)
     * AND a verified bounds-center tap gesture fallback if native click returns false or if the node
     * itself is a custom tab view where `isClickable == false`.
     */
    private fun performReliableClick(node: AccessibilityNodeInfo, targetPkg: String) {
        val nodePkg = node.packageName?.toString()
        if (nodePkg != null && nodePkg != targetPkg && !isWebViewOrSystemOverlay(nodePkg)) return

        var clickableTarget: AccessibilityNodeInfo? = if (node.isClickable) node else null
        var parent = node.parent
        var hops = 0
        while (clickableTarget == null && parent != null && hops < 6) {
            if (parent.isClickable && parent.isEnabled) {
                clickableTarget = parent
            }
            parent = parent.parent
            hops++
        }

        val nativeClicked = clickableTarget?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false

        // Always dispatch a precise tap inside the verified node's screen bounds if native click
        // was rejected OR if the node is inside a custom tab bar that only handles touch events.
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) {
            clickableTarget?.getBoundsInScreen(rect)
        }
        if (!nativeClicked && rect.width() > 0 && rect.height() > 0) {
            val cx = rect.exactCenterX()
            val cy = rect.exactCenterY()
            if (cx > 0f && cy > 0f) {
                dispatchSingleTap(cx, cy)
            }
        }
    }

    /**
     * Last-resort fallback for the 5-tab OPPO Mall bottom dock (首页 | 选购 | 会员 | 服务 | 我的):
     * taps the center tab slot (X = 50% width, Y = 94% height) ONLY during retry on the target app.
     */
    private fun performBottomCenterDockTap() {
        val dm = resources.displayMetrics
        val cx = dm.widthPixels * 0.50f
        val cy = dm.heightPixels * 0.94f
        if (cx > 0f && cy > 0f) {
            dispatchSingleTap(cx, cy)
        }
    }

    private fun dispatchSingleTap(cx: Float, cy: Float) {
        val path = Path().apply { moveTo(cx, cy) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 65L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun buildSnapshot(
        node: AccessibilityNodeInfo,
        nodeMap: MutableList<AccessibilityNodeInfo>,
        depth: Int
    ): UiNodeSnapshot {
        val id = nodeMap.size
        nodeMap.add(node)

        val rect = Rect()
        node.getBoundsInScreen(rect)

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
            isScrollable = node.isScrollable,
            isSelected = node.isSelected,
            isEnabled = node.isEnabled,
            isVisibleToUser = node.isVisibleToUser,
            boundsLeft = rect.left,
            boundsTop = rect.top,
            boundsRight = rect.right,
            boundsBottom = rect.bottom,
            children = children
        )
    }

    private fun isWebViewOrSystemOverlay(pkg: String): Boolean {
        return pkg.contains("webview", ignoreCase = true) || pkg.contains("chromium", ignoreCase = true)
    }

    companion object {
        private const val MAX_TREE_DEPTH = 36
        private const val POLL_INTERVAL_MS = 550L

        @Volatile
        private var instance: CheckinAccessibilityService? = null

        fun refreshIsolationIfConnected() {
            instance?.applyServiceFlags()
        }

        fun notifySessionStarted() {
            instance?.triggerSessionPolling()
        }

        /**
         * Launches the target OPPO Mall app from the bound AccessibilityService context.
         * Bound AccessibilityServices are explicitly exempted from Android 10–15 background
         * activity launch restrictions.
         */
        fun launchTargetAppFromService(launchIntent: Intent): Boolean {
            val svc = instance ?: return false
            return try {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                svc.startActivity(launchIntent)
                svc.triggerSessionPolling()
                true
            } catch (_: Exception) {
                false
            }
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
