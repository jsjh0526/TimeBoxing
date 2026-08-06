package dev.jsjh.timebox.feature.tutorial

import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.model.ScheduleBlock
import dev.jsjh.timebox.domain.model.TaskEditInput
import dev.jsjh.timebox.domain.model.TaskTemplate
import dev.jsjh.timebox.domain.repository.TaskRepository
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialOnboardingTest {
    private val date = LocalDate.of(2026, 8, 6)
    private val result = TutorialCompletionResult(
        schedule = ScheduleBlock(startMinute = 600, endMinute = 660, reminderEnabled = true)
    )

    @Test
    fun autoDecisionStartsOnlyNewlySeededUsersAndRestartsInterruptedUsers() {
        assertEquals(
            TutorialAutoDecision.START_FRESH,
            decideAutoTutorial(TutorialAutoStatus.NEW, seededThisLaunch = true)
        )
        assertEquals(
            TutorialAutoDecision.DO_NOT_START,
            decideAutoTutorial(TutorialAutoStatus.NEW, seededThisLaunch = false)
        )
        assertEquals(
            TutorialAutoDecision.RESTART,
            decideAutoTutorial(TutorialAutoStatus.IN_PROGRESS, seededThisLaunch = false)
        )
        listOf(
            TutorialAutoStatus.COMPLETED,
            TutorialAutoStatus.SKIPPED,
            TutorialAutoStatus.INELIGIBLE
        ).forEach { status ->
            assertEquals(
                TutorialAutoDecision.DO_NOT_START,
                decideAutoTutorial(status, seededThisLaunch = true)
            )
        }
    }

    @Test
    fun settingsReplayNeverChangesRepository() = runBlocking {
        val repository = FakeTaskRepository(date)

        assertEquals(
            TutorialMaterializationResult.NOT_REQUIRED,
            materializeTutorialResult(repository, date, TutorialLaunchSource.SETTINGS, result)
        )
        assertEquals(0, repository.big3Writes)
        assertEquals(0, repository.scheduleWrites)
        assertFalse(repository.task(TutorialTaskIds.MARK_BIG3).isBig3)
        assertEquals(null, repository.task(TutorialTaskIds.SCHEDULE).schedule)
    }

    @Test
    fun automaticCompletionMaterializesOnceAndRetryIsIdempotent() = runBlocking {
        val repository = FakeTaskRepository(date)

        repeat(2) {
            assertEquals(
                TutorialMaterializationResult.SUCCESS,
                materializeTutorialResult(repository, date, TutorialLaunchSource.AUTO_NEW, result)
            )
        }

        assertTrue(repository.task(TutorialTaskIds.MARK_BIG3).isBig3)
        assertEquals(result.schedule, repository.task(TutorialTaskIds.SCHEDULE).schedule)
        assertEquals(1, repository.big3Writes)
        assertEquals(1, repository.scheduleWrites)
    }

    @Test
    fun missingSeedTaskLeavesAutomaticTutorialIncomplete() = runBlocking {
        val repository = FakeTaskRepository(date).also {
            it.tasks.remove(TutorialTaskIds.SCHEDULE)
        }

        assertEquals(
            TutorialMaterializationResult.FAILED,
            materializeTutorialResult(repository, date, TutorialLaunchSource.AUTO_NEW, result)
        )
    }

    private class FakeTaskRepository(date: LocalDate) : TaskRepository {
        val tasks = mutableMapOf(
            TutorialTaskIds.MARK_BIG3 to DailyTask(
                id = TutorialTaskIds.MARK_BIG3,
                date = date,
                title = "Big 3"
            ),
            TutorialTaskIds.SCHEDULE to DailyTask(
                id = TutorialTaskIds.SCHEDULE,
                date = date,
                title = "Schedule"
            )
        )
        var big3Writes = 0
        var scheduleWrites = 0

        fun task(id: String): DailyTask = checkNotNull(tasks[id])

        override suspend fun getTasks(date: LocalDate): List<DailyTask> = tasks.values.toList()

        override suspend fun getTask(date: LocalDate, taskId: String): DailyTask? = tasks[taskId]

        override suspend fun getTemplate(templateId: String): TaskTemplate? = null

        override suspend fun toggleCompleted(date: LocalDate, taskId: String) = Unit

        override suspend fun markCompleted(date: LocalDate, taskId: String) = Unit

        override suspend fun toggleBig3(date: LocalDate, taskId: String) {
            big3Writes++
            val task = task(taskId)
            tasks[taskId] = task.copy(isBig3 = !task.isBig3)
        }

        override suspend fun setSchedule(date: LocalDate, taskId: String, schedule: ScheduleBlock?) {
            scheduleWrites++
            tasks[taskId] = task(taskId).copy(schedule = schedule)
        }

        override suspend fun addTask(date: LocalDate, title: String): DailyTask = error("unused")

        override suspend fun upsertTask(input: TaskEditInput): DailyTask = error("unused")

        override suspend fun deleteTask(date: LocalDate, taskId: String) = Unit

        override suspend fun carryOverIncompleteTasks(fromDate: LocalDate, toDate: LocalDate): Int = 0
    }
}
