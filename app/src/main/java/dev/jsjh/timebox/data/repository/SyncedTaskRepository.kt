package dev.jsjh.timebox.data.repository

import android.util.Log
import dev.jsjh.timebox.data.local.dao.DailyTaskDao
import dev.jsjh.timebox.data.local.dao.TaskTemplateDao
import dev.jsjh.timebox.data.remote.SupabaseSync
import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.model.DailyTaskSource
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
        val before = local.getTask(date, taskId)
        local.setSchedule(date, taskId, schedule)
        val taskEntity = dailyTaskDao.getById(date.toString(), taskId)
        val templateId = before?.templateId ?: taskEntity?.templateId
        if (templateId != null) {
            syncScope.launch {
                pushTemplateSnapshot(templateId)
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
        val previous = input.taskId?.let { local.getTask(input.date, it) }
        val previousTemplateId = previous?.templateId ?: input.templateId
        val task = local.upsertTask(input)
        syncScope.launch {
            if (previousTemplateId != null && previousTemplateId != task.templateId) {
                SupabaseSync.deleteTemplate(userId, previousTemplateId)
                SupabaseSync.deleteTasksByTemplateId(userId, previousTemplateId)
            }
            dailyTaskDao.getById(task.date.toString(), task.id)?.let {
                SupabaseSync.pushTask(userId, it)
            }
            task.templateId?.let { tplId ->
                pushTemplateSnapshot(tplId)
            }
        }
        return task
    }

    override fun deleteTask(date: LocalDate, taskId: String) {
        if (taskId.startsWith("template-")) {
            val templateId = taskId.removePrefix("template-")
            local.deleteTask(date, taskId)
            syncScope.launch {
                SupabaseSync.deleteTemplate(userId, templateId)
                SupabaseSync.deleteTasksByTemplateId(userId, templateId)
            }
            return
        }

        val existing = local.getTask(date, taskId)
        val carryOverSourceId = existing?.let { carryOverSourceId(it, date) }
        local.deleteTask(date, taskId)
        syncScope.launch {
            val templateId = existing?.templateId
            if (templateId != null) {
                SupabaseSync.deleteTemplate(userId, templateId)
                SupabaseSync.deleteTasksByTemplateId(userId, templateId)
            } else {
                SupabaseSync.deleteTask(userId, taskId)
                carryOverSourceId?.let { SupabaseSync.deleteTask(userId, it) }
            }
        }
    }

    override fun carryOverIncompleteTasks(fromDate: LocalDate, toDate: LocalDate): Int {
        val count = local.carryOverIncompleteTasks(fromDate, toDate)
        syncScope.launch {
            val carried = dailyTaskDao.getByDate(toDate.toString())
                .filter { it.source == DailyTaskSource.CARRY_OVER.name }
            SupabaseSync.pushTasks(userId, carried)
        }
        return count
    }

    private suspend fun pushTemplateSnapshot(templateId: String) {
        templateDao.getById(templateId)?.let { SupabaseSync.pushTemplate(userId, it) }
        SupabaseSync.replaceTasksForTemplate(userId, templateId, dailyTaskDao.getByTemplateId(templateId))
    }

    private fun carryOverSourceId(task: DailyTask, toDate: LocalDate): String? {
        if (task.source != DailyTaskSource.CARRY_OVER && !task.id.startsWith("carry-")) return null
        val suffix = "-$toDate"
        return task.id
            .takeIf { it.startsWith("carry-") && it.endsWith(suffix) }
            ?.removePrefix("carry-")
            ?.removeSuffix(suffix)
            ?.takeIf { it.isNotBlank() }
    }
}
