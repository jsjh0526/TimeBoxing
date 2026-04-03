package com.example.timeboxing.data.repository

import com.example.timeboxing.domain.model.RecurrenceRule
import com.example.timeboxing.domain.model.RecurrenceType
import com.example.timeboxing.domain.model.TaskEditInput
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryTaskRepositoryTest {

    private val anchorDate: LocalDate = LocalDate.of(2026, 3, 29)

    @Test
    fun `cached future carry-over is removed when source task is completed`() {
        val repository = InMemoryTaskRepository(anchorDate = anchorDate)
        val today = anchorDate
        val tomorrow = today.plusDays(1)
        val title = "sync-check-complete"

        val created = repository.addTask(today, title)
        val before = repository.getTasks(tomorrow).filter { it.title == title }
        assertEquals(1, before.size)

        repository.toggleCompleted(today, created.id)

        val after = repository.getTasks(tomorrow).filter { it.title == title }
        assertTrue(after.isEmpty())
    }

    @Test
    fun `cached future carry-over reflects edited task fields`() {
        val repository = InMemoryTaskRepository(anchorDate = anchorDate)
        val today = anchorDate
        val tomorrow = today.plusDays(1)
        val originalTitle = "sync-check-edit-original"
        val updatedTitle = "sync-check-edit-updated"

        val created = repository.addTask(today, originalTitle)
        repository.getTasks(tomorrow)

        repository.upsertTask(
            TaskEditInput(
                taskId = created.id,
                date = today,
                title = updatedTitle,
                note = "updated note",
                tags = listOf("Updated"),
                recurrenceRule = null
            )
        )

        val carried = repository.getTasks(tomorrow).single { it.title == updatedTitle }
        assertEquals("updated note", carried.note)
        assertEquals(listOf("Updated"), carried.tags)
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
