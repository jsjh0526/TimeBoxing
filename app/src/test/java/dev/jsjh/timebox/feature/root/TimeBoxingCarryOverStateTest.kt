package dev.jsjh.timebox.feature.root

import dev.jsjh.timebox.data.local.entity.DailyTaskEntity
import dev.jsjh.timebox.data.repository.FakeDailyTaskDao
import dev.jsjh.timebox.data.repository.FakeTaskTemplateDao
import dev.jsjh.timebox.data.repository.RoomTaskRepository
import dev.jsjh.timebox.domain.repository.TaskRepository
import dev.jsjh.timebox.feature.settings.effectiveToday
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TimeBoxingCarryOverStateTest {
    private val today = LocalDate.of(2026, 10, 3)
    private val dao = FakeDailyTaskDao()
    private val local = RoomTaskRepository(FakeTaskTemplateDao(), dao, anchorDate = today)

    @Test
    fun usesAppLogicalTodayBeforeDayStartWithoutMovingCurrentOrFutureTasks() = runBlocking {
        val appToday = effectiveToday(3, LocalDateTime.of(2026, 10, 3, 1, 30))
        dao.upsertAll(listOf(task("older", appToday.minusDays(2)), task("current", appToday), task("future", today)))
        val state = TimeBoxingAppState(local, appToday, CoroutineScope(coroutineContext))
        waitFor { state.calendarStatsRevision > 0 }
        assertEquals(listOf("older"), state.pastIncompleteTasks.map { it.id })
        assertEquals(0, state.reminderScheduleRevision)

        state.carryOverPastIncompleteTasks(listOf("older"))
        waitFor { !state.carryOverInProgress }

        assertEquals(setOf("older", "current"), state.todayTasks.map { it.id }.toSet())
        assertEquals(appToday.toString(), dao.getAll().first { it.id == "older" }.dateIso)
        assertEquals(today.toString(), dao.getAll().first { it.id == "future" }.dateIso)
        assertEquals(1, state.reminderScheduleRevision)
        assertTrue(state.pastIncompleteTasks.isEmpty())
    }

    @Test
    fun doubleTapOnlyStartsOneOperationAndRefreshesTheBacklogAfterwards() = runBlocking {
        dao.upsert(task("old", today.minusDays(2)))
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val delayed = object : TaskRepository by local {
            override suspend fun carryOverPastIncompleteTasks(toDate: LocalDate, confirmedTaskIds: List<String>): Int {
                calls++
                gate.await()
                return local.carryOverPastIncompleteTasks(toDate, confirmedTaskIds)
            }
        }
        val state = TimeBoxingAppState(delayed, today, CoroutineScope(coroutineContext))
        waitFor { state.calendarStatsRevision > 0 }
        state.carryOverPastIncompleteTasks(listOf("old"))
        state.carryOverPastIncompleteTasks(listOf("old"))
        waitFor { calls == 1 }
        assertTrue(state.carryOverInProgress)
        gate.complete(Unit)
        waitFor { !state.carryOverInProgress }

        assertEquals(1, calls)
        assertFalse(state.carryOverFailed)
        assertTrue(state.pastIncompleteTasks.isEmpty())
        assertEquals(1, dao.count())
    }

    @Test
    fun failedOperationResetsTheGuardAndAllowsRetry() = runBlocking {
        dao.upsert(task("old", today.minusDays(2)))
        var calls = 0
        val flaky = object : TaskRepository by local {
            override suspend fun carryOverPastIncompleteTasks(toDate: LocalDate, confirmedTaskIds: List<String>): Int {
                calls++
                if (calls == 1) error("Simulated storage failure")
                return local.carryOverPastIncompleteTasks(toDate, confirmedTaskIds)
            }
        }
        val state = TimeBoxingAppState(flaky, today, CoroutineScope(coroutineContext))
        waitFor { state.calendarStatsRevision > 0 }
        state.carryOverPastIncompleteTasks(listOf("old"))
        waitFor { !state.carryOverInProgress }
        assertTrue(state.carryOverFailed)
        assertEquals(0, state.reminderScheduleRevision)

        state.carryOverPastIncompleteTasks(listOf("old"))
        waitFor { !state.carryOverInProgress }

        assertEquals(2, calls)
        assertFalse(state.carryOverFailed)
        assertTrue(state.pastIncompleteTasks.isEmpty())
    }

    @Test
    fun dismissUsesTheActualPastDateRatherThanAlwaysYesterday() = runBlocking {
        dao.upsert(task("older", today.minusDays(2)))
        val state = TimeBoxingAppState(local, today, CoroutineScope(coroutineContext))
        waitFor { state.calendarStatsRevision > 0 }
        state.dismissPastTask("older")
        waitFor { state.pastIncompleteTasks.isEmpty() }

        assertEquals(0, dao.count())
        assertEquals(1, state.reminderScheduleRevision)
    }

    private suspend fun waitFor(condition: () -> Boolean) = withTimeout(5_000) {
        while (!condition()) delay(1)
    }

    private fun task(id: String, date: LocalDate) = DailyTaskEntity(
        id = id, templateId = null, dateIso = date.toString(), title = id, note = null,
        tagsSerialized = "[]", isBig3 = false, isCompleted = false, startMinute = null,
        endMinute = null, reminderEnabled = false, source = "ONE_OFF"
    )
}
