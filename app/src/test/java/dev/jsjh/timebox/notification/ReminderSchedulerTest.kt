package dev.jsjh.timebox.notification

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ReminderSchedulerTest {
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun reminderTriggerAtMillis_keepsNormalHoursOnTaskDate() {
        val trigger = reminderTriggerAtMillis(
            date = LocalDate.of(2026, 5, 30),
            startMinute = 10 * 60,
            dayStartHour = 3,
            zoneId = zone
        )

        assertEquals(
            Instant.parse("2026-05-30T01:00:00Z").toEpochMilli(),
            trigger
        )
    }

    @Test
    fun reminderTriggerAtMillis_movesBeforeDayStartToNextCalendarDate() {
        val trigger = reminderTriggerAtMillis(
            date = LocalDate.of(2026, 5, 30),
            startMinute = 90,
            dayStartHour = 3,
            zoneId = zone
        )

        assertEquals(
            Instant.parse("2026-05-30T16:30:00Z").toEpochMilli(),
            trigger
        )
    }

    @Test
    fun reminderTriggerAtMillis_midnightStartKeepsSameCalendarDate() {
        val trigger = reminderTriggerAtMillis(
            date = LocalDate.of(2026, 5, 30),
            startMinute = 90,
            dayStartHour = 0,
            zoneId = zone
        )

        assertEquals(
            Instant.parse("2026-05-29T16:30:00Z").toEpochMilli(),
            trigger
        )
    }
}
