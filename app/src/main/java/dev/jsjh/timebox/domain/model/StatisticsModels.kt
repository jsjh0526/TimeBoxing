package dev.jsjh.timebox.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

enum class StatisticsWeekStart(val day: DayOfWeek) {
    SUNDAY(DayOfWeek.SUNDAY),
    MONDAY(DayOfWeek.MONDAY)
}

data class StatisticsWeek(val start: LocalDate, val end: LocalDate, val through: LocalDate) {
    val dates: List<LocalDate> get() = List(7) { start.plusDays(it.toLong()) }
    val includedDates: List<LocalDate> get() = dates.filter { !it.isAfter(through) }
}

fun statisticsWeek(today: LocalDate, weekStart: StatisticsWeekStart, offset: Int = 0): StatisticsWeek {
    require(offset <= 0)
    val daysFromStart = (today.dayOfWeek.value - weekStart.day.value + 7) % 7
    val start = today.minusDays(daysFromStart.toLong()).plusWeeks(offset.toLong())
    val end = start.plusDays(6)
    return StatisticsWeek(start, end, minOf(today, end))
}

data class CompletionStatistics(val completed: Int, val total: Int) {
    val percentage: Int? get() = if (total == 0) null else (completed.toDouble() * 100 / total).roundToInt()
}

data class DailyStatistics(
    val date: LocalDate,
    val overall: CompletionStatistics,
    val big3: CompletionStatistics,
    val scheduled: CompletionStatistics,
    val plannedMinutes: Long,
    val completedPlannedMinutes: Long,
    val carriedOverCount: Int
)

data class WeeklyStatistics(val week: StatisticsWeek, val days: List<DailyStatistics>) {
    val overall = CompletionStatistics(days.sumOf { it.overall.completed }, days.sumOf { it.overall.total })
    val big3 = CompletionStatistics(days.sumOf { it.big3.completed }, days.sumOf { it.big3.total })
    val scheduled = CompletionStatistics(days.sumOf { it.scheduled.completed }, days.sumOf { it.scheduled.total })
    val plannedMinutes = days.sumOf { it.plannedMinutes }
    val completedPlannedMinutes = days.sumOf { it.completedPlannedMinutes }
    val carriedOverCount = days.sumOf { it.carriedOverCount }
}

fun calculateWeeklyStatistics(
    week: StatisticsWeek,
    tasksByDate: Map<LocalDate, List<DailyTask>>
): WeeklyStatistics = WeeklyStatistics(week, week.includedDates.map { date ->
    val tasks = tasksByDate[date].orEmpty().filter { it.date == date && it.title.isNotBlank() }
    val big3 = tasks.filter { it.isBig3 }
    val scheduled = tasks.filter { it.schedule != null }
    DailyStatistics(
        date = date,
        overall = CompletionStatistics(tasks.count { it.isCompleted }, tasks.size),
        big3 = CompletionStatistics(big3.count { it.isCompleted }, big3.size),
        scheduled = CompletionStatistics(scheduled.count { it.isCompleted }, scheduled.size),
        plannedMinutes = scheduled.sumOf { it.schedule!!.durationMinutes.toLong() },
        completedPlannedMinutes = scheduled.filter { it.isCompleted }.sumOf { it.schedule!!.durationMinutes.toLong() },
        carriedOverCount = tasks.count { it.source == DailyTaskSource.CARRY_OVER }
    )
})
