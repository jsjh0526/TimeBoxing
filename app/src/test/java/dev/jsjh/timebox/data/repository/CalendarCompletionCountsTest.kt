package dev.jsjh.timebox.data.repository

import dev.jsjh.timebox.data.local.entity.DailyTaskEntity
import dev.jsjh.timebox.data.local.entity.TaskTemplateEntity
import dev.jsjh.timebox.domain.model.DailyTaskSource
import dev.jsjh.timebox.feature.timetable.calendarWeekDates
import dev.jsjh.timebox.feature.timetable.weeklyCompletionCounts
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarCompletionCountsTest {
    private val habitStart = LocalDate.of(2026, 9, 23)

    @Test
    fun weeklySummaryCountsUnscheduledTasksAndUnmaterializedHabitsWithoutWritingRows() = runBlocking {
        val templates = FakeTaskTemplateDao()
        val tasks = FakeDailyTaskDao()
        templates.upsert(dailyHabit())
        tasks.upsertAll(listOf(
            task("one-off", habitStart.minusDays(2), completed = true),
            task("future", habitStart.plusDays(2)),
            task("habit-completed", habitStart, completed = true, templateId = "habit", source = DailyTaskSource.RECURRING),
            task("carried", habitStart.plusDays(3), completed = true, source = DailyTaskSource.CARRY_OVER),
            task("blank", habitStart).copy(title = " "),
            task("stale", habitStart, templateId = "removed-habit", source = DailyTaskSource.RECURRING),
            task("outside-week", habitStart.plusWeeks(1), completed = true)
        ))
        val repository = RoomTaskRepository(templates, tasks, anchorDate = habitStart)
        val rowsBefore = tasks.getAll()
        val week = calendarWeekDates(habitStart)

        val counts = repository.getTaskCompletionCounts(week)

        assertEquals(3 to 7, weeklyCompletionCounts(week, counts))
        assertEquals(0 to 0, counts[habitStart.minusDays(1)])
        assertEquals(0 to 1, counts[habitStart.plusDays(1)])
        assertEquals(0 to 2, counts[habitStart.plusDays(2)])
        assertEquals(rowsBefore, tasks.getAll())
        assertEquals(listOf(dailyHabit()), templates.getAll())
    }

    @Test
    fun newHabitDoesNotAddTasksToAnEarlierWeek() = runBlocking {
        val templates = FakeTaskTemplateDao().apply { upsert(dailyHabit()) }
        val tasks = FakeDailyTaskDao()
        val repository = RoomTaskRepository(templates, tasks, anchorDate = habitStart)
        val previousWeek = calendarWeekDates(habitStart.minusWeeks(1))

        val counts = repository.getTaskCompletionCounts(previousWeek)

        assertEquals(0 to 0, weeklyCompletionCounts(previousWeek, counts))
        assertEquals(0, tasks.count())
    }

    private fun dailyHabit() = TaskTemplateEntity(
        id = "habit",
        title = "Daily habit",
        note = null,
        tagsSerialized = "",
        recurrenceType = "DAILY",
        repeatDaysSerialized = "",
        startDateIso = habitStart.toString(),
        defaultStartMinute = null,
        defaultEndMinute = null,
        reminderEnabled = false
    )

    private fun task(
        id: String,
        date: LocalDate,
        completed: Boolean = false,
        templateId: String? = null,
        source: DailyTaskSource = DailyTaskSource.ONE_OFF
    ) = DailyTaskEntity(
        id = id,
        templateId = templateId,
        dateIso = date.toString(),
        title = id,
        note = null,
        tagsSerialized = "",
        isBig3 = false,
        isCompleted = completed,
        startMinute = null,
        endMinute = null,
        reminderEnabled = false,
        source = source.name
    )
}
