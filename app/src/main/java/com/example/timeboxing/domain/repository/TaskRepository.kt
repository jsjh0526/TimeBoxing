package com.example.timeboxing.domain.repository

import com.example.timeboxing.domain.model.DailyTask
import com.example.timeboxing.domain.model.ScheduleBlock
import com.example.timeboxing.domain.model.TaskEditInput
import com.example.timeboxing.domain.model.TaskTemplate
import java.time.LocalDate

interface TaskRepository {
    fun getTasks(date: LocalDate): List<DailyTask>
    fun getTask(date: LocalDate, taskId: String): DailyTask?
    fun getTemplate(templateId: String): TaskTemplate?
    fun toggleCompleted(date: LocalDate, taskId: String)
    fun toggleBig3(date: LocalDate, taskId: String)
    fun setSchedule(date: LocalDate, taskId: String, schedule: ScheduleBlock?)
    fun addTask(date: LocalDate, title: String): DailyTask
    fun upsertTask(input: TaskEditInput): DailyTask
    fun deleteTask(date: LocalDate, taskId: String)
}

