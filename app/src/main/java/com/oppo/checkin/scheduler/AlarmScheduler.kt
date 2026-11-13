package com.oppo.checkin.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.oppo.checkin.data.CheckinRepository
import java.time.LocalTime
import java.time.ZonedDateTime

data class ScheduleOutcome(
    val scheduled: Boolean,
    val triggerAtEpochMs: Long?,
    val isExact: Boolean
)

class AlarmScheduler(
    private val context: Context,
    private val repository: CheckinRepository
) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleNextDailyAlarm(now: ZonedDateTime = ZonedDateTime.now()): ScheduleOutcome {
        val config = repository.config.value
        cancelDailyAlarm()

        if (!config.isEnabled) {
            repository.setNextScheduledInfo(null, canScheduleExactAlarms())
            return ScheduleOutcome(scheduled = false, triggerAtEpochMs = null, isExact = false)
        }

        val nextRun = calculateNextExecutionTime(now, config.scheduleHour, config.scheduleMinute)
        val triggerAtMillis = nextRun.toInstant().toEpochMilli()
        val pendingIntent = buildAlarmPendingIntent()

        val hasExactPermission = canScheduleExactAlarms()
        val scheduledExact = if (hasExactPermission) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                true
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                false
            }
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            false
        }

        repository.setNextScheduledInfo(triggerAtMillis, scheduledExact)
        return ScheduleOutcome(
            scheduled = true,
            triggerAtEpochMs = triggerAtMillis,
            isExact = scheduledExact
        )
    }

    fun cancelDailyAlarm() {
        val pendingIntent = buildAlarmPendingIntent()
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        repository.setNextScheduledInfo(null, canScheduleExactAlarms())
    }

    fun rescheduleIfEnabled(now: ZonedDateTime = ZonedDateTime.now()): ScheduleOutcome {
        return if (repository.config.value.isEnabled) {
            scheduleNextDailyAlarm(now)
        } else {
            cancelDailyAlarm()
            ScheduleOutcome(scheduled = false, triggerAtEpochMs = null, isExact = canScheduleExactAlarms())
        }
    }

    private fun buildAlarmPendingIntent(): PendingIntent {
        val intent = Intent(context, DailyAlarmReceiver::class.java).apply {
            action = ACTION_DAILY_CHECKIN
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DAILY_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_DAILY_CHECKIN = "com.oppo.checkin.ACTION_DAILY_CHECKIN"
        private const val REQUEST_CODE_DAILY_ALARM = 3001

        /**
         * Calculates the next local execution ZonedDateTime, properly handling
         * day/month/year rollovers and daylight saving time transitions.
         */
        fun calculateNextExecutionTime(
            now: ZonedDateTime,
            hour: Int,
            minute: Int
        ): ZonedDateTime {
            val targetTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            val todayCandidate = ZonedDateTime.of(now.toLocalDate(), targetTime, now.zone)
            return if (todayCandidate.isAfter(now)) {
                todayCandidate
            } else {
                ZonedDateTime.of(now.toLocalDate().plusDays(1), targetTime, now.zone)
            }
        }
    }
}
