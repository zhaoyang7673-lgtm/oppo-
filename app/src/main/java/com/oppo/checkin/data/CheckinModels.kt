package com.oppo.checkin.data

enum class CheckinState {
    IDLE,
    STARTING,
    LAUNCHING_OPPO_MALL,
    FINDING_MEMBERSHIP_TAB,
    VERIFYING_MEMBERSHIP_PAGE,
    FINDING_MY_POINTS,
    VERIFYING_POINTS_PAGE,
    LOCATING_DAILY_CHECK_IN_CARD,
    DETECTING_CHECK_IN_BUTTON,
    TODAY_NOT_CHECKED_IN,
    TODAY_ALREADY_CHECKED_IN,
    CHECK_IN_STATE_UNKNOWN,
    CLICKING_CHECK_IN_ONCE,
    VERIFYING_CHECK_IN_RESULT,
    CHECK_IN_SUCCESS,
    CHECK_IN_SUCCESS_WITH_LIMITED_VERIFICATION,
    CHECK_IN_RESULT_UNKNOWN,
    NEEDS_USER_ACTION,
    FAILED,
    TIMEOUT,
    CLEANUP_AND_RETURN
}

enum class CheckinResultStatus(val label: String) {
    WAITING("等待执行"),
    RUNNING("执行中"),
    SUCCESS("签到成功"),
    ALREADY_COMPLETED("今日已签到"),
    NEEDS_USER_ACTION("需要手动操作"),
    PERMISSION_MISSING("权限不足"),
    FAILED("执行失败"),
    UNKNOWN("结果未知")
}

data class UiLabelConfig(
    val membershipLabels: List<String> = listOf("会员"),
    val membershipPageAnchors: List<String> = listOf("钻卡会员", "会员专属权益", "尊享积分", "用机服务", "购物特权"),
    val dockSiblingLabels: List<String> = listOf("首页", "选购", "会员", "服务", "我的"),
    val pointsPageLabels: List<String> = listOf("我的积分"),
    val pointsMallAnchors: List<String> = listOf("积分商城"),
    val checkinCardAnchors: List<String> = listOf("累计签到", "签到提醒", "每日签到"),
    val checkinButtonLabels: List<String> = listOf("签到"),
    val alreadyCheckedInLabels: List<String> = listOf("已签到"),
    val successConfirmationLabels: List<String> = listOf("签到成功", "已完成", "已领取"),
    val excludedActionLabels: List<String> = listOf("去参与", "立即兑换", "立即购买", "去购买"),
    val stageTimeoutSeconds: Int = 15,
    val maxNavigationRetries: Int = 2
)

data class CheckinConfig(
    val isEnabled: Boolean = false,
    val scheduleHour: Int = 9,
    val scheduleMinute: Int = 0,
    val targetPackageName: String = "",
    val targetAppName: String = "",
    val accessibilityDisclosureAccepted: Boolean = false,
    val debugTestModeEnabled: Boolean = false,
    val labelConfig: UiLabelConfig = UiLabelConfig()
)

data class ExecutionRecord(
    val id: String,
    val timestampEpochMs: Long,
    val dateString: String,
    val status: CheckinResultStatus,
    val appLaunched: Boolean,
    val buttonLocated: Boolean,
    val resultVerified: Boolean,
    val failureReason: String? = null,
    val triggerSource: String = "定时"
)

data class DiagnosticLogEntry(
    val timestampEpochMs: Long,
    val state: CheckinState,
    val expectedAnchor: String,
    val detectedAnchor: String?,
    val buttonText: String?,
    val hierarchyValidated: Boolean?,
    val durationMs: Long,
    val note: String
)

data class AppCandidate(
    val appName: String,
    val packageName: String,
    val isLikelyOppoApp: Boolean
)
