package dev.jsjh.timebox.domain.repository

import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.model.ScheduleBlock
import dev.jsjh.timebox.domain.model.TaskEditInput
import dev.jsjh.timebox.domain.model.TaskTemplate
import java.time.LocalDate

interface TaskRepository {
    fun getTasks(date: LocalDate): List<DailyTask>
    fun getTask(date: LocalDate, taskId: String): DailyTask?
    fun getTemplate(templateId: String): TaskTemplate?
    fun toggleCompleted(date: LocalDate, taskId: String)
    fun markCompleted(date: LocalDate, taskId: String)
    fun toggleBig3(date: LocalDate, taskId: String)
    fun setSchedule(date: LocalDate, taskId: String, schedule: ScheduleBlock?)
    fun addTask(date: LocalDate, title: String): DailyTask
    fun upsertTask(input: TaskEditInput): DailyTask
    fun deleteTask(date: LocalDate, taskId: String)
    fun carryOverIncompleteTasks(fromDate: LocalDate, toDate: LocalDate): Int
}

