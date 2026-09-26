package dev.jsjh.timebox.feature.timetable

import java.time.LocalDate

// The calendar and its summary both use Sunday-Saturday and stored task dates.
internal fun calendarWeekDates(calendarToday: LocalDate): List<LocalDate> {
    val sunday = calendarToday.minusDays((calendarToday.dayOfWeek.value % 7).toLong())
    return List(7) { sunday.plusDays(it.toLong()) }
}

internal fun weeklyCompletionCounts(
    weekDates: List<LocalDate>,
    countsByDate: Map<LocalDate, Pair<Int, Int>>
): Pair<Int, Int>? {
    if (weekDates.any { it !in countsByDate }) return null
    return weekDates.sumOf { countsByDate.getValue(it).first } to
        weekDates.sumOf { countsByDate.getValue(it).second }
}
