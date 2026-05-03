package dev.jsjh.timebox.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class RecurrenceType {
    DAILY,
    WEEKDAYS,
    CUSTOM
}

data class RecurrenceRule(
    val type: RecurrenceType,
    val repeatDays: Set<DayOfWeek> = emptySet()
)

fun RecurrenceRule.occursOn(dayOfWeek: DayOfWeek): Boolean = when (type) {
    RecurrenceType.DAILY -> true
    RecurrenceType.WEEKDAYS -> {
        if (repeatDays.isNotEmpty()) {
            dayOfWeek in repeatDays
        } else {
            dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        }
    }
    RecurrenceType.CUSTOM -> dayOfWeek in repeatDays
}

data class ScheduleBlock(
    val startMinute: Int,
    val endMinute: Int,
    val reminderEnabled: Boolean = false
) {
    init {
        require(startMinute in 0..(24 * 60))
        require(endMinute in 0..(24 * 60))
        require(endMinute > startMinute)
    }

    val durationMinutes: Int
        get() = endMinute - startMinute
}

data class TaskTemplate(
    val id: String,
    val title: String,
    val note: String? = null,
    val tags: List<String> = emptyList(),
    val recurrenceRule: RecurrenceRule? = null,
    val defaultSchedule: ScheduleBlock? = null
)

fun TaskTemplate.occursOn(dayOfWeek: DayOfWeek): Boolean {
    return recurrenceRule?.occursOn(dayOfWeek) ?: false
}

enum class DailyTaskSource {
    ONE_OFF,
    RECURRING,
    CARRY_OVER
}

data class DailyTask(
    val id: String,
    val templateId: String? = null,
    val date: LocalDate,
    val title: String,
    val note: String? = null,
    val tags: List<String> = emptyList(),
    val isBig3: Boolean = false,
    val isCompleted: Boolean = false,
    val schedule: ScheduleBlock? = null,
    val source: DailyTaskSource = DailyTaskSource.ONE_OFF
)

data class TaskEditInput(
    val taskId: String? = null,
    val templateId: String? = null,
    val date: LocalDate,
    val title: String,
    val note: String? = null,
    val tags: List<String> = emptyList(),
    val isBig3: Boolean = false,
    val recurrenceRule: RecurrenceRule? = null,
    val schedule: ScheduleBlock? = null
)

val DailyTask.startTimeOrNull: LocalTime?
    get() = schedule?.let { LocalTime.of(it.startMinute / 60, it.startMinute % 60) }

val DailyTask.endTimeOrNull: LocalTime?
    get() = schedule?.let { LocalTime.of(it.endMinute / 60, it.endMinute % 60) }
