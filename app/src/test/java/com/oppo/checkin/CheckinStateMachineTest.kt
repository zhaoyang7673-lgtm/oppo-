package com.oppo.checkin

import com.oppo.checkin.automation.AutomationOutcomeListener
import com.oppo.checkin.automation.CheckinStateMachine
import com.oppo.checkin.automation.StateAction
import com.oppo.checkin.automation.TimeoutScheduler
import com.oppo.checkin.automation.UiNodeSnapshot
import com.oppo.checkin.data.CheckinResultStatus
import com.oppo.checkin.data.CheckinState
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
    var lastStatus: CheckinResultStatus? = null
    var lastAppLaunched = false
    var lastButtonLocated = false
    var lastResultVerified = false
    var lastReason: String? = null
    var fallbackReasons = mutableListOf<String>()

    override fun onSessionStarted() {
        startedCount++
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

    @Test
    fun `happy path navigates membership to points to single sign-in click and verifies success`() {
        val (machine, listener, _) = createEngine()
        machine.startSession(targetPkg, UiLabelConfig(), "定时")

        // Home page with non-clickable "会员" text inside clickable container
        val homeTree = UiNodeSnapshot(
            nodeId = 0,
            children = listOf(
                UiNodeSnapshot(
                    nodeId = 1,
                    isClickable = true,
                    children = listOf(UiNodeSnapshot(nodeId = 2, text = "会员", isClickable = false))
                )
            )
        )
        val action1 = machine.processUiTree(targetPkg, homeTree)
        assertEquals(StateAction.ClickNode(1, "会员"), action1)
        assertEquals(CheckinState.OPENING_POINTS_PAGE, machine.state.value)

        // Membership page with "我的积分"
        val membershipTree = UiNodeSnapshot(
            nodeId = 0,
            children = listOf(
                UiNodeSnapshot(nodeId = 3, text = "我的积分", isClickable = true)
            )
        )
        val action2 = machine.processUiTree(targetPkg, membershipTree)
        assertEquals(StateAction.ClickNode(3, "我的积分"), action2)
        assertEquals(CheckinState.CHECKING_SIGN_IN_STATUS, machine.state.value)

        // Points page with "签到"
        val pointsTree = UiNodeSnapshot(
            nodeId = 0,
            children = listOf(
                UiNodeSnapshot(nodeId = 4, text = "签到", isClickable = true)
            )
        )
        val action3 = machine.processUiTree(targetPkg, pointsTree)
        assertEquals(StateAction.ClickNode(4, "签到"), action3)
        assertEquals(CheckinState.VERIFYING_RESULT, machine.state.value)

        // Even if the same "签到" button briefly appears again, NEVER click a second time
        val duplicateAction = machine.processUiTree(targetPkg, pointsTree)
        assertEquals(StateAction.None, duplicateAction)

        // Confirmation label appears
        val successTree = UiNodeSnapshot(
            nodeId = 0,
            children = listOf(UiNodeSnapshot(nodeId = 5, text = "签到成功"))
        )
        val action4 = machine.processUiTree(targetPkg, successTree)
        assertEquals(StateAction.None, action4)
        assertEquals(CheckinState.SUCCESS, machine.state.value)
        assertEquals(CheckinResultStatus.SUCCESS, listener.lastStatus)
        assertTrue(listener.lastAppLaunched)
        assertTrue(listener.lastButtonLocated)
        assertTrue(listener.lastResultVerified)
    }

    @Test
    fun `detects already checked in and never clicks`() {
        val (machine, listener, _) = createEngine()
        machine.startSession(targetPkg, UiLabelConfig(), "手动测试")

        val homeTree = UiNodeSnapshot(
            nodeId = 0,
            children = listOf(UiNodeSnapshot(nodeId = 1, text = "会员", isClickable = true))
        )
        machine.processUiTree(targetPkg, homeTree)

        val alreadyTree = UiNodeSnapshot(
            nodeId = 0,
            children = listOf(
                UiNodeSnapshot(nodeId = 2, text = "今日已签到", isClickable = false),
                UiNodeSnapshot(nodeId = 3, text = "签到", isClickable = true)
            )
        )
        val action = machine.processUiTree(targetPkg, alreadyTree)
        assertEquals(StateAction.None, action)
        assertEquals(CheckinState.ALREADY_COMPLETED, machine.state.value)
        assertEquals(CheckinResultStatus.ALREADY_COMPLETED, listener.lastStatus)
        assertTrue(listener.lastResultVerified)
    }

    @Test
    fun `ignores events from unrelated packages`() {
        val (machine, _, _) = createEngine()
        machine.startSession(targetPkg, UiLabelConfig(), "定时")

        val otherAppTree = UiNodeSnapshot(
            nodeId = 0,
            children = listOf(UiNodeSnapshot(nodeId = 1, text = "会员", isClickable = true))
        )
        val action = machine.processUiTree("com.other.unrelated", otherAppTree)
        assertEquals(StateAction.None, action)
        assertEquals(CheckinState.WAITING_FOR_TARGET_APP, machine.state.value)
    }

    @Test
    fun `marks result UNKNOWN if verification times out after clicking sign-in`() {
        val (machine, listener, scheduler) = createEngine()
        machine.startSession(targetPkg, UiLabelConfig(), "手动测试")

        machine.processUiTree(
            targetPkg,
            UiNodeSnapshot(0, children = listOf(UiNodeSnapshot(1, text = "会员", isClickable = true)))
        )
        machine.processUiTree(
            targetPkg,
            UiNodeSnapshot(0, children = listOf(UiNodeSnapshot(2, text = "签到", isClickable = true)))
        )
        assertEquals(CheckinState.VERIFYING_RESULT, machine.state.value)

        scheduler.triggerTimeout()
        assertEquals(CheckinState.UNKNOWN, machine.state.value)
        assertEquals(CheckinResultStatus.UNKNOWN, listener.lastStatus)
        assertFalse(listener.lastResultVerified)
    }

    @Test
    fun `stops safely on unexpected login or captcha dialog`() {
        val (machine, listener, _) = createEngine()
        machine.startSession(targetPkg, UiLabelConfig(), "定时")

        val captchaTree = UiNodeSnapshot(
            nodeId = 0,
            children = listOf(UiNodeSnapshot(1, text = "请完成滑块验证"))
        )
        val action = machine.processUiTree(targetPkg, captchaTree)
        assertEquals(StateAction.None, action)
        assertEquals(CheckinState.NEEDS_USER_ACTION, machine.state.value)
        assertEquals(CheckinResultStatus.NEEDS_USER_ACTION, listener.lastStatus)
    }

    @Test
    fun `enforces max two navigation retries before stopping safely`() {
        val (machine, listener, scheduler) = createEngine()
        machine.startSession(targetPkg, UiLabelConfig(maxNavigationRetries = 2), "手动测试")

        // Enter app without finding "会员"
        machine.processUiTree(targetPkg, UiNodeSnapshot(0, children = emptyList()))
        assertEquals(CheckinState.LOCATING_MEMBERSHIP_TAB, machine.state.value)

        // Retry 1
        scheduler.triggerTimeout()
        assertEquals(CheckinState.LOCATING_MEMBERSHIP_TAB, machine.state.value)
        // Retry 2
        scheduler.triggerTimeout()
        assertEquals(CheckinState.LOCATING_MEMBERSHIP_TAB, machine.state.value)
        // Exceeded retry limit -> NEEDS_USER_ACTION
        scheduler.triggerTimeout()
        assertEquals(CheckinState.NEEDS_USER_ACTION, machine.state.value)
        assertEquals(CheckinResultStatus.NEEDS_USER_ACTION, listener.lastStatus)
    }
}
