package dev.jsjh.timebox.feature.home

import dev.jsjh.timebox.domain.model.ScheduleBlock

private const val MinutesPerDay = 24 * 60
private const val MaxDayStartMinute = 6 * 60

internal fun appDayMinute(minute: Int, dayStartMinute: Int): Int {
    val safeDayStartMinute = dayStartMinute.coerceIn(0, MaxDayStartMinute)
    return if (safeDayStartMinute > 0 && minute < safeDayStartMinute) {
        minute + MinutesPerDay
    } else {
        minute
    }
}

internal fun appDayEndMinute(schedule: ScheduleBlock, dayStartMinute: Int): Int {
    return appDayMinute(schedule.startMinute, dayStartMinute) + schedule.durationMinutes
}
