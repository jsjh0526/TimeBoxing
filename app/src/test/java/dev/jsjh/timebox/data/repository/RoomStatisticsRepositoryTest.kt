package dev.jsjh.timebox.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jsjh.timebox.data.local.database.TaskDatabase
import dev.jsjh.timebox.data.local.entity.DailyTaskEntity
import dev.jsjh.timebox.data.local.entity.TaskTemplateEntity
import dev.jsjh.timebox.domain.model.StatisticsWeekStart
import dev.jsjh.timebox.domain.model.calculateWeeklyStatistics
import dev.jsjh.timebox.domain.model.statisticsWeek
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomStatisticsRepositoryTest {
    private lateinit var database: TaskDatabase
    private lateinit var repository: RoomStatisticsRepository
    private val today = LocalDate.of(2026, 10, 7)

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TaskDatabase::class.java).build()
        repository = RoomStatisticsRepository(database)
    }

    @After fun tearDown() { database.close() }

    @Test fun snapshotMatchesCalendarCountsWithoutChangingRowsTemplatesOrOutbox() = runBlocking {
        withContext(Dispatchers.IO) {
            database.taskTemplateDao().upsert(habit())
            database.dailyTaskDao().upsertAll(listOf(
                task("one-off", today.minusDays(2)).copy(isCompleted = true),
                task("done-habit", today).copy(templateId = "habit", source = "RECURRING", isCompleted = true, isBig3 = true, startMinute = 600, endMinute = 630),
                task("carried", today).copy(source = "CARRY_OVER"),
                task("blank", today).copy(title = "  "),
                task("stale", today).copy(templateId = "removed", source = "RECURRING"),
                task("future", today.plusDays(2)).copy(isCompleted = true)
            ))
        }
        val before = rows()
        val week = statisticsWeek(today, StatisticsWeekStart.SUNDAY)
        val snapshot = repository.getTasksForDates(week.includedDates)
        val calendar = RoomTaskRepository(database.taskTemplateDao(), database.dailyTaskDao()).getTaskCompletionCounts(week.includedDates)
        assertEquals(calendar, snapshot.mapValues { (_, tasks) -> tasks.count { it.isCompleted } to tasks.size })
        assertEquals(before, rows())
        val result = calculateWeeklyStatistics(week, snapshot)
        assertEquals(2, result.overall.completed)
        assertEquals(1, result.carriedOverCount)
        assertEquals(1, result.big3.completed)
        assertFalse(snapshot.values.flatten().any { it.id == "future" || it.id == "stale" || it.id == "blank" })
    }

    @Test fun unmaterializedHabitsUseTheirStartDateRuleAndDefaultSchedule() = runBlocking {
        withContext(Dispatchers.IO) {
            database.taskTemplateDao().upsert(habit().copy(recurrenceType = "CUSTOM", repeatDaysSerialized = "WEDNESDAY", defaultStartMinute = 60, defaultEndMinute = 90))
        }
        val dates = listOf(today.minusWeeks(1), today.minusDays(1), today, today.plusDays(1), today)
        val snapshot = repository.getTasksForDates(dates)
        assertEquals(4, snapshot.size)
        assertTrue(snapshot.getValue(today.minusWeeks(1)).isEmpty())
        assertTrue(snapshot.getValue(today.minusDays(1)).isEmpty())
        assertEquals(30, snapshot.getValue(today).single().schedule?.durationMinutes)
        assertFalse(snapshot.getValue(today).single().isCompleted)
        assertEquals(0, withContext(Dispatchers.IO) { database.dailyTaskDao().count() })
    }

    @Test fun invalidBlocksAreExcludedFromTimeButNotOverallCompletion() = runBlocking {
        withContext(Dispatchers.IO) {
            database.dailyTaskDao().upsert(task("invalid", today).copy(isCompleted = true, startMinute = 100, endMinute = 10))
        }
        val week = statisticsWeek(today, StatisticsWeekStart.SUNDAY)
        val result = calculateWeeklyStatistics(week, repository.getTasksForDates(week.includedDates))
        assertEquals(1, result.overall.completed)
        assertEquals(0, result.scheduled.total)
        assertEquals(0L, result.plannedMinutes)
    }

    @Test fun emptyRequestDoesNotGenerateDataAndSeparateAccountDatabasesStaySeparate() = runBlocking {
        assertTrue(repository.getTasksForDates(emptyList()).isEmpty())
        val second = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TaskDatabase::class.java).build()
        try {
            withContext(Dispatchers.IO) { database.dailyTaskDao().upsert(task("first-account", today)) }
            assertEquals(1, repository.getTasksForDates(listOf(today)).getValue(today).size)
            assertTrue(RoomStatisticsRepository(second).getTasksForDates(listOf(today)).getValue(today).isEmpty())
        } finally { second.close() }
    }

    @Test fun currentStateDeletionCarryOverAndHabitChangesRecalculatePastNumbers() = runBlocking {
        withContext(Dispatchers.IO) { database.dailyTaskDao().upsert(task("past", today.minusDays(1))) }
        assertEquals(1, repository.getTasksForDates(listOf(today.minusDays(1))).values.flatten().size)
        withContext(Dispatchers.IO) { database.dailyTaskDao().carryOverPastIncompleteTasks(today.toString(), listOf("past")) }
        assertTrue(repository.getTasksForDates(listOf(today.minusDays(1))).values.flatten().isEmpty())
        assertEquals("past", repository.getTasksForDates(listOf(today)).values.flatten().single().id)
        withContext(Dispatchers.IO) { database.dailyTaskDao().deleteById(today.toString(), "past") }
        assertTrue(repository.getTasksForDates(listOf(today)).values.flatten().isEmpty())
        withContext(Dispatchers.IO) { database.taskTemplateDao().upsert(habit()) }
        assertEquals(1, repository.getTasksForDates(listOf(today)).values.flatten().size)
        withContext(Dispatchers.IO) { database.taskTemplateDao().upsert(habit().copy(recurrenceType = "CUSTOM", repeatDaysSerialized = "MONDAY")) }
        assertTrue(repository.getTasksForDates(listOf(today)).values.flatten().isEmpty())
    }

    private suspend fun rows() = withContext(Dispatchers.IO) {
        Triple(database.dailyTaskDao().getAll(), database.taskTemplateDao().getAll(), database.syncOutboxDao().getAll())
    }

    private fun habit() = TaskTemplateEntity("habit", "Daily habit", null, "", "DAILY", "", today.minusDays(1).toString(), null, null, false)
    private fun task(id: String, date: LocalDate) = DailyTaskEntity(id, null, date.toString(), id, null, "", false, false, null, null, false, "ONE_OFF")
}
