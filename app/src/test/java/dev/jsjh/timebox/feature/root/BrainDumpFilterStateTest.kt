package dev.jsjh.timebox.feature.root

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.jsjh.timebox.data.local.entity.DailyTaskEntity
import dev.jsjh.timebox.data.repository.FakeDailyTaskDao
import dev.jsjh.timebox.data.repository.FakeTaskTemplateDao
import dev.jsjh.timebox.data.repository.RoomTaskRepository
import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.model.TaskEditInput
import dev.jsjh.timebox.domain.repository.TaskRepository
import dev.jsjh.timebox.feature.todo.TodoTaskOrderStore
import java.time.LocalDate
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
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
class BrainDumpFilterStateTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val today = LocalDate.of(2026, 10, 9)
    private val dao = FakeDailyTaskDao()
    private val local = RoomTaskRepository(FakeTaskTemplateDao(), dao)
    private val store get() = TodoTaskOrderStore(context, "guest")

    @Before fun reset() {
        context.getSharedPreferences("todo_task_order_v1", Context.MODE_PRIVATE).edit().clear().commit()
        dao.upsertAll(listOf(
            task("A", "Work|Urgent"), task("B", "Home"),
            task("C", "Work|Urgent"), task("D", "Work|Urgent").copy(isCompleted = true),
            task("E", "Work")
        ))
    }

    @Test fun collapseClearTabSwitchAndRefreshHaveSeparateStateRules() = runBlocking {
        val state = ready(CoroutineScope(coroutineContext))
        select(state)
        state.updateBrainDumpTagsExpanded(false)
        assertEquals(setOf("Work", "Urgent"), state.brainDumpSelectedTags)
        state.updateBrainDumpTagsExpanded(true)
        state.clearBrainDumpTags()
        assertTrue(state.brainDumpTagsExpanded)
        assertTrue(state.brainDumpSelectedTags.isEmpty())
        select(state)
        state.selectTab(AppTab.SETTINGS)
        state.selectTab(AppTab.TODO)
        val revision = state.calendarStatsRevision
        state.refreshAll() // The same refresh used on foreground return must not reset filters.
        waitFor { state.calendarStatsRevision > revision }
        assertSelected(state)
    }

    @Test fun newAppStateAccountAndTodayResetButUnchangedTodayDoesNot() = runBlocking {
        val scope = CoroutineScope(coroutineContext)
        val state = ready(scope)
        select(state)
        state.updateTodayDate(today)
        assertSelected(state)
        val recreated = ready(scope)
        assertReset(recreated)
        val otherAccount = TimeBoxingAppState(local, today, scope, TodoTaskOrderStore(context, "other"))
        waitFor { otherAccount.calendarStatsRevision > 0 }
        assertReset(otherAccount)
        state.updateTodayDate(today.plusDays(1))
        assertReset(state)
        waitFor { state.todayTasks.all { it.date == state.today } }
    }

    @Test fun filteredReorderPreservesHiddenAndCompletedSlotsAndStoredData() = runBlocking {
        val state = ready(CoroutineScope(coroutineContext))
        select(state)
        val before = dao.getByDate(today.toString()).associateBy { it.id }
        state.reorderTodayTodoTask("C", 0)
        assertEquals(listOf("C", "B", "A", "D", "E"), state.todayTodoTasks.map { it.id })
        state.reorderTodayTodoTask("B", 0) // A hidden card cannot be moved by a stale callback.
        assertEquals(listOf("C", "B", "A", "D", "E"), store.read(today, "brainDump"))
        state.clearBrainDumpTags()
        assertEquals(listOf("C", "B", "A", "D", "E"), state.todayTodoTasks.map { it.id })
        assertEquals(before, dao.getByDate(today.toString()).associateBy { it.id })
    }

    @Test fun successfulTodoQuickAddClearsFilterWithoutAddingTags() = runBlocking {
        val scope = CoroutineScope(coroutineContext)
        val state = ready(scope)
        state.openTodo()
        awaitStateJobs(scope)
        select(state)
        val revision = state.calendarStatsRevision
        state.quickAddTask("New task")
        waitFor { state.calendarStatsRevision > revision }
        assertReset(state)
        assertEquals("New task", state.todayTodoTasks.first().title)
        assertTrue(state.todayTodoTasks.first().tags.isEmpty())
    }

    @Test fun newEditorSaveClearsButCancelAndExistingEditKeepFilter() = runBlocking {
        val scope = CoroutineScope(coroutineContext)
        val state = ready(scope)
        state.openTodo()
        awaitStateJobs(scope)
        select(state)
        state.openNewTaskEditor()
        state.dismissEditor()
        assertSelected(state)
        state.openTaskEditor("A")
        waitFor { state.editorDraft?.taskId == "A" }
        state.updateEditor { it.copy(title = "Edited A") }
        var revision = state.calendarStatsRevision
        state.saveEditor()
        waitFor { state.calendarStatsRevision > revision }
        assertSelected(state)
        assertEquals(listOf("Work", "Urgent"), state.todayTasks.first { it.id == "A" }.tags)
        state.openNewTaskEditor()
        state.updateEditor { it.copy(title = "New editor task", tags = listOf("Own", "Own"), timeBlockEnabled = false) }
        revision = state.calendarStatsRevision
        state.saveEditor()
        waitFor { state.calendarStatsRevision > revision }
        assertReset(state)
        assertEquals("New editor task", state.todayTodoTasks.first().title)
        assertEquals(listOf("Own", "Own"), state.todayTodoTasks.first().tags)
    }

    @Test fun futureDateAndNonTodoCreationDoNotResetCurrentFilter() = runBlocking {
        val scope = CoroutineScope(coroutineContext)
        val state = ready(scope)
        state.openTodo()
        awaitStateJobs(scope)
        select(state)
        state.openNewTaskEditor(today.plusDays(1))
        state.updateEditor { it.copy(title = "Future", timeBlockEnabled = false) }
        var revision = state.calendarStatsRevision
        state.saveEditor()
        waitFor { state.calendarStatsRevision > revision }
        assertSelected(state)
        revision = state.calendarStatsRevision
        state.quickAddTask("Future quick", today.plusDays(2))
        waitFor { state.calendarStatsRevision > revision }
        assertSelected(state)
        state.selectTab(AppTab.HOME)
        awaitStateJobs(scope)
        state.openNewTaskEditor()
        state.updateEditor { it.copy(title = "Home task", timeBlockEnabled = false) }
        revision = state.calendarStatsRevision
        state.saveEditor()
        waitFor { state.calendarStatsRevision > revision }
        assertSelected(state)
    }

    @Test fun failureDoesNotClearSelectionOrExpansion() = runBlocking {
        val failures = mutableListOf<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, e -> failures += e })
        try {
            val failing = object : TaskRepository by local {
                override suspend fun addTask(date: LocalDate, title: String): DailyTask = error("Failed add")
                override suspend fun upsertTask(input: TaskEditInput): DailyTask = error("Failed save")
            }
            val state = ready(scope, failing)
            state.openTodo()
            awaitStateJobs(scope)
            select(state)
            state.quickAddTask("N")
            waitFor { failures.size == 1 }
            assertSelected(state)
            state.openNewTaskEditor()
            state.updateEditor { it.copy(title = "N", timeBlockEnabled = false) }
            state.saveEditor()
            waitFor { failures.size == 2 }
            assertSelected(state)
            assertEquals(5, dao.count())
        } finally {
            scope.cancel()
        }
    }

    @Test fun completionDeletionAndBig3MovementDoNotDiscardSelections() = runBlocking {
        val state = ready(CoroutineScope(coroutineContext))
        select(state)
        var revision = state.calendarStatsRevision
        state.toggleCompleted("A")
        waitFor { state.calendarStatsRevision > revision }
        assertSelected(state)
        state.toggleBig3("C")
        waitFor { state.todayTasks.first { it.id == "C" }.isBig3 }
        assertSelected(state)
        state.openTaskEditor("E")
        waitFor { state.editorDraft?.taskId == "E" }
        revision = state.calendarStatsRevision
        state.deleteEditingTask()
        waitFor { state.calendarStatsRevision > revision }
        assertSelected(state)
    }

    private fun select(state: TimeBoxingAppState) {
        state.updateBrainDumpTagsExpanded(true)
        state.toggleBrainDumpTag("Work")
        state.toggleBrainDumpTag("Urgent")
    }
    private fun assertSelected(state: TimeBoxingAppState) {
        assertEquals(setOf("Work", "Urgent"), state.brainDumpSelectedTags)
        assertTrue(state.brainDumpTagsExpanded)
    }
    private fun assertReset(state: TimeBoxingAppState) {
        assertTrue(state.brainDumpSelectedTags.isEmpty())
        assertFalse(state.brainDumpTagsExpanded)
    }
    private suspend fun ready(scope: CoroutineScope, repository: TaskRepository = local): TimeBoxingAppState {
        val state = TimeBoxingAppState(repository, today, scope, store)
        waitFor { state.calendarStatsRevision > 0 }
        return state
    }
    private suspend fun waitFor(condition: () -> Boolean) = withTimeout(5_000) {
        while (!condition()) delay(1)
    }
    private suspend fun awaitStateJobs(scope: CoroutineScope) {
        // The fake DAO is not thread-safe; finish screen refreshes before starting a write.
        val jobs = scope.coroutineContext[Job]?.children?.toList().orEmpty()
        withTimeout(5_000) { jobs.joinAll() }
    }
    private fun task(id: String, tags: String) = DailyTaskEntity(
        id, null, today.toString(), id, null, tags, false, false, null, null, false, "ONE_OFF"
    )
}
