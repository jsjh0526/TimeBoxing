package dev.jsjh.timebox.domain.model

import dev.jsjh.timebox.feature.settings.effectiveToday
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsModelsTest {
    private val today = LocalDate.of(2026, 10, 7)

    @Test fun sundayAndMondayStartsCrossMonthsAndYearsCorrectly() {
        val day = LocalDate.of(2027, 1, 1)
        val sunday = statisticsWeek(day, StatisticsWeekStart.SUNDAY)
        val monday = statisticsWeek(day, StatisticsWeekStart.MONDAY)
        assertEquals(LocalDate.of(2026, 12, 27), sunday.start)
        assertEquals(LocalDate.of(2027, 1, 2), sunday.end)
        assertEquals(LocalDate.of(2026, 12, 28), monday.start)
        assertEquals(5, monday.includedDates.size)
        assertEquals(7, statisticsWeek(day, StatisticsWeekStart.SUNDAY, -1).includedDates.size)
    }

    @Test fun currentWeekIncludesTodayButNotFutureDays() {
        val week = statisticsWeek(today, StatisticsWeekStart.SUNDAY)
        val tasks = week.dates.associateWith { listOf(task(it)) }
        val result = calculateWeeklyStatistics(week, tasks)
        assertEquals(4, result.overall.total)
        assertEquals(today, result.days.last().date)
        assertEquals(7, week.dates.size)
        assertEquals(7, calculateWeeklyStatistics(statisticsWeek(today, StatisticsWeekStart.SUNDAY, -1), emptyMap()).days.size)
    }

    @Test(expected = IllegalArgumentException::class) fun futureWeekRequestsAreRejected() {
        statisticsWeek(today, StatisticsWeekStart.SUNDAY, 1)
    }

    @Test fun actualMidnightAndDayStartSettingsAreIndependent() {
        val before = LocalDateTime.of(2026, 10, 10, 23, 59)
        val after = before.plusMinutes(1)
        assertEquals(before.toLocalDate(), effectiveToday(3, after))
        val oldWeek = statisticsWeek(before.toLocalDate(), StatisticsWeekStart.SUNDAY)
        val newWeek = statisticsWeek(after.toLocalDate(), StatisticsWeekStart.SUNDAY)
        assertEquals(7, oldWeek.includedDates.size)
        assertEquals(1, newWeek.includedDates.size)
        assertEquals(LocalDate.of(2026, 10, 11), newWeek.start)
    }

    @Test fun countsBig3ScheduledUnscheduledCarriedAndHabitTasks() {
        val week = statisticsWeek(today, StatisticsWeekStart.SUNDAY)
        val tasks = listOf(
            task().copy(isCompleted = true, isBig3 = true, schedule = ScheduleBlock(60, 120)),
            task().copy(isBig3 = true, schedule = ScheduleBlock(90, 180), source = DailyTaskSource.CARRY_OVER),
            task().copy(isCompleted = true, source = DailyTaskSource.RECURRING),
            task().copy(title = "  "),
            task(today.plusDays(1)).copy(isCompleted = true)
        )
        val result = calculateWeeklyStatistics(week, mapOf(today to tasks))
        assertEquals(CompletionStatistics(2, 3), result.overall)
        assertEquals(67, result.overall.percentage)
        assertEquals(CompletionStatistics(1, 2), result.big3)
        assertEquals(CompletionStatistics(1, 2), result.scheduled)
        assertEquals(150L, result.plannedMinutes)
        assertEquals(60L, result.completedPlannedMinutes)
        assertEquals(1, result.carriedOverCount)
        assertEquals(result.overall.total, result.days.sumOf { it.overall.total })
    }

    @Test fun completionUndoChangesCountsAndCompletedTimeNotPlannedTime() {
        val week = statisticsWeek(today, StatisticsWeekStart.SUNDAY)
        val done = task().copy(isCompleted = true, schedule = ScheduleBlock(0, 1440))
        val completed = calculateWeeklyStatistics(week, mapOf(today to listOf(done)))
        val undone = calculateWeeklyStatistics(week, mapOf(today to listOf(done.copy(isCompleted = false))))
        assertEquals(1440L, completed.completedPlannedMinutes)
        assertEquals(0L, undone.completedPlannedMinutes)
        assertEquals(completed.plannedMinutes, undone.plannedMinutes)
        assertEquals(0, undone.overall.percentage)
    }

    @Test fun emptyPeriodHasUndefinedRatesRatherThanZeroPercent() {
        val empty = calculateWeeklyStatistics(statisticsWeek(today, StatisticsWeekStart.MONDAY), emptyMap())
        assertEquals(CompletionStatistics(0, 0), empty.overall)
        assertNull(empty.overall.percentage)
        assertNull(empty.big3.percentage)
        assertNull(empty.scheduled.percentage)
        assertEquals(0L, empty.plannedMinutes)
        assertTrue(empty.days.all { it.overall.total == 0 })
        assertEquals(13, CompletionStatistics(1, 8).percentage)
    }

    private fun task(date: LocalDate = today) = DailyTask("task", date = date, title = "Task")
}
