package dev.jsjh.timebox.feature.root

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import dev.jsjh.timebox.data.local.entity.DailyTaskEntity
import dev.jsjh.timebox.data.repository.FakeDailyTaskDao
import dev.jsjh.timebox.data.repository.FakeTaskTemplateDao
import dev.jsjh.timebox.data.repository.RoomTaskRepository
import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.model.DailyTaskSource
import dev.jsjh.timebox.domain.model.RecurrenceRule
import dev.jsjh.timebox.domain.model.RecurrenceType
import dev.jsjh.timebox.domain.model.TaskEditInput
import dev.jsjh.timebox.domain.repository.TaskRepository
import dev.jsjh.timebox.feature.todo.TodoTaskOrderStore
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TimeBoxingTodoOrderTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val today = LocalDate.of(2026, 10, 7)
    private val dao = FakeDailyTaskDao()
    private val local = RoomTaskRepository(FakeTaskTemplateDao(), dao)
    private val store get() = TodoTaskOrderStore(context, "guest")

    @Before fun reset() {
        context.getSharedPreferences("todo_task_order_v1", Context.MODE_PRIVATE).edit().clear().commit()
        dao.upsertAll(listOf(task("A"), task("B"), task("C")))
    }

    @Test fun quickAddInsertsAtTopWithoutChangingManualOrderOrOtherViews() = runBlocking {
        val state = ready(CoroutineScope(coroutineContext))
        state.reorderTodayTodoTask("C", 0)
        state.quickAddTask("N")
        waitFor { state.todayTodoTasks.size == 4 }
        assertEquals(listOf("N", "C", "A", "B"), state.todayTodoTasks.map { it.title })
        assertEquals(listOf("A", "B", "C", "N"), state.todayTasks.map { it.title })
        assertEquals(4, dao.count())
    }

    @Test fun editorCreationGoesToTopButEditingDoesNot() = runBlocking {
        val state = ready(CoroutineScope(coroutineContext))
        state.reorderTodayTodoTask("C", 0)
        state.openNewTaskEditor()
        state.updateEditor { it.copy(title = "N", timeBlockEnabled = false) }
        state.saveEditor()
        waitFor { state.todayTodoTasks.size == 4 }
        val before = state.todayTodoTasks.map { it.id }
        state.openTaskEditor("A")
        waitFor { state.editorDraft?.taskId == "A" }
        state.updateEditor { it.copy(title = "Edited A") }
        state.saveEditor()
        waitFor { state.todayTodoTasks.any { it.title == "Edited A" } }
        assertEquals(before, state.todayTodoTasks.map { it.id })
        assertEquals("N", state.todayTodoTasks.first().title)
    }

    @Test fun completionAndVisibleDragPreserveCompletedSlotAndTaskData() = runBlocking {
        dao.upsert(task("B").copy(startMinute = 600, endMinute = 660, reminderEnabled = true, note = "keep"))
        val state = ready(CoroutineScope(coroutineContext))
        state.toggleCompleted("B")
        waitFor { state.todayTasks.first { it.id == "B" }.isCompleted }
        assertEquals(1 to 3, state.completionCounts(today))
        state.reorderTodayTodoTask("C", 0)
        assertEquals(listOf("C", "B", "A"), state.todayTodoTasks.map { it.id })
        state.toggleCompleted("B")
        waitFor { !state.todayTasks.first { it.id == "B" }.isCompleted }
        assertEquals(listOf("C", "B", "A"), state.todayTodoTasks.map { it.id })
        val preserved = dao.getById(today.toString(), "B")!!
        assertEquals(600, preserved.startMinute)
        assertEquals(660, preserved.endMinute)
        assertEquals("keep", preserved.note)
        assertTrue(preserved.reminderEnabled)
        assertEquals(0 to 3, state.completionCounts(today))
    }

    @Test fun recreationAndLanguageChangeRestoreUserOrder() = runBlocking {
        val scope = CoroutineScope(coroutineContext)
        val state = ready(scope)
        state.reorderTodayTodoTask("C", 0)
        state.toggleCompleted("B")
        waitFor { state.todayTasks.first { it.id == "B" }.isCompleted }
        val french = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.FRENCH)
        })
        val restored = TimeBoxingAppState(local, today, scope, TodoTaskOrderStore(french, "guest"))
        waitFor { restored.calendarStatsRevision > 0 }
        assertEquals(listOf("C", "A", "B"), restored.todayTodoTasks.map { it.id })
        assertTrue(restored.todayTodoTasks.last().isCompleted)
    }

    @Test fun accountsAndDatesKeepSeparateOrders() = runBlocking {
        val scope = CoroutineScope(coroutineContext)
        val guest = ready(scope)
        guest.reorderTodayTodoTask("C", 0)
        val other = TimeBoxingAppState(local, today, scope, TodoTaskOrderStore(context, "other"))
        waitFor { other.calendarStatsRevision > 0 }
        assertEquals(listOf("A", "B", "C"), other.todayTodoTasks.map { it.id })
        dao.upsertAll(listOf(task("F1", today.plusDays(1)), task("F2", today.plusDays(1))))
        store.write(today.plusDays(1), "brainDump", listOf("F2", "F1"))
        guest.updateTodayDate(today.plusDays(1))
        waitFor { guest.todayTodoTasks.size == 2 }
        assertEquals(listOf("F2", "F1"), guest.todayTodoTasks.map { it.id })
        guest.updateTodayDate(today)
        waitFor { guest.todayTodoTasks.size == 3 }
        assertEquals(listOf("C", "A", "B"), guest.todayTodoTasks.map { it.id })
    }

    @Test fun discoveredAndCarriedTasksAppendRatherThanResetManualOrder() = runBlocking {
        val state = ready(CoroutineScope(coroutineContext))
        state.reorderTodayTodoTask("C", 0)
        dao.upsert(task("remote"))
        val revision = state.calendarStatsRevision
        state.refreshAll()
        waitFor { state.calendarStatsRevision > revision }
        assertEquals(listOf("C", "A", "B", "remote"), state.todayTodoTasks.map { it.id })
        dao.upsert(task("old", today.minusDays(2)))
        state.carryOverPastIncompleteTasks(listOf("old"))
        waitFor { !state.carryOverInProgress }
        assertEquals(listOf("C", "A", "B", "remote", "old"), state.todayTodoTasks.map { it.id })
    }

    @Test fun big3ChangesDoNotCountAsNewCreation() = runBlocking {
        val state = ready(CoroutineScope(coroutineContext))
        state.reorderTodayTodoTask("C", 0)
        state.toggleBig3("A")
        waitFor { state.todayTasks.first { it.id == "A" }.isBig3 }
        state.toggleBig3("A")
        waitFor { !state.todayTasks.first { it.id == "A" }.isBig3 }
        assertEquals(listOf("C", "B", "A"), state.todayTodoTasks.map { it.id })
    }

    @Test fun futureEditorCreationStoresOrderForItsOwnDate() = runBlocking {
        val date = today.plusDays(3)
        dao.upsertAll(listOf(task("F1", date), task("F2", date)))
        store.write(date, "brainDump", listOf("F2", "F1"))
        val state = ready(CoroutineScope(coroutineContext))
        state.openNewTaskEditor(date)
        state.updateEditor { it.copy(title = "Future N", timeBlockEnabled = false) }
        state.saveEditor()
        waitFor { dao.getByDate(date.toString()).size == 3 }
        state.updateTodayDate(date)
        waitFor { state.todayTodoTasks.size == 3 && state.todayTodoTasks.all { it.date == date } }
        assertEquals(listOf("Future N", "F2", "F1"), state.todayTodoTasks.map { it.title })
    }

    @Test fun editorCreationsGoToTheirOwnSectionButAutoGeneratedHabitsAppend() = runBlocking {
        dao.upsert(task("B").copy(isBig3 = true))
        val rule = RecurrenceRule(RecurrenceType.DAILY)
        val h1 = local.upsertTask(TaskEditInput(date = today, title = "H1", recurrenceRule = rule))
        val h2 = local.upsertTask(TaskEditInput(date = today, title = "H2", recurrenceRule = rule))
        val state = ready(CoroutineScope(coroutineContext))
        state.reorderTodayTodoTask("C", 0)
        state.openNewTaskEditor()
        state.updateEditor { it.copy(title = "New priority", isBig3 = true, timeBlockEnabled = false) }
        val priorityRevision = state.calendarStatsRevision
        state.saveEditor()
        waitFor { state.calendarStatsRevision > priorityRevision && state.todayTodoTasks.any { it.title == "New priority" } }
        assertEquals(listOf("New priority", "B"), state.todayTodoTasks.filter { it.isBig3 }.map { it.title })
        state.openNewTaskEditor()
        state.updateEditor { it.copy(title = "New habit", recurringEnabled = true, timeBlockEnabled = false) }
        val habitRevision = state.calendarStatsRevision
        state.saveEditor()
        waitFor { state.calendarStatsRevision > habitRevision && state.todayTodoTasks.any { it.title == "New habit" } }
        assertEquals(listOf("New habit", "H1", "H2"), state.todayTodoTasks.filter { it.source == DailyTaskSource.RECURRING }.map { it.title })
        assertEquals(listOf("C", "A"), state.todayTodoTasks.filter { !it.isBig3 && it.source != DailyTaskSource.RECURRING }.map { it.id })
        val tomorrow = today.plusDays(1)
        store.write(tomorrow, "recurring", listOf("${h2.templateId}-$tomorrow", "${h1.templateId}-$tomorrow"))
        state.updateTodayDate(tomorrow)
        waitFor { state.todayTodoTasks.size == 3 && state.todayTodoTasks.all { it.date == tomorrow } }
        assertEquals(listOf("H2", "H1", "New habit"), state.todayTodoTasks.map { it.title })
    }

    @Test fun editingACompletedTaskKeepsItsSlotAndCompletionState() = runBlocking {
        dao.upsert(task("B").copy(isCompleted = true, startMinute = 600, endMinute = 660, note = "keep"))
        val state = ready(CoroutineScope(coroutineContext))
        state.reorderTodayTodoTask("C", 0)
        state.openTaskEditor("B")
        waitFor { state.editorDraft?.taskId == "B" }
        state.updateEditor { it.copy(title = "Edited completed B") }
        state.saveEditor()
        waitFor { state.todayTodoTasks.any { it.title == "Edited completed B" } }
        assertEquals(listOf("C", "B", "A"), state.todayTodoTasks.map { it.id })
        assertTrue(state.todayTodoTasks[1].isCompleted)
        assertEquals("keep", state.todayTodoTasks[1].note)
        assertEquals(600, state.todayTodoTasks[1].schedule?.startMinute)
        assertEquals(1 to 3, state.completionCounts(today))
    }

    @Test fun latestSuccessfulSaveIsFirstEvenWhenEarlierRequestFinishesLater() = runBlocking {
        val firstGate = CompletableDeferred<Unit>()
        val delayed = object : TaskRepository by local {
            override suspend fun addTask(date: LocalDate, title: String): DailyTask {
                if (title == "First request") firstGate.await()
                return local.addTask(date, title)
            }
        }
        val state = ready(CoroutineScope(coroutineContext), delayed)
        state.quickAddTask("First request")
        state.quickAddTask("Second request")
        waitFor { state.todayTodoTasks.size == 4 }
        firstGate.complete(Unit)
        waitFor { state.todayTodoTasks.size == 5 }
        assertEquals(listOf("First request", "Second request", "A", "B", "C"), state.todayTodoTasks.map { it.title })
        assertEquals(5, store.read(today, "brainDump")!!.distinct().size)
    }

    @Test fun oldRefreshCannotEraseTheOrderOfANewlySavedTask() = runBlocking {
        val captured = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        var first = true
        val delayed = object : TaskRepository by local {
            override suspend fun getTasks(date: LocalDate): List<DailyTask> {
                val snapshot = local.getTasks(date)
                if (first) {
                    first = false
                    captured.complete(Unit)
                    gate.await()
                }
                return snapshot
            }
        }
        val state = TimeBoxingAppState(delayed, today, CoroutineScope(coroutineContext), store)
        captured.await()
        state.quickAddTask("N")
        waitFor { state.todayTodoTasks.size == 4 }
        gate.complete(Unit)
        waitFor { state.calendarStatsRevision >= 2 }
        assertEquals(listOf("N", "A", "B", "C"), state.todayTodoTasks.map { it.title })
        assertEquals(state.todayTodoTasks.map { it.id }, store.read(today, "brainDump"))
    }

    @Test fun failedQuickAddAndEditorSaveDoNotChangeOrderMetadata() = runBlocking {
        val failures = mutableListOf<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, error -> failures += error })
        try {
            val failing = object : TaskRepository by local {
                override suspend fun addTask(date: LocalDate, title: String): DailyTask = error("Failed add")
                override suspend fun upsertTask(input: TaskEditInput): DailyTask = error("Failed edit")
            }
            val state = ready(scope, failing)
            state.reorderTodayTodoTask("C", 0)
            state.quickAddTask("N")
            waitFor { failures.size == 1 }
            state.openNewTaskEditor()
            state.updateEditor { it.copy(title = "N", timeBlockEnabled = false) }
            state.saveEditor()
            waitFor { failures.size == 2 }
            assertEquals(listOf("C", "A", "B"), store.read(today, "brainDump"))
            assertEquals(3, dao.count())
            assertFalse(state.todayTodoTasks.any { it.title == "N" })
        } finally {
            scope.cancel()
        }
    }

    private suspend fun ready(scope: CoroutineScope, repository: TaskRepository = local): TimeBoxingAppState {
        val state = TimeBoxingAppState(repository, today, scope, store)
        waitFor { state.calendarStatsRevision > 0 }
        return state
    }

    private suspend fun waitFor(condition: () -> Boolean) = withTimeout(5_000) {
        while (!condition()) delay(1)
    }

    private fun task(id: String, date: LocalDate = today) = DailyTaskEntity(
        id, null, date.toString(), id, null, "[]", false, false, null, null, false, "ONE_OFF"
    )
}
