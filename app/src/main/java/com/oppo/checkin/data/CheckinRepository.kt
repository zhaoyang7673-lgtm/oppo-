package com.oppo.checkin.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

class CheckinRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<CheckinConfig> = _config.asStateFlow()

    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<ExecutionRecord>> = _history.asStateFlow()

    private val _lastSuccessDate = MutableStateFlow(prefs.getString(KEY_LAST_SUCCESS_DATE, null))
    val lastSuccessDate: StateFlow<String?> = _lastSuccessDate.asStateFlow()

    private val _nextScheduledEpochMs = MutableStateFlow(
        prefs.getLong(KEY_NEXT_SCHEDULED_MS, -1L).takeIf { it > 0L }
    )
    val nextScheduledEpochMs: StateFlow<Long?> = _nextScheduledEpochMs.asStateFlow()

    private val _usingExactAlarm = MutableStateFlow(prefs.getBoolean(KEY_USING_EXACT_ALARM, false))
    val usingExactAlarm: StateFlow<Boolean> = _usingExactAlarm.asStateFlow()

    private val _todayStatus = MutableStateFlow(computeTodayStatus(_history.value))
    val todayStatus: StateFlow<CheckinResultStatus> = _todayStatus.asStateFlow()

    fun updateConfig(transform: (CheckinConfig) -> CheckinConfig) {
        val updated = transform(_config.value)
        saveConfig(updated)
        _config.value = updated
    }

    fun setNextScheduledInfo(epochMs: Long?, isExact: Boolean) {
        prefs.edit().apply {
            if (epochMs != null) {
                putLong(KEY_NEXT_SCHEDULED_MS, epochMs)
            } else {
                remove(KEY_NEXT_SCHEDULED_MS)
            }
            putBoolean(KEY_USING_EXACT_ALARM, isExact)
        }.apply()
        _nextScheduledEpochMs.value = epochMs
        _usingExactAlarm.value = isExact
    }

    fun setTodayRunning() {
        _todayStatus.value = CheckinResultStatus.RUNNING
    }

    fun recordExecution(
        status: CheckinResultStatus,
        appLaunched: Boolean,
        buttonLocated: Boolean,
        resultVerified: Boolean,
        failureReason: String? = null,
        triggerSource: String = "定时",
        todayDate: String = LocalDate.now().toString()
    ) {
        val record = ExecutionRecord(
            id = UUID.randomUUID().toString(),
            timestampEpochMs = System.currentTimeMillis(),
            dateString = todayDate,
            status = status,
            appLaunched = appLaunched,
            buttonLocated = buttonLocated,
            resultVerified = resultVerified,
            failureReason = failureReason,
            triggerSource = triggerSource
        )

        val updatedList = (listOf(record) + _history.value).take(MAX_HISTORY_SIZE)
        saveHistory(updatedList)
        _history.value = updatedList

        if (status == CheckinResultStatus.SUCCESS || status == CheckinResultStatus.ALREADY_COMPLETED) {
            prefs.edit().putString(KEY_LAST_SUCCESS_DATE, todayDate).apply()
            _lastSuccessDate.value = todayDate
        }
        _todayStatus.value = computeTodayStatus(updatedList, todayDate)
    }

    fun hasCompletedToday(todayDate: String = LocalDate.now().toString()): Boolean {
        return _history.value.any {
            it.dateString == todayDate &&
                (it.status == CheckinResultStatus.SUCCESS ||
                    it.status == CheckinResultStatus.ALREADY_COMPLETED)
        }
    }

    fun refreshTodayStatus(todayDate: String = LocalDate.now().toString()) {
        _todayStatus.value = computeTodayStatus(_history.value, todayDate)
    }

    fun clearAllLocalData() {
        prefs.edit().clear().apply()
        _config.value = CheckinConfig()
        _history.value = emptyList()
        _lastSuccessDate.value = null
        _nextScheduledEpochMs.value = null
        _usingExactAlarm.value = false
        _todayStatus.value = CheckinResultStatus.WAITING
    }

    private fun computeTodayStatus(
        records: List<ExecutionRecord>,
        todayDate: String = LocalDate.now().toString()
    ): CheckinResultStatus {
        val todayRecords = records.filter { it.dateString == todayDate }
        if (todayRecords.isEmpty()) return CheckinResultStatus.WAITING
        val completed = todayRecords.firstOrNull {
            it.status == CheckinResultStatus.SUCCESS ||
                it.status == CheckinResultStatus.ALREADY_COMPLETED
        }
        return completed?.status ?: todayRecords.first().status
    }

    private fun loadConfig(): CheckinConfig {
        val defaults = CheckinConfig()
        val labelConfig = UiLabelConfig(
            membershipLabels = parseCsv(prefs.getString(KEY_LABELS_MEMBERSHIP, null))
                ?: defaults.labelConfig.membershipLabels,
            pointsPageLabels = parseCsv(prefs.getString(KEY_LABELS_POINTS, null))
                ?: defaults.labelConfig.pointsPageLabels,
            checkinButtonLabels = parseCsv(prefs.getString(KEY_LABELS_CHECKIN, null))
                ?: defaults.labelConfig.checkinButtonLabels,
            alreadyCheckedInLabels = parseCsv(prefs.getString(KEY_LABELS_ALREADY, null))
                ?: defaults.labelConfig.alreadyCheckedInLabels,
            successConfirmationLabels = parseCsv(prefs.getString(KEY_LABELS_SUCCESS, null))
                ?: defaults.labelConfig.successConfirmationLabels,
            stageTimeoutSeconds = prefs.getInt(KEY_STAGE_TIMEOUT_SEC, 15).coerceIn(5, 45),
            maxNavigationRetries = prefs.getInt(KEY_MAX_RETRIES, 2).coerceIn(0, 2)
        )
        return CheckinConfig(
            isEnabled = prefs.getBoolean(KEY_ENABLED, false),
            scheduleHour = prefs.getInt(KEY_HOUR, 9).coerceIn(0, 23),
            scheduleMinute = prefs.getInt(KEY_MINUTE, 0).coerceIn(0, 59),
            targetPackageName = prefs.getString(KEY_TARGET_PACKAGE, "") ?: "",
            targetAppName = prefs.getString(KEY_TARGET_APP_NAME, "") ?: "",
            accessibilityDisclosureAccepted = prefs.getBoolean(KEY_DISCLOSURE_ACCEPTED, false),
            labelConfig = labelConfig
        )
    }

    private fun saveConfig(config: CheckinConfig) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, config.isEnabled)
            .putInt(KEY_HOUR, config.scheduleHour)
            .putInt(KEY_MINUTE, config.scheduleMinute)
            .putString(KEY_TARGET_PACKAGE, config.targetPackageName)
            .putString(KEY_TARGET_APP_NAME, config.targetAppName)
            .putBoolean(KEY_DISCLOSURE_ACCEPTED, config.accessibilityDisclosureAccepted)
            .putString(KEY_LABELS_MEMBERSHIP, config.labelConfig.membershipLabels.joinToString(","))
            .putString(KEY_LABELS_POINTS, config.labelConfig.pointsPageLabels.joinToString(","))
            .putString(KEY_LABELS_CHECKIN, config.labelConfig.checkinButtonLabels.joinToString(","))
            .putString(KEY_LABELS_ALREADY, config.labelConfig.alreadyCheckedInLabels.joinToString(","))
            .putString(KEY_LABELS_SUCCESS, config.labelConfig.successConfirmationLabels.joinToString(","))
            .putInt(KEY_STAGE_TIMEOUT_SEC, config.labelConfig.stageTimeoutSeconds)
            .putInt(KEY_MAX_RETRIES, config.labelConfig.maxNavigationRetries)
            .apply()
    }

    private fun loadHistory(): List<ExecutionRecord> {
        val raw = prefs.getString(KEY_HISTORY_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val statusName = obj.optString("status", CheckinResultStatus.UNKNOWN.name)
                    val status = runCatching { CheckinResultStatus.valueOf(statusName) }
                        .getOrDefault(CheckinResultStatus.UNKNOWN)
                    add(
                        ExecutionRecord(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            timestampEpochMs = obj.optLong("ts", 0L),
                            dateString = obj.optString("date", ""),
                            status = status,
                            appLaunched = obj.optBoolean("appLaunched", false),
                            buttonLocated = obj.optBoolean("buttonLocated", false),
                            resultVerified = obj.optBoolean("resultVerified", false),
                            failureReason = obj.optString("reason", "").takeIf { it.isNotBlank() },
                            triggerSource = obj.optString("source", "定时")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveHistory(records: List<ExecutionRecord>) {
        val array = JSONArray()
        records.forEach { r ->
            val obj = JSONObject().apply {
                put("id", r.id)
                put("ts", r.timestampEpochMs)
                put("date", r.dateString)
                put("status", r.status.name)
                put("appLaunched", r.appLaunched)
                put("buttonLocated", r.buttonLocated)
                put("resultVerified", r.resultVerified)
                put("reason", r.failureReason ?: "")
                put("source", r.triggerSource)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY_JSON, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "oppo_checkin_prefs"
        private const val MAX_HISTORY_SIZE = 50

        private const val KEY_ENABLED = "enabled"
        private const val KEY_HOUR = "schedule_hour"
        private const val KEY_MINUTE = "schedule_minute"
        private const val KEY_TARGET_PACKAGE = "target_package"
        private const val KEY_TARGET_APP_NAME = "target_app_name"
        private const val KEY_DISCLOSURE_ACCEPTED = "disclosure_accepted"
        private const val KEY_LAST_SUCCESS_DATE = "last_success_date"
        private const val KEY_NEXT_SCHEDULED_MS = "next_scheduled_ms"
        private const val KEY_USING_EXACT_ALARM = "using_exact_alarm"
        private const val KEY_HISTORY_JSON = "history_json"

        private const val KEY_LABELS_MEMBERSHIP = "labels_membership"
        private const val KEY_LABELS_POINTS = "labels_points"
        private const val KEY_LABELS_CHECKIN = "labels_checkin"
        private const val KEY_LABELS_ALREADY = "labels_already"
        private const val KEY_LABELS_SUCCESS = "labels_success"
        private const val KEY_STAGE_TIMEOUT_SEC = "stage_timeout_sec"
        private const val KEY_MAX_RETRIES = "max_retries"

        fun parseCsv(raw: String?): List<String>? {
            if (raw.isNullOrBlank()) return null
            val parsed = raw.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }
            return parsed.takeIf { it.isNotEmpty() }
        }
    }
}
