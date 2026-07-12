package dev.jsjh.timebox.domain.repository

import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.model.ScheduleBlock
import dev.jsjh.timebox.domain.model.TaskEditInput
import dev.jsjh.timebox.domain.model.TaskTemplate
import java.time.LocalDate

interface TaskRepository {
    suspend fun getTasks(date: LocalDate): List<DailyTask>
    suspend fun getTasks(dates: Collection<LocalDate>): Map<LocalDate, List<DailyTask>> {
        return dates.distinct().associateWith { getTasks(it) }
    }
    suspend fun getTaskCompletionCounts(dates: Collection<LocalDate>): Map<LocalDate, Pair<Int, Int>> {
        return getTasks(dates).mapValues { (_, tasks) ->
            val visibleTasks = tasks.filter { it.title.isNotBlank() }
            visibleTasks.count { it.isCompleted } to visibleTasks.size
        }
    }
    suspend fun getTask(date: LocalDate, taskId: String): DailyTask?
    suspend fun getTemplate(templateId: String): TaskTemplate?
    suspend fun toggleCompleted(date: LocalDate, taskId: String)
    suspend fun markCompleted(date: LocalDate, taskId: String)
    suspend fun toggleBig3(date: LocalDate, taskId: String)
    suspend fun setSchedule(date: LocalDate, taskId: String, schedule: ScheduleBlock?)
    suspend fun addTask(date: LocalDate, title: String): DailyTask
    suspend fun upsertTask(input: TaskEditInput): DailyTask
    suspend fun deleteTask(date: LocalDate, taskId: String)
    suspend fun carryOverIncompleteTasks(fromDate: LocalDate, toDate: LocalDate): Int
}

