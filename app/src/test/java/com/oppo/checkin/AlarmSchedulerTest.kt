package com.oppo.checkin

import com.oppo.checkin.scheduler.AlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmSchedulerTest {

    @Test
    fun `schedules same day when current time is before target`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = ZonedDateTime.of(2026, 11, 13, 8, 30, 0, 0, zone)
        val next = AlarmScheduler.calculateNextExecutionTime(now, 9, 0)
        assertEquals(ZonedDateTime.of(2026, 11, 13, 9, 0, 0, 0, zone), next)
    }

    @Test
    fun `schedules next day when current time is equal to or after target`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val exact = ZonedDateTime.of(2026, 11, 13, 9, 0, 0, 0, zone)
        val nextFromExact = AlarmScheduler.calculateNextExecutionTime(exact, 9, 0)
        assertEquals(ZonedDateTime.of(2026, 11, 14, 9, 0, 0, 0, zone), nextFromExact)

        val after = ZonedDateTime.of(2026, 11, 13, 21, 15, 0, 0, zone)
        val nextFromAfter = AlarmScheduler.calculateNextExecutionTime(after, 9, 0)
        assertEquals(ZonedDateTime.of(2026, 11, 14, 9, 0, 0, 0, zone), nextFromAfter)
    }

    @Test
    fun `handles month and year transitions correctly`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val janEnd = ZonedDateTime.of(2026, 1, 31, 10, 0, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 2, 1, 9, 0, 0, 0, zone),
            AlarmScheduler.calculateNextExecutionTime(janEnd, 9, 0)
        )

        val yearEnd = ZonedDateTime.of(2026, 12, 31, 9, 1, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2027, 1, 1, 9, 0, 0, 0, zone),
            AlarmScheduler.calculateNextExecutionTime(yearEnd, 9, 0)
        )
    }

    @Test
    fun `handles daylight saving time transitions cleanly`() {
        val laZone = ZoneId.of("America/Los_Angeles")
        // Spring forward on 2026-03-08: 02:00 -> 03:00 PDT
        val beforeDst = ZonedDateTime.of(2026, 3, 7, 10, 0, 0, 0, laZone)
        val nextDst = AlarmScheduler.calculateNextExecutionTime(beforeDst, 9, 0)
        assertEquals(2026, nextDst.year)
        assertEquals(3, nextDst.monthValue)
        assertEquals(8, nextDst.dayOfMonth)
        assertEquals(9, nextDst.hour)
        assertEquals(0, nextDst.minute)
        assertEquals("-07:00", nextDst.offset.id)
    }

    @Test
    fun `preserves timezone when zone changes`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val nowTokyo = ZonedDateTime.of(2026, 11, 13, 8, 59, 0, 0, tokyo)
        val nextTokyo = AlarmScheduler.calculateNextExecutionTime(nowTokyo, 9, 0)
        assertEquals(tokyo, nextTokyo.zone)
        assertEquals(9, nextTokyo.hour)
    }
}
