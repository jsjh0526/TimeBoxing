package dev.jsjh.timebox.data.repository

import dev.jsjh.timebox.domain.model.DailyTaskSource
import dev.jsjh.timebox.domain.model.RecurrenceRule
import dev.jsjh.timebox.domain.model.RecurrenceType
import dev.jsjh.timebox.domain.model.TaskEditInput
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryTaskRepositoryTest {

    private val anchorDate: LocalDate = LocalDate.of(2026, 3, 29)

    @Test
    fun `manual carry-over copies yesterday incomplete one-off tasks to today`() {
        val repository = InMemoryTaskRepository(anchorDate = anchorDate)
        val yesterday = anchorDate
        val today = yesterday.plusDays(1)
        val title = "carry-me"

        repository.addTask(yesterday, title)

        repository.carryOverIncompleteTasks(yesterday, today)

        val carried = repository.getTasks(today).single { it.title == title }
        assertEquals(DailyTaskSource.CARRY_OVER, carried.source)
        assertEquals(null, carried.schedule)
        assertEquals(false, carried.isCompleted)
        assertEquals(false, carried.isBig3)
    }

    @Test
    fun `manual carry-over skips completed and recurring tasks`() {
        val repository = InMemoryTaskRepository(anchorDate = anchorDate)
        val yesterday = anchorDate
        val today = yesterday.plusDays(1)
        val completed = repository.addTask(yesterday, "done-already")
        repository.toggleCompleted(yesterday, completed.id)

        repository.carryOverIncompleteTasks(yesterday, today)

        val carriedTitles = repository.getTasks(today)
            .filter { it.source == DailyTaskSource.CARRY_OVER }
            .map { it.title }
        assertTrue("done-already" !in carriedTitles)
    }

    @Test
    fun `editing recurring task updates cached recurring instances on other dates`() {
        val repository = InMemoryTaskRepository(anchorDate = anchorDate)
        val today = anchorDate
        val tomorrow = today.plusDays(1)
        val recurringToday = repository.getTasks(today).first { it.templateId == "tpl-standup" }

        repository.getTasks(tomorrow)
        repository.upsertTask(
            TaskEditInput(
                taskId = recurringToday.id,
                templateId = recurringToday.templateId,
                date = today,
                title = "standup-updated",
                note = "new recurring note",
                tags = listOf("Meeting", "Updated"),
                recurrenceRule = RecurrenceRule(RecurrenceType.DAILY),
                schedule = recurringToday.schedule
            )
        )

        val tomorrowRecurring = repository.getTasks(tomorrow).single { it.templateId == "tpl-standup" }
        assertEquals("standup-updated", tomorrowRecurring.title)
        assertEquals("new recurring note", tomorrowRecurring.note)
        assertEquals(listOf("Meeting", "Updated"), tomorrowRecurring.tags)
    }
}
