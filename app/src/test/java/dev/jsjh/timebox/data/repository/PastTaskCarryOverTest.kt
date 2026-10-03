package dev.jsjh.timebox.data.repository

import dev.jsjh.timebox.data.local.entity.DailyTaskEntity
import dev.jsjh.timebox.data.local.entity.TaskTemplateEntity
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PastTaskCarryOverTest {
    private val today = LocalDate.of(2026, 10, 3)
    private val dao = FakeDailyTaskDao()
    private val templates = FakeTaskTemplateDao()
    private val repository = RoomTaskRepository(templates, dao, anchorDate = today)

    @Test
    fun includesAllPastGeneralTasksButNotHabitsCompletedBlankTodayOrFutureTasks() = runBlocking {
        dao.upsertAll(listOf(
            task("yesterday", today.minusDays(1)),
            task("two-days-ago", today.minusDays(2)),
            task("last-year", today.minusYears(1)),
            task("today", today),
            task("future", today.plusDays(1)),
            task("done", today.minusDays(2)).copy(isCompleted = true),
            task("habit", today.minusDays(2)).copy(source = "RECURRING"),
            task("template-child", today.minusDays(2)).copy(templateId = "habit"),
            task("blank", today.minusDays(2)).copy(title = "  ")
        ))

        assertEquals(listOf("last-year", "two-days-ago", "yesterday"), candidates())
        assertEquals(9, dao.count())
    }

    @Test
    fun trulyMovesTasksPreservingIdentityAndContentButClearingScheduleAndBig3() = runBlocking {
        val original = task("older", today.minusDays(2)).copy(
            note = "Keep this note", tagsSerialized = "[\"Work\",\"a|b\"]", isBig3 = true,
            startMinute = 90, endMinute = 120, reminderEnabled = true
        )
        dao.upsert(original)

        assertEquals(1, repository.carryOverPastIncompleteTasks(today, candidates()))
        assertNull(dao.getById(original.dateIso, original.id))
        val moved = checkNotNull(dao.getById(today.toString(), original.id))
        assertEquals(original.title, moved.title)
        assertEquals(original.note, moved.note)
        assertEquals(original.tagsSerialized, moved.tagsSerialized)
        assertEquals("CARRY_OVER", moved.source)
        assertFalse(moved.isBig3)
        assertFalse(moved.isCompleted)
        assertFalse(moved.reminderEnabled)
        assertNull(moved.startMinute)
        assertNull(moved.endMinute)
        assertTrue(repository.getReminderCandidates().isEmpty())
        assertTrue(candidates().isEmpty())
        assertEquals(1, dao.count())
    }

    @Test
    fun confirmsOnlyShownIdsAndRechecksTheirCurrentEligibility() = runBlocking {
        val valid = task("valid", today.minusDays(3))
        val completed = task("completed-after-dialog", today.minusDays(2))
        val habit = task("habit-after-dialog", today.minusDays(1))
        val rescheduled = task("future-after-dialog", today.minusDays(1))
        dao.upsertAll(listOf(valid, completed, habit, rescheduled))
        val confirmed = candidates()
        dao.upsert(completed.copy(isCompleted = true))
        dao.upsert(habit.copy(source = "RECURRING"))
        dao.upsert(rescheduled.copy(dateIso = today.plusDays(1).toString()))
        dao.upsert(task("arrived-after-dialog", today.minusDays(4)))

        assertEquals(1, repository.carryOverPastIncompleteTasks(today, confirmed + "valid" + "unknown"))
        assertEquals(listOf("valid"), dao.getByDate(today.toString()).map { it.id })
        assertEquals(listOf("arrived-after-dialog"), candidates())
        assertEquals(5, dao.count())
    }

    @Test
    fun oldCopiesAlreadyCarriedTodayOrIntoFutureAndCompletedCopiesDoNotResurrect() = runBlocking {
        listOf("active", "future", "done").forEach { dao.upsert(task(it, today.minusDays(4))) }
        dao.upsert(carry("active", today))
        dao.upsert(carry("future", today.plusDays(1)))
        dao.upsert(carry("done", today.minusDays(2)).copy(isCompleted = true))

        assertTrue(candidates().isEmpty())
        assertEquals(0, repository.carryOverPastIncompleteTasks(today, listOf("active", "future", "done")))
        assertEquals(6, dao.count())
    }

    @Test
    fun nestedLegacyCopiesCountOnceAndMoveOnlyTheLatestContent() = runBlocking {
        val original = task("root", today.minusDays(20))
        val first = carry(original.id, today.minusDays(19))
        val latest = carry(first.id, today.minusDays(2)).copy(title = "Edited latest title")
        dao.upsertAll(listOf(original, first, latest))

        assertEquals(listOf(latest.id), candidates())
        assertEquals(1, repository.carryOverPastIncompleteTasks(today, candidates()))
        assertEquals(1, dao.count())
        assertEquals("Edited latest title", dao.getByDate(today.toString()).single().title)
        assertEquals(0, repository.carryOverPastIncompleteTasks(today, listOf(latest.id)))

        assertEquals(1, repository.carryOverPastIncompleteTasks(today.plusDays(1), listOf(latest.id)))
        assertEquals(latest.id, dao.getAll().single().id)
        assertEquals(1, dao.count())
    }

    @Test
    fun sameTitleDoesNotMergeIndependentTasksAndCompletedHistoryIsPreserved() = runBlocking {
        val completed = task("root", today.minusDays(3)).copy(isCompleted = true)
        val pendingCopy = carry("root", today.minusDays(2)).copy(title = "Same title")
        val separate = task("separate", today.minusDays(1)).copy(title = "Same title")
        dao.upsertAll(listOf(completed, pendingCopy, separate))

        assertEquals(2, repository.carryOverPastIncompleteTasks(today, candidates()))
        assertEquals(completed, dao.getById(completed.dateIso, completed.id))
        assertEquals(2, dao.getByDate(today.toString()).size)
        assertEquals(3, dao.count())
    }

    @Test
    fun dismissingOldCarryTaskRemovesItsIncompletePredecessorsRegardlessOfDateGap() = runBlocking {
        val original = task("root", today.minusMonths(2))
        val latest = carry("root", today.minusDays(2))
        val unrelated = task("other", today.minusDays(2))
        dao.upsertAll(listOf(original, latest, unrelated))

        repository.deleteTask(today.minusDays(2), latest.id)

        assertEquals(listOf(unrelated.id), candidates())
        assertEquals(listOf(unrelated), dao.getAll())
    }

    @Test
    fun noAutomaticMoveWhenDatesChangeOrBacklogIsRead() = runBlocking {
        val original = task("pending", today.minusDays(2))
        dao.upsert(original)

        repository.getPastIncompleteTasks(today)
        repository.getPastIncompleteTasks(today.plusDays(1))
        assertEquals(0, repository.carryOverPastIncompleteTasks(today, emptyList()))

        assertEquals(listOf(original), dao.getAll())
    }

    @Test
    fun readingBacklogDoesNotMaterializeOldRecurringInstances() = runBlocking {
        templates.upsert(TaskTemplateEntity(
            id = "habit", title = "Daily habit", note = null, tagsSerialized = "[]",
            recurrenceType = "DAILY", repeatDaysSerialized = "", startDateIso = today.minusYears(1).toString(),
            defaultStartMinute = null, defaultEndMinute = null, reminderEnabled = false
        ))
        dao.upsert(task("past", today.minusDays(2)))

        assertEquals(listOf("past"), candidates())
        assertEquals(1, dao.count())
    }

    private suspend fun candidates(): List<String> = repository.getPastIncompleteTasks(today).map { it.id }

    private fun carry(parentId: String, date: LocalDate) =
        task("carry-$parentId-$date", date).copy(source = "CARRY_OVER")

    private fun task(id: String, date: LocalDate) = DailyTaskEntity(
        id = id, templateId = null, dateIso = date.toString(), title = id, note = null,
        tagsSerialized = "[]", isBig3 = false, isCompleted = false, startMinute = null,
        endMinute = null, reminderEnabled = false, source = "ONE_OFF"
    )
}
