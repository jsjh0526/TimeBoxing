package dev.jsjh.timebox.feature.timetable

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarWeekTest {
    @Test
    fun sundayStartsANewWeekWhileSaturdayStillBelongsToPreviousWeek() {
        val saturday = calendarWeekDates(LocalDate.of(2026, 9, 26))
        val sunday = calendarWeekDates(LocalDate.of(2026, 9, 27))

        assertEquals(LocalDate.of(2026, 9, 20), saturday.first())
        assertEquals(LocalDate.of(2026, 9, 26), saturday.last())
        assertEquals(LocalDate.of(2026, 9, 27), sunday.first())
        assertEquals(LocalDate.of(2026, 10, 3), sunday.last())
        assertEquals(7, sunday.distinct().size)
    }

    @Test
    fun weekCanSpanTwoYears() {
        val dates = calendarWeekDates(LocalDate.of(2027, 1, 1))

        assertEquals(LocalDate.of(2026, 12, 27), dates.first())
        assertEquals(LocalDate.of(2027, 1, 2), dates.last())
        assertEquals(7, dates.size)
    }

    @Test
    fun leapDayIsIncludedInItsCalendarWeek() {
        val dates = calendarWeekDates(LocalDate.of(2028, 3, 1))

        assertEquals(LocalDate.of(2028, 2, 27), dates.first())
        assertEquals(LocalDate.of(2028, 3, 4), dates.last())
        assertEquals(LocalDate.of(2028, 2, 29), dates[2])
    }

    @Test
    fun summaryIncludesEntireWeekButExcludesOtherCalendarCells() {
        val dates = calendarWeekDates(LocalDate.of(2026, 9, 23))
        val counts = listOf(2 to 3, 3 to 4, 4 to 4, 3 to 5, 2 to 4, 3 to 3, 1 to 2)
        val byDate = dates.zip(counts).toMap() +
            (dates.first().minusDays(1) to (100 to 100)) +
            (dates.last().plusDays(1) to (200 to 200))

        assertEquals(18 to 25, weeklyCompletionCounts(dates, byDate))
    }

    @Test
    fun emptyWeekIsZeroOutOfZero() {
        val dates = calendarWeekDates(LocalDate.of(2026, 9, 23))

        assertEquals(0 to 0, weeklyCompletionCounts(dates, dates.associateWith { 0 to 0 }))
    }

    @Test
    fun missingDataIsNotDisplayedAsAnEmptyOrPartialWeek() {
        val dates = calendarWeekDates(LocalDate.of(2026, 9, 23))

        assertNull(weeklyCompletionCounts(dates, emptyMap()))
        assertNull(weeklyCompletionCounts(dates, dates.dropLast(1).associateWith { 1 to 2 }))
    }
}
