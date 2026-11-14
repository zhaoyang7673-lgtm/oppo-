package com.oppo.checkin.automation

import com.oppo.checkin.data.CheckinRepository
import com.oppo.checkin.data.CheckinResultStatus
import com.oppo.checkin.data.CheckinState
import com.oppo.checkin.data.DiagnosticLogEntry
import com.oppo.checkin.data.UiLabelConfig
import com.oppo.checkin.notification.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class StateAction {
    data object None : StateAction()
    data class ClickNode(val nodeId: Int, val description: String) : StateAction()
    data class ScrollForward(val nodeId: Int, val description: String) : StateAction()
    data object TapBottomCenterDockFallback : StateAction()
}

interface TimeoutScheduler {
    fun schedule(delayMs: Long, onTimeout: () -> Unit)
    fun cancel()
}

interface AutomationOutcomeListener {
    fun onSessionStarted()
    fun onProgressUpdated(state: CheckinState, message: String)
    fun onDiagnosticLog(entry: DiagnosticLogEntry)
    fun onSessionFinished(
        status: CheckinResultStatus,
        appLaunched: Boolean,
        buttonLocated: Boolean,
        resultVerified: Boolean,
        failureReason: String?,
        triggerSource: String
    )
    fun onManualFallbackRequired(reason: String)
    fun onCleanupAndReturn()
}

class RepositoryOutcomeListener(
    private val repository: CheckinRepository,
    private val notificationHelper: NotificationHelper
) : AutomationOutcomeListener {

    override fun onSessionStarted() {
        repository.setTodayRunning()
    }

    override fun onProgressUpdated(state: CheckinState, message: String) {
        if (state != CheckinState.IDLE) {
            notificationHelper.updateProgressNotification(message)
        }
    }

    override fun onDiagnosticLog(entry: DiagnosticLogEntry) {
        if (repository.config.value.debugTestModeEnabled) {
            repository.appendDiagnosticLog(entry)
        }
    }

    override fun onSessionFinished(
        status: CheckinResultStatus,
        appLaunched: Boolean,
        buttonLocated: Boolean,
        resultVerified: Boolean,
        failureReason: String?,
        triggerSource: String
    ) {
        repository.recordExecution(
            status = status,
            appLaunched = appLaunched,
            buttonLocated = buttonLocated,
            resultVerified = resultVerified,
            failureReason = failureReason,
            triggerSource = triggerSource
        )
        notificationHelper.showResultNotification(status, failureReason)
    }

    override fun onManualFallbackRequired(reason: String) {
        repository.recordExecution(
            status = CheckinResultStatus.NEEDS_USER_ACTION,
            appLaunched = false,
            buttonLocated = false,
            resultVerified = false,
            failureReason = reason,
            triggerSource = "定时"
        )
        notificationHelper.cancelProgressNotification()
        notificationHelper.showManualFallbackNotification(reason)
    }

    override fun onCleanupAndReturn() {
        notificationHelper.cancelProgressNotification()
    }
}

class CheckinStateMachine(
    private val outcomeListener: AutomationOutcomeListener,
    private val timeoutScheduler: TimeoutScheduler,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {

    private val _state = MutableStateFlow(CheckinState.IDLE)
    val state: StateFlow<CheckinState> = _state.asStateFlow()

    private val _statusDetail = MutableStateFlow("")
    val statusDetail: StateFlow<String> = _statusDetail.asStateFlow()

    private var targetPackage: String = ""
    private var labelConfig: UiLabelConfig = UiLabelConfig()
    private var triggerSource: String = "手动"

    private var sessionStartMs: Long = 0L
    private var appLaunched: Boolean = false
    private var buttonLocated: Boolean = false
    private var resultVerified: Boolean = false
    private var hasClickedSignIn: Boolean = false
    private var navigationRetries: Int = 0
    private var lastActionTimeMs: Long = 0L
    private var lastSampledLabels: String = "尚未读取到目标窗口"

    @Synchronized
    fun isActiveSessionFor(packageName: String): Boolean {
        return targetPackage.isNotEmpty() &&
            packageName == targetPackage &&
            !isTerminalOrIdle(_state.value)
    }

    @Synchronized
    fun getActiveTargetPackage(): String? {
        return targetPackage.takeIf { it.isNotEmpty() && !isTerminalOrIdle(_state.value) }
    }

    @Synchronized
    fun startSession(
        targetPackage: String,
        labelConfig: UiLabelConfig,
        triggerSource: String
    ) {
        timeoutScheduler.cancel()
        this.targetPackage = targetPackage
        this.labelConfig = labelConfig
        this.triggerSource = triggerSource
        this.sessionStartMs = timeProvider()
        this.appLaunched = false
        this.buttonLocated = false
        this.resultVerified = false
        this.hasClickedSignIn = false
        this.navigationRetries = 0
        this.lastActionTimeMs = 0L
        this.lastSampledLabels = "尚未读取到目标窗口"

        transitionTo(CheckinState.STARTING, "正在启动 OPPO 商城…")
        outcomeListener.onSessionStarted()
        transitionTo(CheckinState.LAUNCHING_OPPO_MALL, "正在启动 OPPO 商城…")
        armStageTimeout()
    }

    @Synchronized
    fun abortToManualFallback(reason: String) {
        if (isTerminalOrIdle(_state.value)) return
        timeoutScheduler.cancel()
        transitionTo(CheckinState.NEEDS_USER_ACTION, reason)
        outcomeListener.onManualFallbackRequired(reason)
        cleanupAndReturn()
    }

    @Synchronized
    fun cancelSession() {
        timeoutScheduler.cancel()
        transitionTo(CheckinState.IDLE, "")
    }

    @Synchronized
    fun processUiTree(eventPackage: String, root: UiNodeSnapshot): StateAction {
        if (eventPackage != targetPackage || isTerminalOrIdle(_state.value)) {
            return StateAction.None
        }

        lastSampledLabels = UiNodeInspector.sampleTopVisibleLabels(root)

        val blocker = UiNodeInspector.detectUnexpectedBlocker(root)
        if (blocker != null) {
            logDiag(
                state = _state.value,
                expected = "正常签到流程页面",
                detected = blocker,
                buttonText = null,
                validated = false,
                note = "遇到阻断界面，安全停止"
            )
            finishAndCleanup(
                terminalState = CheckinState.NEEDS_USER_ACTION,
                resultStatus = CheckinResultStatus.NEEDS_USER_ACTION,
                reason = blocker
            )
            return StateAction.None
        }

        val now = timeProvider()
        val minActionIntervalMs = 700L

        return when (_state.value) {
            CheckinState.STARTING,
            CheckinState.LAUNCHING_OPPO_MALL -> {
                appLaunched = true
                transitionTo(CheckinState.FINDING_MEMBERSHIP_TAB, "正在进入会员中心…")
                armStageTimeout()
                handleFindingMembershipOrShortcut(root, now, minActionIntervalMs)
            }

            CheckinState.FINDING_MEMBERSHIP_TAB -> {
                handleFindingMembershipOrShortcut(root, now, minActionIntervalMs)
            }

            CheckinState.VERIFYING_MEMBERSHIP_PAGE -> {
                val pointsAnchor = UiNodeInspector.verifyPointsPage(root, labelConfig)
                if (pointsAnchor != null) {
                    transitionTo(CheckinState.LOCATING_DAILY_CHECK_IN_CARD, "正在定位今日签到区域…")
                    armStageTimeout()
                    return inspectAndActOnCheckInCard(root, now, minActionIntervalMs)
                }

                val anchor = UiNodeInspector.verifyMembershipPage(root, labelConfig)
                if (anchor != null) {
                    logDiag(
                        state = CheckinState.VERIFYING_MEMBERSHIP_PAGE,
                        expected = "会员页锚点",
                        detected = anchor,
                        buttonText = null,
                        validated = true,
                        note = "确认处于会员中心"
                    )
                    transitionTo(CheckinState.FINDING_MY_POINTS, "正在查找“我的积分”…")
                    armStageTimeout()
                    tryClickMyPoints(root, now, minActionIntervalMs)
                } else {
                    StateAction.None
                }
            }

            CheckinState.FINDING_MY_POINTS -> {
                val pointsAnchor = UiNodeInspector.verifyPointsPage(root, labelConfig)
                if (pointsAnchor != null) {
                    transitionTo(CheckinState.LOCATING_DAILY_CHECK_IN_CARD, "正在定位今日签到区域…")
                    armStageTimeout()
                    inspectAndActOnCheckInCard(root, now, minActionIntervalMs)
                } else {
                    tryClickMyPoints(root, now, minActionIntervalMs)
                }
            }

            CheckinState.VERIFYING_POINTS_PAGE -> {
                val pointsAnchor = UiNodeInspector.verifyPointsPage(root, labelConfig)
                if (pointsAnchor != null) {
                    logDiag(
                        state = CheckinState.VERIFYING_POINTS_PAGE,
                        expected = "积分商城",
                        detected = pointsAnchor,
                        buttonText = null,
                        validated = true,
                        note = "已验证积分商城页面"
                    )
                    transitionTo(CheckinState.LOCATING_DAILY_CHECK_IN_CARD, "正在定位今日签到区域…")
                    armStageTimeout()
                    inspectAndActOnCheckInCard(root, now, minActionIntervalMs)
                } else {
                    StateAction.None
                }
            }

            CheckinState.LOCATING_DAILY_CHECK_IN_CARD,
            CheckinState.DETECTING_CHECK_IN_BUTTON -> {
                inspectAndActOnCheckInCard(root, now, minActionIntervalMs)
            }

            CheckinState.TODAY_NOT_CHECKED_IN,
            CheckinState.CLICKING_CHECK_IN_ONCE,
            CheckinState.VERIFYING_CHECK_IN_RESULT -> {
                val inspection = UiNodeInspector.inspectDailyCheckInCard(root, labelConfig)
                if (inspection.buttonStatus == CheckInButtonStatus.ALREADY_CHECKED_IN &&
                    inspection.insideVerifiedCard
                ) {
                    resultVerified = true
                    logDiag(
                        state = CheckinState.VERIFYING_CHECK_IN_RESULT,
                        expected = "签到按钮变为“已签到”",
                        detected = inspection.contextAnchorFound,
                        buttonText = inspection.buttonText,
                        validated = true,
                        note = "CHECK_IN_SUCCESS"
                    )
                    finishAndCleanup(
                        terminalState = CheckinState.CHECK_IN_SUCCESS,
                        resultStatus = CheckinResultStatus.SUCCESS,
                        reason = "今日签到成功 ✓"
                    )
                    return StateAction.None
                }

                val successMsg = UiNodeInspector.findMatchingLabel(
                    root,
                    labelConfig.successConfirmationLabels
                )
                if (successMsg != null) {
                    resultVerified = true
                    logDiag(
                        state = CheckinState.VERIFYING_CHECK_IN_RESULT,
                        expected = "签到成功提示",
                        detected = successMsg,
                        buttonText = inspection.buttonText,
                        validated = false,
                        note = "CHECK_IN_SUCCESS_WITH_LIMITED_VERIFICATION"
                    )
                    finishAndCleanup(
                        terminalState = CheckinState.CHECK_IN_SUCCESS_WITH_LIMITED_VERIFICATION,
                        resultStatus = CheckinResultStatus.SUCCESS,
                        reason = "今日签到成功 ✓（已检测到提示：$successMsg）"
                    )
                }
                StateAction.None
            }

            else -> StateAction.None
        }
    }

    private fun handleFindingMembershipOrShortcut(
        root: UiNodeSnapshot,
        now: Long,
        minActionIntervalMs: Long
    ): StateAction {
        // 1. If OPPO Mall was already on Points Mall page when launched
        val pointsAnchor = UiNodeInspector.verifyPointsPage(root, labelConfig)
        if (pointsAnchor != null) {
            transitionTo(CheckinState.LOCATING_DAILY_CHECK_IN_CARD, "正在定位今日签到区域…")
            armStageTimeout()
            return inspectAndActOnCheckInCard(root, now, minActionIntervalMs)
        }

        // 2. If OPPO Mall was already on Membership page when launched
        val membershipAnchor = UiNodeInspector.verifyMembershipPage(root, labelConfig)
        if (membershipAnchor != null) {
            logDiag(
                state = CheckinState.VERIFYING_MEMBERSHIP_PAGE,
                expected = "会员页锚点",
                detected = membershipAnchor,
                buttonText = null,
                validated = true,
                note = "已处于会员中心"
            )
            transitionTo(CheckinState.FINDING_MY_POINTS, "正在查找“我的积分”…")
            armStageTimeout()
            return tryClickMyPoints(root, now, minActionIntervalMs)
        }

        // 3. Try locating and clicking the bottom dock "会员" tab
        val clickTabAction = tryClickMembershipTab(root, now, minActionIntervalMs)
        if (clickTabAction != StateAction.None) {
            return clickTabAction
        }

        // 4. If a splash ad or home promo popup ("跳过" / "关闭") is blocking the home screen, dismiss it
        if (now - lastActionTimeMs >= minActionIntervalMs) {
            val dismissNode = UiNodeInspector.findSafePopupDismiss(root)
            if (dismissNode != null) {
                lastActionTimeMs = now
                return StateAction.ClickNode(dismissNode.clickableNodeId, dismissNode.matchedLabel)
            }
        }

        // 5. If retry >= 1 and OPPO Mall renders custom vector icons on the bottom dock without exposing
        //    "会员" text to Accessibility, tap the middle dock slot (3rd of 5 tabs) once.
        if (navigationRetries >= 1 && now - lastActionTimeMs >= 1500L) {
            lastActionTimeMs = now
            transitionTo(CheckinState.VERIFYING_MEMBERSHIP_PAGE, "正在进入会员中心…")
            armStageTimeout()
            return StateAction.TapBottomCenterDockFallback
        }

        return StateAction.None
    }

    private fun tryClickMembershipTab(
        root: UiNodeSnapshot,
        now: Long,
        minIntervalMs: Long
    ): StateAction {
        if (now - lastActionTimeMs < minIntervalMs) return StateAction.None
        val tab = UiNodeInspector.findBottomDockTab(root, labelConfig)
        if (tab != null) {
            lastActionTimeMs = now
            logDiag(
                state = CheckinState.FINDING_MEMBERSHIP_TAB,
                expected = "底部导航“会员”",
                detected = tab.matchedLabel,
                buttonText = tab.matchedLabel,
                validated = true,
                note = "已点击底部导航“会员”"
            )
            transitionTo(CheckinState.VERIFYING_MEMBERSHIP_PAGE, "正在进入会员中心…")
            armStageTimeout()
            return StateAction.ClickNode(tab.clickableNodeId, tab.matchedLabel)
        }
        return StateAction.None
    }

    private fun tryClickMyPoints(
        root: UiNodeSnapshot,
        now: Long,
        minIntervalMs: Long
    ): StateAction {
        if (now - lastActionTimeMs < minIntervalMs) return StateAction.None
        val entry = UiNodeInspector.findMyPointsEntry(root, labelConfig)
        if (entry != null) {
            lastActionTimeMs = now
            logDiag(
                state = CheckinState.FINDING_MY_POINTS,
                expected = "我的积分",
                detected = entry.matchedLabel,
                buttonText = entry.matchedLabel,
                validated = true,
                note = "已点击“我的积分”"
            )
            transitionTo(CheckinState.VERIFYING_POINTS_PAGE, "正在进入积分商城…")
            armStageTimeout()
            return StateAction.ClickNode(entry.clickableNodeId, entry.matchedLabel)
        }

        // If on Membership page during a retry and "我的积分" is below the fold, scroll down gently
        if (navigationRetries >= 1) {
            val scrollableId = UiNodeInspector.findScrollableNodeId(root)
            if (scrollableId != null) {
                lastActionTimeMs = now
                return StateAction.ScrollForward(scrollableId, "向下滚动查找我的积分")
            }
        }
        return StateAction.None
    }

    private fun inspectAndActOnCheckInCard(
        root: UiNodeSnapshot,
        now: Long,
        minIntervalMs: Long
    ): StateAction {
        transitionTo(CheckinState.DETECTING_CHECK_IN_BUTTON, "正在读取签到按钮状态…")
        val inspection = UiNodeInspector.inspectDailyCheckInCard(root, labelConfig)

        logDiag(
            state = CheckinState.DETECTING_CHECK_IN_BUTTON,
            expected = "签到卡片及精确按钮(签到/已签到)",
            detected = inspection.contextAnchorFound,
            buttonText = inspection.buttonText,
            validated = inspection.insideVerifiedCard,
            note = "status=${inspection.buttonStatus}"
        )

        if (!inspection.cardFound) {
            return StateAction.None
        }

        return when (inspection.buttonStatus) {
            CheckInButtonStatus.ALREADY_CHECKED_IN -> {
                buttonLocated = true
                resultVerified = true
                transitionTo(CheckinState.TODAY_ALREADY_CHECKED_IN, "检测到今日已签到，无需重复操作。")
                finishAndCleanup(
                    terminalState = CheckinState.TODAY_ALREADY_CHECKED_IN,
                    resultStatus = CheckinResultStatus.ALREADY_COMPLETED,
                    reason = "今日已签到 ✓"
                )
                StateAction.None
            }

            CheckInButtonStatus.NOT_CHECKED_IN -> {
                if (hasClickedSignIn) {
                    transitionTo(CheckinState.VERIFYING_CHECK_IN_RESULT, "正在验证签到结果…")
                    return StateAction.None
                }
                if (now - lastActionTimeMs < minIntervalMs) return StateAction.None
                val nodeId = inspection.clickableNodeId ?: return StateAction.None

                buttonLocated = true
                hasClickedSignIn = true
                lastActionTimeMs = now
                transitionTo(CheckinState.TODAY_NOT_CHECKED_IN, "检测到今日尚未签到，正在执行签到…")
                transitionTo(CheckinState.CLICKING_CHECK_IN_ONCE, "检测到今日尚未签到，正在执行签到…")
                transitionTo(CheckinState.VERIFYING_CHECK_IN_RESULT, "正在验证签到结果…")
                armStageTimeout()
                StateAction.ClickNode(nodeId, inspection.buttonText ?: "签到")
            }

            CheckInButtonStatus.UNKNOWN -> StateAction.None
        }
    }

    @Synchronized
    fun onStageTimeout() {
        val current = _state.value
        if (isTerminalOrIdle(current)) return

        when (current) {
            CheckinState.STARTING,
            CheckinState.LAUNCHING_OPPO_MALL -> {
                if (triggerSource == "定时") {
                    abortToManualFallback("等待 OPPO 商城进入前台超时，请手动点击开始签到")
                } else {
                    finishAndCleanup(
                        terminalState = CheckinState.TIMEOUT,
                        resultStatus = CheckinResultStatus.FAILED,
                        reason = "签到执行超时，请手动检查。"
                    )
                }
            }

            CheckinState.FINDING_MEMBERSHIP_TAB,
            CheckinState.VERIFYING_MEMBERSHIP_PAGE,
            CheckinState.FINDING_MY_POINTS,
            CheckinState.VERIFYING_POINTS_PAGE -> {
                if (navigationRetries < labelConfig.maxNavigationRetries) {
                    navigationRetries++
                    val retryState = when (current) {
                        CheckinState.VERIFYING_MEMBERSHIP_PAGE -> CheckinState.FINDING_MEMBERSHIP_TAB
                        CheckinState.VERIFYING_POINTS_PAGE -> CheckinState.FINDING_MY_POINTS
                        else -> current
                    }
                    transitionTo(retryState, "页面响应较慢，正在进行第 $navigationRetries 次重试…")
                    armStageTimeout()
                } else {
                    finishAndCleanup(
                        terminalState = CheckinState.NEEDS_USER_ACTION,
                        resultStatus = CheckinResultStatus.NEEDS_USER_ACTION,
                        reason = "未能识别目标页面（${current.name}，可见文本：$lastSampledLabels），请手动检查。"
                    )
                }
            }

            CheckinState.LOCATING_DAILY_CHECK_IN_CARD,
            CheckinState.DETECTING_CHECK_IN_BUTTON -> {
                logDiag(
                    state = CheckinState.CHECK_IN_STATE_UNKNOWN,
                    expected = "精确签到按钮(签到/已签到)",
                    detected = null,
                    buttonText = null,
                    validated = false,
                    note = "CHECK_IN_STATE_UNKNOWN"
                )
                finishAndCleanup(
                    terminalState = CheckinState.CHECK_IN_STATE_UNKNOWN,
                    resultStatus = CheckinResultStatus.UNKNOWN,
                    reason = "无法准确识别签到状态（可见文本：$lastSampledLabels），请手动检查。"
                )
            }

            CheckinState.TODAY_NOT_CHECKED_IN,
            CheckinState.CLICKING_CHECK_IN_ONCE,
            CheckinState.VERIFYING_CHECK_IN_RESULT -> {
                finishAndCleanup(
                    terminalState = CheckinState.CHECK_IN_RESULT_UNKNOWN,
                    resultStatus = CheckinResultStatus.UNKNOWN,
                    reason = "无法准确识别签到状态，请手动检查。"
                )
            }

            else -> {
                finishAndCleanup(
                    terminalState = CheckinState.TIMEOUT,
                    resultStatus = CheckinResultStatus.FAILED,
                    reason = "签到执行超时，请手动检查。"
                )
            }
        }
    }

    private fun finishAndCleanup(
        terminalState: CheckinState,
        resultStatus: CheckinResultStatus,
        reason: String
    ) {
        timeoutScheduler.cancel()
        transitionTo(terminalState, reason)
        outcomeListener.onSessionFinished(
            status = resultStatus,
            appLaunched = appLaunched,
            buttonLocated = buttonLocated,
            resultVerified = resultVerified,
            failureReason = reason,
            triggerSource = triggerSource
        )
        cleanupAndReturn()
    }

    private fun cleanupAndReturn() {
        transitionTo(CheckinState.CLEANUP_AND_RETURN, "签到任务已完成，正在返回…")
        outcomeListener.onCleanupAndReturn()
        transitionTo(CheckinState.IDLE, _statusDetail.value)
    }

    private fun transitionTo(nextState: CheckinState, detail: String) {
        _state.value = nextState
        if (detail.isNotBlank()) {
            _statusDetail.value = detail
            outcomeListener.onProgressUpdated(nextState, detail)
        }
    }

    private fun logDiag(
        state: CheckinState,
        expected: String,
        detected: String?,
        buttonText: String?,
        validated: Boolean?,
        note: String
    ) {
        outcomeListener.onDiagnosticLog(
            DiagnosticLogEntry(
                timestampEpochMs = timeProvider(),
                state = state,
                expectedAnchor = expected,
                detectedAnchor = detected,
                buttonText = buttonText,
                hierarchyValidated = validated,
                durationMs = (timeProvider() - sessionStartMs).coerceAtLeast(0L),
                note = note
            )
        )
    }

    private fun armStageTimeout() {
        timeoutScheduler.cancel()
        val delayMs = labelConfig.stageTimeoutSeconds.coerceIn(5, 45) * 1000L
        timeoutScheduler.schedule(delayMs) {
            onStageTimeout()
        }
    }

    private fun isTerminalOrIdle(state: CheckinState): Boolean {
        return state == CheckinState.IDLE ||
            state == CheckinState.CHECK_IN_SUCCESS ||
            state == CheckinState.CHECK_IN_SUCCESS_WITH_LIMITED_VERIFICATION ||
            state == CheckinState.TODAY_ALREADY_CHECKED_IN ||
            state == CheckinState.CHECK_IN_STATE_UNKNOWN ||
            state == CheckinState.CHECK_IN_RESULT_UNKNOWN ||
            state == CheckinState.NEEDS_USER_ACTION ||
            state == CheckinState.FAILED ||
            state == CheckinState.TIMEOUT ||
            state == CheckinState.CLEANUP_AND_RETURN
    }
}
