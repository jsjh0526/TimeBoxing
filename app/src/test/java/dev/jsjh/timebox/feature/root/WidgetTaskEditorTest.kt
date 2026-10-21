package dev.jsjh.timebox.feature.root

import dev.jsjh.timebox.data.local.entity.DailyTaskEntity
import dev.jsjh.timebox.data.local.entity.TaskTemplateEntity
import dev.jsjh.timebox.data.repository.FakeDailyTaskDao
import dev.jsjh.timebox.data.repository.FakeTaskTemplateDao
import dev.jsjh.timebox.data.repository.RoomTaskRepository
import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.repository.TaskRepository
import dev.jsjh.timebox.widget.WidgetTaskTarget
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetTaskEditorTest {
    private val today = LocalDate.of(2026, 10, 21)
    private val dao = FakeDailyTaskDao()
    private val templates = FakeTaskTemplateDao()
    private val local = RoomTaskRepository(templates, dao)

    @Test fun opensNormalBig3AndCompletedTasksOnTheirOriginalDateWithoutWriting() = runBlocking {
        val date = today.minusDays(1)
        dao.upsertAll(listOf(task("normal", date), task("big3", date).copy(isBig3 = true),
            task("completed", date).copy(isCompleted = true)))
        val state = ready(CoroutineScope(coroutineContext))
        val before = dao.getAll()
        before.forEach { stored ->
            assertTrue(state.openWidgetTaskEditor(target(stored.id, date), "account-a"))
            assertEquals(AppTab.TODO, state.currentTab)
            val draft = state.editorDraft!!
            assertEquals(stored.id, draft.taskId)
            assertEquals(date, draft.date)
            assertEquals(stored.title, draft.title)
            assertEquals("keep note", draft.note)
            assertEquals(listOf("work"), draft.tags)
            assertEquals(stored.isBig3, draft.isBig3)
            assertEquals("01:30", draft.startText)
            assertEquals("02:00", draft.endText)
            assertTrue(draft.alertEnabled)
        }
        assertEquals(before, dao.getAll())
    }

    @Test fun wrongAccountAndMalformedTargetDoNotReadOrCreateAnEditor() = runBlocking {
        var reads = 0
        val counted = object : TaskRepository by local {
            override suspend fun getStoredTask(date: LocalDate, taskId: String): DailyTask? {
                reads++
                return local.getStoredTask(date, taskId)
            }
        }
        dao.upsert(task("same-id", today))
        val state = ready(CoroutineScope(coroutineContext), counted)
        state.openNewTaskEditor()
        assertFalse(state.openWidgetTaskEditor(target("same-id", today), "account-b"))
        assertNull(state.editorDraft)
        assertFalse(state.openWidgetTaskEditor(null, "account-a"))
        assertEquals(0, reads)
        assertEquals(AppTab.TODO, state.currentTab)
        assertEquals(1, dao.count())
    }

    @Test fun deletedOrMovedTaskDoesNotOpenAnotherDateOrANewTask() = runBlocking {
        dao.upsert(task("moved", today))
        val state = ready(CoroutineScope(coroutineContext))
        val before = dao.getAll()
        listOf("deleted", "moved").forEach { id ->
            assertFalse(state.openWidgetTaskEditor(target(id, today.minusDays(1)), "account-a"))
            assertNull(state.editorDraft)
        }
        assertEquals(before, dao.getAll())
    }

    @Test fun missingRecurringTaskIsNotMaterializedOrReplacedWithATemplateEditor() = runBlocking {
        templates.upsert(TaskTemplateEntity(
            id = "habit", title = "Habit", note = null, tagsSerialized = "[]", recurrenceType = "DAILY",
            repeatDaysSerialized = "", startDateIso = today.minusDays(30).toString(),
            defaultStartMinute = null, defaultEndMinute = null, reminderEnabled = false
        ))
        val state = ready(CoroutineScope(coroutineContext))
        val before = dao.getAll()
        val beforeTemplates = templates.getAll()
        listOf("habit-${today.minusDays(2)}", "template-habit").forEach { id ->
            assertFalse(state.openWidgetTaskEditor(target(id, today.minusDays(2)), "account-a"))
            assertNull(state.editorDraft)
        }
        assertEquals(before, dao.getAll())
        assertEquals(beforeTemplates, templates.getAll())
    }

    @Test fun lookupFailureLeavesTodoUsableWithoutAnEmptyEditor() = runBlocking {
        val failing = object : TaskRepository by local {
            override suspend fun getStoredTask(date: LocalDate, taskId: String): DailyTask? = error("Read failed")
        }
        val state = ready(CoroutineScope(coroutineContext), failing)
        assertFalse(state.openWidgetTaskEditor(target("A", today), "account-a"))
        assertNull(state.editorDraft)
        assertEquals(AppTab.TODO, state.currentTab)
    }

    @Test fun openingAMissingHabitTodayDoesNotRefreshAndRecreateIt() = runBlocking {
        templates.upsert(TaskTemplateEntity("habit", "Habit", null, "", "DAILY", "", today.toString(),
            null, null, false))
        val state = ready(CoroutineScope(coroutineContext))
        dao.deleteByDateAndTemplateId(today.toString(), "habit")
        assertFalse(state.openWidgetTaskEditor(target("habit-$today", today), "account-a"))
        assertNull(state.editorDraft)
        assertEquals(0, dao.count())
    }

    @Test fun storedLookupDoesNotInitializeTutorialData() = runBlocking {
        val uninitialized = RoomTaskRepository(templates, dao, seedInitialData = true)
        assertNull(uninitialized.getStoredTask(today, "missing"))
        assertEquals(0, dao.count())
        assertEquals(0, templates.count())
    }

    @Test fun cancelledOlderRequestCannotOverwriteTheNextTaskEditor() = runBlocking {
        dao.upsertAll(listOf(task("A", today), task("B", today)))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val delayed = object : TaskRepository by local {
            override suspend fun getStoredTask(date: LocalDate, taskId: String): DailyTask? {
                if (taskId == "A") withContext(NonCancellable) {
                    started.complete(Unit)
                    release.await()
                }
                return local.getStoredTask(date, taskId)
            }
        }
        val state = ready(CoroutineScope(coroutineContext), delayed)
        val oldRequest = launch { state.openWidgetTaskEditor(target("A", today), "account-a") }
        started.await()
        oldRequest.cancel()
        assertTrue(state.openWidgetTaskEditor(target("B", today), "account-a"))
        release.complete(Unit)
        oldRequest.join()
        assertEquals("B", state.editorDraft?.taskId)
    }

    private suspend fun ready(scope: CoroutineScope, repository: TaskRepository = local): TimeBoxingAppState {
        val state = TimeBoxingAppState(repository, today, scope)
        withTimeout(5_000) { while (state.calendarStatsRevision == 0) delay(1) }
        return state
    }

    private fun target(id: String, date: LocalDate) = WidgetTaskTarget("account-a", id, date)

    private fun task(id: String, date: LocalDate) = DailyTaskEntity(
        id, null, date.toString(), id, "keep note", "work", false, false, 90, 120, true, "ONE_OFF"
    )
}
