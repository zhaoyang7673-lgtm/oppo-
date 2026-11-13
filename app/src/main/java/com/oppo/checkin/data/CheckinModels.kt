package com.oppo.checkin.data

enum class CheckinState {
    IDLE,
    WAITING_FOR_TARGET_APP,
    OPENING_HOME_PAGE,
    LOCATING_MEMBERSHIP_TAB,
    OPENING_POINTS_PAGE,
    CHECKING_SIGN_IN_STATUS,
    CLICKING_SIGN_IN,
    VERIFYING_RESULT,
    SUCCESS,
    ALREADY_COMPLETED,
    NEEDS_USER_ACTION,
    FAILED,
    UNKNOWN
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
    val pointsPageLabels: List<String> = listOf("我的积分", "积分"),
    val checkinButtonLabels: List<String> = listOf("签到", "每日签到"),
    val alreadyCheckedInLabels: List<String> = listOf("已签到", "今日已签到", "已完成", "已领取"),
    val successConfirmationLabels: List<String> = listOf("签到成功", "已完成", "已领取", "已签到", "今日已签到"),
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

data class AppCandidate(
    val appName: String,
    val packageName: String,
    val isLikelyOppoApp: Boolean
)
