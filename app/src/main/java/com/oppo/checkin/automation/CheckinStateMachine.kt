package com.oppo.checkin.automation

import com.oppo.checkin.data.CheckinRepository
import com.oppo.checkin.data.CheckinResultStatus
import com.oppo.checkin.data.CheckinState
import com.oppo.checkin.data.UiLabelConfig
import com.oppo.checkin.notification.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class StateAction {
    data object None : StateAction()
    data class ClickNode(val nodeId: Int, val description: String) : StateAction()
}

interface TimeoutScheduler {
    fun schedule(delayMs: Long, onTimeout: () -> Unit)
    fun cancel()
}

interface AutomationOutcomeListener {
    fun onSessionStarted()
    fun onSessionFinished(
        status: CheckinResultStatus,
        appLaunched: Boolean,
        buttonLocated: Boolean,
        resultVerified: Boolean,
        failureReason: String?,
        triggerSource: String
    )
    fun onManualFallbackRequired(reason: String)
}

class RepositoryOutcomeListener(
    private val repository: CheckinRepository,
    private val notificationHelper: NotificationHelper
) : AutomationOutcomeListener {

    override fun onSessionStarted() {
        repository.setTodayRunning()
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
        notificationHelper.showManualFallbackNotification(reason)
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

    private var appLaunched: Boolean = false
    private var buttonLocated: Boolean = false
    private var resultVerified: Boolean = false
    private var hasClickedSignIn: Boolean = false
    private var navigationRetries: Int = 0
    private var lastActionTimeMs: Long = 0L

    @Synchronized
    fun isActiveSessionFor(packageName: String): Boolean {
        return targetPackage.isNotEmpty() &&
            packageName == targetPackage &&
            !isTerminalOrIdle(_state.value)
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
        this.appLaunched = false
        this.buttonLocated = false
        this.resultVerified = false
        this.hasClickedSignIn = false
        this.navigationRetries = 0
        this.lastActionTimeMs = 0L

        _state.value = CheckinState.WAITING_FOR_TARGET_APP
        _statusDetail.value = "正在等待 OPPO 商城启动..."
        outcomeListener.onSessionStarted()
        armStageTimeout()
    }

    @Synchronized
    fun abortToManualFallback(reason: String) {
        if (isTerminalOrIdle(_state.value)) return
        timeoutScheduler.cancel()
        _state.value = CheckinState.NEEDS_USER_ACTION
        _statusDetail.value = reason
        outcomeListener.onManualFallbackRequired(reason)
    }

    @Synchronized
    fun cancelSession() {
        timeoutScheduler.cancel()
        _state.value = CheckinState.IDLE
        _statusDetail.value = ""
    }

    @Synchronized
    fun processUiTree(eventPackage: String, root: UiNodeSnapshot): StateAction {
        if (eventPackage != targetPackage || isTerminalOrIdle(_state.value)) {
            return StateAction.None
        }

        val blocker = UiNodeInspector.detectUnexpectedBlocker(root)
        if (blocker != null) {
            finishSession(
                terminalState = CheckinState.NEEDS_USER_ACTION,
                resultStatus = CheckinResultStatus.NEEDS_USER_ACTION,
                reason = blocker
            )
            return StateAction.None
        }

        val now = timeProvider()
        val minActionIntervalMs = 700L

        return when (_state.value) {
            CheckinState.WAITING_FOR_TARGET_APP,
            CheckinState.OPENING_HOME_PAGE -> {
                appLaunched = true
                _state.value = CheckinState.LOCATING_MEMBERSHIP_TAB
                _statusDetail.value = "已进入应用，正在查找“会员”标签..."
                armStageTimeout()
                tryLocateAndClickMembership(root, now, minActionIntervalMs)
            }

            CheckinState.LOCATING_MEMBERSHIP_TAB -> {
                // Check if we already arrived on points page or already see check-in status
                if (UiNodeInspector.findMatchingLabel(root, labelConfig.pointsPageLabels) != null ||
                    UiNodeInspector.findMatchingLabel(root, labelConfig.checkinButtonLabels) != null ||
                    UiNodeInspector.findMatchingLabel(root, labelConfig.alreadyCheckedInLabels) != null
                ) {
                    _state.value = CheckinState.CHECKING_SIGN_IN_STATUS
                    _statusDetail.value = "正在检查今日签到状态..."
                    armStageTimeout()
                    handleCheckOrClickSignIn(root, now, minActionIntervalMs)
                } else {
                    tryLocateAndClickMembership(root, now, minActionIntervalMs)
                }
            }

            CheckinState.OPENING_POINTS_PAGE -> {
                if (UiNodeInspector.findMatchingLabel(root, labelConfig.alreadyCheckedInLabels) != null ||
                    UiNodeInspector.findMatchingLabel(root, labelConfig.checkinButtonLabels) != null
                ) {
                    _state.value = CheckinState.CHECKING_SIGN_IN_STATUS
                    _statusDetail.value = "已进入积分页，正在检查签到状态..."
                    armStageTimeout()
                    handleCheckOrClickSignIn(root, now, minActionIntervalMs)
                } else {
                    tryLocateAndClickPointsEntry(root, now, minActionIntervalMs)
                }
            }

            CheckinState.CHECKING_SIGN_IN_STATUS -> {
                handleCheckOrClickSignIn(root, now, minActionIntervalMs)
            }

            CheckinState.CLICKING_SIGN_IN,
            CheckinState.VERIFYING_RESULT -> {
                // Never click sign-in a second time! Only verify confirmation text.
                val confirmed = UiNodeInspector.findMatchingLabel(
                    root,
                    labelConfig.successConfirmationLabels
                )
                if (confirmed != null) {
                    resultVerified = true
                    finishSession(
                        terminalState = CheckinState.SUCCESS,
                        resultStatus = CheckinResultStatus.SUCCESS,
                        reason = "已验证签到结果：$confirmed"
                    )
                }
                StateAction.None
            }

            else -> StateAction.None
        }
    }

    private fun tryLocateAndClickMembership(
        root: UiNodeSnapshot,
        now: Long,
        minIntervalMs: Long
    ): StateAction {
        if (now - lastActionTimeMs < minIntervalMs) return StateAction.None

        val membershipNode = UiNodeInspector.findClickableTarget(root, labelConfig.membershipLabels)
        if (membershipNode != null) {
            lastActionTimeMs = now
            _state.value = CheckinState.OPENING_POINTS_PAGE
            _statusDetail.value = "已点击“${membershipNode.matchedLabel}”，正在查找积分入口..."
            armStageTimeout()
            return StateAction.ClickNode(membershipNode.clickableNodeId, membershipNode.matchedLabel)
        }
        return StateAction.None
    }

    private fun tryLocateAndClickPointsEntry(
        root: UiNodeSnapshot,
        now: Long,
        minIntervalMs: Long
    ): StateAction {
        if (now - lastActionTimeMs < minIntervalMs) return StateAction.None

        val pointsNode = UiNodeInspector.findClickableTarget(root, labelConfig.pointsPageLabels)
        if (pointsNode != null) {
            lastActionTimeMs = now
            _state.value = CheckinState.CHECKING_SIGN_IN_STATUS
            _statusDetail.value = "已点击“${pointsNode.matchedLabel}”，正在等待签到页..."
            armStageTimeout()
            return StateAction.ClickNode(pointsNode.clickableNodeId, pointsNode.matchedLabel)
        }
        return StateAction.None
    }

    private fun handleCheckOrClickSignIn(
        root: UiNodeSnapshot,
        now: Long,
        minIntervalMs: Long
    ): StateAction {
        // 1. Check already-completed labels BEFORE attempting any click.
        val alreadyLabel = UiNodeInspector.findMatchingLabel(root, labelConfig.alreadyCheckedInLabels)
        if (alreadyLabel != null) {
            buttonLocated = true
            resultVerified = true
            finishSession(
                terminalState = CheckinState.ALREADY_COMPLETED,
                resultStatus = CheckinResultStatus.ALREADY_COMPLETED,
                reason = "检测到今日已完成签到（$alreadyLabel），未重复点击"
            )
            return StateAction.None
        }

        // 2. Hard idempotency guard: never click sign-in more than once per session.
        if (hasClickedSignIn) {
            _state.value = CheckinState.VERIFYING_RESULT
            return StateAction.None
        }

        if (now - lastActionTimeMs < minIntervalMs) return StateAction.None

        val checkinNode = UiNodeInspector.findClickableTarget(root, labelConfig.checkinButtonLabels)
        if (checkinNode != null) {
            buttonLocated = true
            hasClickedSignIn = true
            lastActionTimeMs = now
            _state.value = CheckinState.VERIFYING_RESULT
            _statusDetail.value = "已点击“${checkinNode.matchedLabel}”，正在等待结果确认..."
            armStageTimeout()
            return StateAction.ClickNode(checkinNode.clickableNodeId, checkinNode.matchedLabel)
        }

        return StateAction.None
    }

    @Synchronized
    fun onStageTimeout() {
        val current = _state.value
        if (isTerminalOrIdle(current)) return

        when (current) {
            CheckinState.WAITING_FOR_TARGET_APP -> {
                if (triggerSource == "定时") {
                    abortToManualFallback("等待 OPPO 商城进入前台超时，请手动点击开始签到")
                } else {
                    finishSession(
                        terminalState = CheckinState.FAILED,
                        resultStatus = CheckinResultStatus.FAILED,
                        reason = "等待目标应用打开超时"
                    )
                }
            }

            CheckinState.LOCATING_MEMBERSHIP_TAB,
            CheckinState.OPENING_POINTS_PAGE -> {
                if (navigationRetries < labelConfig.maxNavigationRetries) {
                    navigationRetries++
                    _statusDetail.value = "页面响应较慢，正在进行第 $navigationRetries 次重试..."
                    armStageTimeout()
                } else {
                    val stageName = if (current == CheckinState.LOCATING_MEMBERSHIP_TAB) {
                        "未找到“会员”标签"
                    } else {
                        "未找到“我的积分”入口"
                    }
                    finishSession(
                        terminalState = CheckinState.NEEDS_USER_ACTION,
                        resultStatus = CheckinResultStatus.NEEDS_USER_ACTION,
                        reason = "${stageName}（可能界面已改版），请手动签到"
                    )
                }
            }

            CheckinState.CHECKING_SIGN_IN_STATUS -> {
                finishSession(
                    terminalState = CheckinState.NEEDS_USER_ACTION,
                    resultStatus = CheckinResultStatus.NEEDS_USER_ACTION,
                    reason = "未在积分页找到“签到”按钮或已签到状态，请手动确认"
                )
            }

            CheckinState.CLICKING_SIGN_IN,
            CheckinState.VERIFYING_RESULT -> {
                // Click was already issued; never retry click and never claim SUCCESS without verification.
                finishSession(
                    terminalState = CheckinState.UNKNOWN,
                    resultStatus = CheckinResultStatus.UNKNOWN,
                    reason = "已点击签到，但未在规定时间内检测到确认提示，请手动核对结果"
                )
            }

            else -> {
                finishSession(
                    terminalState = CheckinState.FAILED,
                    resultStatus = CheckinResultStatus.FAILED,
                    reason = "执行超时"
                )
            }
        }
    }

    private fun armStageTimeout() {
        timeoutScheduler.cancel()
        val delayMs = labelConfig.stageTimeoutSeconds.coerceIn(5, 45) * 1000L
        timeoutScheduler.schedule(delayMs) {
            onStageTimeout()
        }
    }

    private fun finishSession(
        terminalState: CheckinState,
        resultStatus: CheckinResultStatus,
        reason: String
    ) {
        timeoutScheduler.cancel()
        _state.value = terminalState
        _statusDetail.value = reason
        outcomeListener.onSessionFinished(
            status = resultStatus,
            appLaunched = appLaunched,
            buttonLocated = buttonLocated,
            resultVerified = resultVerified,
            failureReason = reason,
            triggerSource = triggerSource
        )
    }

    private fun isTerminalOrIdle(state: CheckinState): Boolean {
        return state == CheckinState.IDLE ||
            state == CheckinState.SUCCESS ||
            state == CheckinState.ALREADY_COMPLETED ||
            state == CheckinState.NEEDS_USER_ACTION ||
            state == CheckinState.FAILED ||
            state == CheckinState.UNKNOWN
    }
}
