package com.oppo.checkin

import com.oppo.checkin.automation.AutomationOutcomeListener
import com.oppo.checkin.automation.CheckinStateMachine
import com.oppo.checkin.automation.StateAction
import com.oppo.checkin.automation.TimeoutScheduler
import com.oppo.checkin.automation.UiNodeSnapshot
import com.oppo.checkin.data.CheckinResultStatus
import com.oppo.checkin.data.CheckinState
import com.oppo.checkin.data.DiagnosticLogEntry
import com.oppo.checkin.data.UiLabelConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTimeoutScheduler : TimeoutScheduler {
    var pendingTimeout: (() -> Unit)? = null
    override fun schedule(delayMs: Long, onTimeout: () -> Unit) {
        pendingTimeout = onTimeout
    }
    override fun cancel() {
        pendingTimeout = null
    }
    fun triggerTimeout() {
        val action = pendingTimeout
        pendingTimeout = null
        action?.invoke()
    }
}

private class FakeOutcomeListener : AutomationOutcomeListener {
    var startedCount = 0
    var cleanupCount = 0
    var lastStatus: CheckinResultStatus? = null
    var lastAppLaunched = false
    var lastButtonLocated = false
    var lastResultVerified = false
    var lastReason: String? = null
    val progressMessages = mutableListOf<String>()
    val diagnosticLogs = mutableListOf<DiagnosticLogEntry>()
    val fallbackReasons = mutableListOf<String>()

    override fun onSessionStarted() {
        startedCount++
    }

    override fun onProgressUpdated(state: CheckinState, message: String) {
        progressMessages.add(message)
    }

    override fun onDiagnosticLog(entry: DiagnosticLogEntry) {
        diagnosticLogs.add(entry)
    }

    override fun onSessionFinished(
        status: CheckinResultStatus,
        appLaunched: Boolean,
        buttonLocated: Boolean,
        resultVerified: Boolean,
        failureReason: String?,
        triggerSource: String
    ) {
        lastStatus = status
        lastAppLaunched = appLaunched
        lastButtonLocated = buttonLocated
        lastResultVerified = resultVerified
        lastReason = failureReason
    }

    override fun onManualFallbackRequired(reason: String) {
        fallbackReasons.add(reason)
    }

    override fun onCleanupAndReturn() {
        cleanupCount++
    }
}

class CheckinStateMachineTest {

    private val targetPkg = "com.oppo.store"
    private var fakeClock = 10_000L

    private fun createEngine(): Triple<CheckinStateMachine, FakeOutcomeListener, FakeTimeoutScheduler> {
        val listener = FakeOutcomeListener()
        val scheduler = FakeTimeoutScheduler()
        val machine = CheckinStateMachine(listener, scheduler) {
            fakeClock += 1_000L
            fakeClock
        }
        return Triple(machine, listener, scheduler)
    }

    private fun buildHomeTreeWithDock(): UiNodeSnapshot {
        return UiNodeSnapshot(
            nodeId = 0,
            children = listOf(
                UiNodeSnapshot(nodeId = 1, text = "首页商城推荐"),
                UiNodeSnapshot(
                    nodeId = 2,
                    viewIdResourceName = "com.oppo.store:id/bottom_dock",
                    children = listOf(
                        UiNodeSnapshot(nodeId = 3, text = "首页", isClickable = true),
                        UiNodeSnapshot(nodeId = 4, text = "选购", isClickable = true),
                        UiNodeSnapshot(
                            nodeId = 5,
                            isClickable = true,
                            children = listOf(UiNodeSnapshot(nodeId = 6, text = "会员", isClickable = false))
                        ),
                        UiNodeSnapshot(nodeId = 7, text = "服务", isClickable = true),
                        UiNodeSnapshot(nodeId = 8, text = "我的", isClickable = true)
                    )
                )
            )
        )
    }

    private fun buildMembershipPageTree(): UiNodeSnapshot {
        return UiNodeSnapshot(
            nodeId = 0,
            children = listOf(
                UiNodeSnapshot(nodeId = 1, text = "钻卡会员"),
                UiNodeSnapshot(nodeId = 2, text = "尊享积分"),
                UiNodeSnapshot(nodeId = 3, text = "280"),
                UiNodeSnapshot(
                    nodeId = 4,
                    isClickable = true,
                    children = listOf(UiNodeSnapshot(nodeId = 5, text = "我的积分 >", isClickable = false))
                ),
                UiNodeSnapshot(
                    nodeId = 6,
                    children = listOf(
                        UiNodeSnapshot(nodeId = 7, text = "首页"),
                        UiNodeSnapshot(nodeId = 8, text = "会员")
                    )
                )
            )
        )
    }

    @Test
    fun `Scenario A and E - not checked in clicks once and verifies button change to checked in`() {
        val (machine, listener, _) = createEngine()
        machine.startSession(targetPkg, UiLabelConfig(), "定时")

        // 1. Click Membership in bottom dock
        val action1 = machine.processUiTree(targetPkg, buildHomeTreeWithDock())
        assertEquals(StateAction.ClickNode(5, "会员"), action1)
        assertEquals(CheckinState.VERIFYING_MEMBERSHIP_PAGE, machine.state.value)

        // 2. Verify Membership page & click My Points
        val action2 = machine.processUiTree(targetPkg, buildMembershipPageTree())
        assertEquals(StateAction.ClickNode(4, "我的积分"), action2)
        assertEquals(CheckinState.VERIFYING_POINTS_PAGE, machine.state.value)

        // 3. Points Mall page with daily check-in card showing "签到" button
        val pointsNotCheckedIn = UiNodeSnapshot(
            nodeId = 0,
            children = listOf(
                UiNodeSnapshot(nodeId = 10, text = "积分商城"),
                UiNodeSnapshot(
                    nodeId = 11,
                    children = listOf(
                        UiNodeSnapshot(nodeId = 12, text = "累计签到"),
                        UiNodeSnapshot(nodeId = 13, text = "已签到 11 天"),
                        UiNodeSnapshot(nodeId = 14, text = "签到", isClickable = true)
                    )
                )
            )
        )
        val action3 = machine.processUiTree(targetPkg, pointsNotCheckedIn)
        assertEquals(StateAction.ClickNode(14, "签到"), action3)
        assertEquals(CheckinState.VERIFYING_CHECK_IN_RESULT, machine.state.value)

        // Duplicate click guard: even if "签到" briefly remains, never click again
        val duplicateAction = machine.processUiTree(targetPkg, pointsNotCheckedIn)
        assertEquals(StateAction.None, duplicateAction)

        // 4. Same verified button now displays "已签到" -> CHECK_IN_SUCCESS -> CLEANUP_AND_RETURN -> IDLE
        val pointsAfterClick = UiNodeSnapshot(
            nodeId = 0,
            children = listOf(
                UiNodeSnapshot(nodeId = 10, text = "积分商城"),
                UiNodeSnapshot(
                    nodeId = 11,
                    children = listOf(
                        UiNodeSnapshot(nodeId = 12, text = "累计签到"),
                        UiNodeSnapshot(nodeId = 13, text = "已签到 12 天"),
                        UiNodeSnapshot(nodeId = 14, text = "已签到", isClickable = false, isEnabled = false)
                    )
                )
            )
        )
        val action4 = machine.processUiTree(targetPkg, pointsAfterClick)
        assertEquals(StateAction.None, action4)
        assertEquals(CheckinResultStatus.SUCCESS, listener.lastStatus)
        assertTrue(listener.lastAppLaunched)
        assertTrue(listener.lastButtonLocated)
        assertTrue(listener.lastResultVerified)
        assertEquals(1, listener.cleanupCount)
        assertEquals(CheckinState.IDLE, machine.state.value)
    }

    @Test
    fun `Scenario B - already checked in button displays 已签到 and does not click`() {
        val (machine, listener, _) = createEngine()
        machine.startSession(targetPkg, UiLabelConfig(), "手动测试")

        machine.processUiTree(targetPkg, buildHomeTreeWithDock())
        machine.processUiTree(targetPkg, buildMembershipPageTree())

        val alreadyTree = UiNodeSnapshot(
            nodeId = 0,
            children = listOf(
                UiNodeSnapshot(nodeId = 10, text = "积分商城"),
                UiNodeSnapshot(
                    nodeId = 11,
                    children = listOf(
                        UiNodeSnapshot(nodeId = 12, text = "签到提醒"),
                        UiNodeSnapshot(nodeId = 13, text = "已签到", isClickable = false)
                    )
                )
            )
        )
        val action = machine.processUiTree(targetPkg, alreadyTree)
        assertEquals(StateAction.None, action)
        assertEquals(CheckinResultStatus.ALREADY_COMPLETED, listener.lastStatus)
        assertTrue(listener.lastResultVerified)
        assertEquals(1, listener.cleanupCount)
        assertEquals(CheckinState.IDLE, machine.state.value)
    }

    @Test
    fun `Scenario C - streak text 已签到 11 天 without actual button returns UNKNOWN and NEVER ALREADY_COMPLETED`() {
        val (machine, listener, scheduler) = createEngine()
        machine.startSession(targetPkg, UiLabelConfig(), "手动测试")

        machine.processUiTree(targetPkg, buildHomeTreeWithDock())
        machine.processUiTree(targetPkg, buildMembershipPageTree())

        val streakOnlyTree = UiNodeSnapshot(
            nodeId = 0,
            children = listOf(
                UiNodeSnapshot(nodeId = 10, text = "积分商城"),
                UiNodeSnapshot(
                    nodeId = 11,
                    children = listOf(
                        UiNodeSnapshot(nodeId = 12, text = "累计签到"),
                        UiNodeSnapshot(nodeId = 13, text = "已签到 11 天")
                        // Actual check-in button is missing!
                    )
                )
            )
        )
        val action = machine.processUiTree(targetPkg, streakOnlyTree)
        assertEquals(StateAction.None, action)
        // Must wait and eventually time out as CHECK_IN_STATE_UNKNOWN, NEVER ALREADY_COMPLETED
        scheduler.triggerTimeout()
        assertEquals(CheckinResultStatus.UNKNOWN, listener.lastStatus)
        assertFalse(listener.lastResultVerified)
        assertEquals(CheckinState.IDLE, machine.state.value)
    }

    @Test
    fun `Scenario D - unrelated 去参与 button is never clicked`() {
        val (machine, _, _) = createEngine()
        machine.startSession(targetPkg, UiLabelConfig(), "手动测试")

        machine.processUiTree(targetPkg, buildHomeTreeWithDock())
        machine.processUiTree(targetPkg, buildMembershipPageTree())

        val promoTree = UiNodeSnapshot(
            nodeId = 0,
            children = listOf(
                UiNodeSnapshot(nodeId = 10, text = "积分商城"),
                UiNodeSnapshot(
                    nodeId = 11,
                    children = listOf(
                        UiNodeSnapshot(nodeId = 12, text = "每日签到"),
                        UiNodeSnapshot(
                            nodeId = 13,
                            isClickable = true,
                            children = listOf(
                                UiNodeSnapshot(nodeId = 14, text = "签到"),
                                UiNodeSnapshot(nodeId = 15, text = "去参与", isClickable = true)
                            )
                        )
                    )
                )
            )
        )
        val action = machine.processUiTree(targetPkg, promoTree)
        assertEquals(StateAction.None, action)
    }

    @Test
    fun `ignores events from unrelated packages`() {
        val (machine, _, _) = createEngine()
        machine.startSession(targetPkg, UiLabelConfig(), "定时")

        val action = machine.processUiTree("com.other.unrelated", buildHomeTreeWithDock())
        assertEquals(StateAction.None, action)
        assertEquals(CheckinState.LAUNCHING_OPPO_MALL, machine.state.value)
    }

    @Test
    fun `stops safely on unexpected captcha dialog`() {
        val (machine, listener, _) = createEngine()
        machine.startSession(targetPkg, UiLabelConfig(), "定时")

        val captchaTree = UiNodeSnapshot(
            nodeId = 0,
            children = listOf(UiNodeSnapshot(nodeId = 1, text = "请完成滑块验证"))
        )
        val action = machine.processUiTree(targetPkg, captchaTree)
        assertEquals(StateAction.None, action)
        assertEquals(CheckinResultStatus.NEEDS_USER_ACTION, listener.lastStatus)
        assertEquals(CheckinState.IDLE, machine.state.value)
    }
}
