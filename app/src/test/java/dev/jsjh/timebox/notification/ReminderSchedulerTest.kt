package dev.jsjh.timebox.notification

import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.model.ScheduleBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun shouldScheduleReminder_usesUpdatedDayStartForExistingTask() {
        val task = reminderTask(
            date = LocalDate.of(2026, 5, 30),
            startMinute = 90
        )
        val now = Instant.parse("2026-05-30T00:00:00Z").toEpochMilli()

        assertFalse(
            shouldScheduleReminder(task, ReminderSettings(), dayStartHour = 0, nowMillis = now, zoneId = zone)
        )
        assertTrue(
            shouldScheduleReminder(task, ReminderSettings(), dayStartHour = 3, nowMillis = now, zoneId = zone)
        )
    }

    @Test
    fun shouldScheduleReminder_excludesCompletedOrDisabledTasks() {
        val task = reminderTask(
            date = LocalDate.of(2026, 5, 30),
            startMinute = 10 * 60
        )
        val now = Instant.parse("2026-05-29T00:00:00Z").toEpochMilli()

        assertFalse(
            shouldScheduleReminder(
                task.copy(isCompleted = true),
                ReminderSettings(),
                dayStartHour = 3,
                nowMillis = now,
                zoneId = zone
            )
        )
        assertFalse(
            shouldScheduleReminder(
                task,
                ReminderSettings(notificationsEnabled = false),
                dayStartHour = 3,
                nowMillis = now,
                zoneId = zone
            )
        )
    }

    private fun reminderTask(date: LocalDate, startMinute: Int): DailyTask = DailyTask(
        id = "task-$date-$startMinute",
        date = date,
        title = "Reminder",
        schedule = ScheduleBlock(
            startMinute = startMinute,
            endMinute = startMinute + 30,
            reminderEnabled = true
        )
    )
}
