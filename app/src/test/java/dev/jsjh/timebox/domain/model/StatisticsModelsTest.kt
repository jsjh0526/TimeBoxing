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

    @Test fun comparisonMatchesElapsedDaysForEitherWeekStartAcrossYearAndLeapDay() {
        listOf(today, LocalDate.of(2027, 1, 1), LocalDate.of(2024, 2, 29)).forEach { date ->
            StatisticsWeekStart.entries.forEach { start ->
                val current = statisticsWeek(date, start)
                val previous = statisticsComparisonWeek(current)
                assertEquals(current.includedDates.map { it.minusWeeks(1) }, previous.includedDates)
                assertEquals(current.start.dayOfWeek, previous.start.dayOfWeek)
                assertEquals(date.minusWeeks(1), previous.through)
            }
        }
    }

    @Test fun comparisonOfPastWeeksAlwaysIncludesSevenDays() {
        val current = statisticsWeek(today, StatisticsWeekStart.MONDAY, -1)
        val previous = statisticsComparisonWeek(current)
        assertEquals(7, previous.includedDates.size)
        assertEquals(current.end.minusWeeks(1), previous.through)
    }

    @Test fun comparisonExcludesLaterDaysOfLastWeekAndUsesPercentagePoints() {
        val currentWeek = statisticsWeek(today, StatisticsWeekStart.MONDAY)
        val previousWeek = statisticsComparisonWeek(currentWeek)
        val previousDay = today.minusWeeks(1)
        val tasks = mapOf(
            today to listOf(
                task().copy(isCompleted = true, isBig3 = true, source = DailyTaskSource.CARRY_OVER),
                task()
            ),
            previousDay to listOf(
                task(previousDay).copy(isCompleted = true, isBig3 = true, source = DailyTaskSource.CARRY_OVER),
                task(previousDay).copy(isBig3 = true, source = DailyTaskSource.CARRY_OVER),
                task(previousDay)
            ),
            previousDay.plusDays(1) to List(10) { task(previousDay.plusDays(1)).copy(isCompleted = true) },
            today.plusDays(1) to List(10) { task(today.plusDays(1)).copy(isCompleted = true) }
        )
        val current = calculateWeeklyStatistics(currentWeek, tasks)
        val previous = calculateWeeklyStatistics(previousWeek, tasks)
        val comparison = WeeklyStatisticsComparison(current, previous)
        assertEquals(CompletionStatistics(1, 3), previous.overall)
        assertEquals(17, comparison.overallPercentagePointChange)
        assertEquals(50, comparison.big3PercentagePointChange)
        assertEquals(-1, comparison.carriedOverChange)
        val reversedTasks = tasks.mapValues { (_, rows) -> rows.map { it.copy(isCompleted = !it.isCompleted) } }
        val reversed = WeeklyStatisticsComparison(
            calculateWeeklyStatistics(currentWeek, reversedTasks),
            calculateWeeklyStatistics(previousWeek, reversedTasks)
        )
        assertEquals(-17, reversed.overallPercentagePointChange)
        assertEquals(-50, reversed.big3PercentagePointChange)
    }

    @Test fun comparisonWithoutTasksIsUnavailableButUncompletedTasksHaveZeroRate() {
        val currentWeek = statisticsWeek(today, StatisticsWeekStart.SUNDAY)
        val previousWeek = statisticsComparisonWeek(currentWeek)
        val current = calculateWeeklyStatistics(currentWeek, mapOf(today to listOf(task())))
        val empty = calculateWeeklyStatistics(previousWeek, emptyMap())
        val missingPrevious = WeeklyStatisticsComparison(current, empty)
        assertNull(missingPrevious.overallPercentagePointChange)
        assertNull(missingPrevious.big3PercentagePointChange)
        assertNull(missingPrevious.carriedOverChange)
        val previousDay = today.minusWeeks(1)
        val previous = calculateWeeklyStatistics(previousWeek, mapOf(previousDay to listOf(task(previousDay))))
        val same = WeeklyStatisticsComparison(current, previous)
        assertEquals(0, same.overallPercentagePointChange)
        assertNull(same.big3PercentagePointChange)
        assertEquals(0, same.carriedOverChange)
        val missingCurrent = WeeklyStatisticsComparison(calculateWeeklyStatistics(currentWeek, emptyMap()), previous)
        assertNull(missingCurrent.overallPercentagePointChange)
        assertNull(missingCurrent.carriedOverChange)
    }

    @Test fun comparisonUsesWeightedWeeklyRatesAndCountsAdditionalCarryTasks() {
        val week = statisticsWeek(today, StatisticsWeekStart.MONDAY)
        val previous = statisticsComparisonWeek(week)
        val tasks = mapOf(
            week.start to List(2) { task(week.start).copy(isCompleted = true) },
            week.start.plusDays(1) to listOf(
                task(week.start.plusDays(1)).copy(source = DailyTaskSource.CARRY_OVER),
                task(week.start.plusDays(1))
            ),
            previous.start to listOf(task(previous.start).copy(isCompleted = true)),
            previous.start.plusDays(1) to List(9) { task(previous.start.plusDays(1)) }
        )
        val comparison = WeeklyStatisticsComparison(
            calculateWeeklyStatistics(week, tasks),
            calculateWeeklyStatistics(previous, tasks)
        )
        assertEquals(40, comparison.overallPercentagePointChange)
        assertEquals(1, comparison.carriedOverChange)
    }

    @Test(expected = IllegalArgumentException::class) fun mismatchedComparisonPeriodsAreRejected() {
        val currentWeek = statisticsWeek(today, StatisticsWeekStart.SUNDAY)
        val fullPreviousWeek = statisticsWeek(today, StatisticsWeekStart.SUNDAY, -1)
        WeeklyStatisticsComparison(
            calculateWeeklyStatistics(currentWeek, emptyMap()),
            calculateWeeklyStatistics(fullPreviousWeek, emptyMap())
        )
    }

    private fun task(date: LocalDate = today) = DailyTask("task", date = date, title = "Task")
}
