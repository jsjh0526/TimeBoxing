package dev.jsjh.timebox.data.repository

import android.util.Log
import dev.jsjh.timebox.data.local.dao.DailyTaskDao
import dev.jsjh.timebox.data.local.dao.TaskTemplateDao
import dev.jsjh.timebox.data.remote.SupabaseSync
import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.model.ScheduleBlock
import dev.jsjh.timebox.domain.model.TaskEditInput
import dev.jsjh.timebox.domain.repository.TaskRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SyncedTaskRepository(
    private val local: RoomTaskRepository,
    private val templateDao: TaskTemplateDao,
    private val dailyTaskDao: DailyTaskDao,
    private val userId: String
) : TaskRepository by local, TemplateProvider by local {

    private val syncExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.w("TaskSync", "Background sync failed", throwable)
    }
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + syncExceptionHandler)

    override fun toggleCompleted(date: LocalDate, taskId: String) {
        local.toggleCompleted(date, taskId)
        val entity = dailyTaskDao.getById(date.toString(), taskId) ?: return
        syncScope.launch { SupabaseSync.pushTask(userId, entity) }
    }

    override fun toggleBig3(date: LocalDate, taskId: String) {
        local.toggleBig3(date, taskId)
        val entity = dailyTaskDao.getById(date.toString(), taskId) ?: return
        syncScope.launch { SupabaseSync.pushTask(userId, entity) }
    }

    override fun setSchedule(date: LocalDate, taskId: String, schedule: ScheduleBlock?) {
        local.setSchedule(date, taskId, schedule)
        val taskEntity = dailyTaskDao.getById(date.toString(), taskId)
        val templateId = taskEntity?.templateId
        if (templateId != null) {
            val tplEntity = templateDao.getById(templateId)
            syncScope.launch {
                if (tplEntity != null) SupabaseSync.pushTemplate(userId, tplEntity)
                SupabaseSync.pushTask(userId, taskEntity)
            }
        } else if (taskEntity != null) {
            syncScope.launch { SupabaseSync.pushTask(userId, taskEntity) }
        }
    }

    override fun addTask(date: LocalDate, title: String): DailyTask {
        val task = local.addTask(date, title)
        val entity = dailyTaskDao.getById(date.toString(), task.id) ?: return task
        syncScope.launch { SupabaseSync.pushTask(userId, entity) }
        return task
    }

    override fun upsertTask(input: TaskEditInput): DailyTask {
        val task = local.upsertTask(input)
        syncScope.launch {
            dailyTaskDao.getById(task.date.toString(), task.id)?.let {
                SupabaseSync.pushTask(userId, it)
            }
            task.templateId?.let { tplId ->
                templateDao.getById(tplId)?.let { SupabaseSync.pushTemplate(userId, it) }
            }
        }
        return task
    }

    override fun deleteTask(date: LocalDate, taskId: String) {
        val existing = local.getTask(date, taskId)
        local.deleteTask(date, taskId)
        syncScope.launch {
            val templateId = existing?.templateId
                ?: taskId.takeIf { it.startsWith("template-") }?.removePrefix("template-")
            if (templateId != null) {
                SupabaseSync.deleteTemplate(userId, templateId)
                SupabaseSync.deleteTasksByTemplateId(userId, templateId)
            } else {
                SupabaseSync.deleteTask(userId, taskId)
            }
        }
    }

    override fun carryOverIncompleteTasks(fromDate: LocalDate, toDate: LocalDate): Int {
        val count = local.carryOverIncompleteTasks(fromDate, toDate)
        syncScope.launch {
            val carried = dailyTaskDao.getByDate(toDate.toString())
                .filter { it.source == "CARRY_OVER" }
            SupabaseSync.pushTasks(userId, carried)
        }
        return count
    }
}
