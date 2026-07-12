package dev.jsjh.timebox.data.repository

import androidx.room.withTransaction
import dev.jsjh.timebox.data.local.dao.DailyTaskDao
import dev.jsjh.timebox.data.local.dao.TaskTemplateDao
import dev.jsjh.timebox.data.local.database.TaskDatabase
import dev.jsjh.timebox.data.local.entity.DailyTaskEntity
import dev.jsjh.timebox.data.local.entity.TaskTemplateEntity
import dev.jsjh.timebox.data.remote.SyncOutboxProcessor
import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.model.DailyTaskSource
import dev.jsjh.timebox.domain.model.ScheduleBlock
import dev.jsjh.timebox.domain.model.TaskEditInput
import dev.jsjh.timebox.domain.repository.TaskRepository
import java.time.LocalDate

class SyncedTaskRepository(
    private val database: TaskDatabase,
    private val local: RoomTaskRepository,
    private val templateDao: TaskTemplateDao,
    private val dailyTaskDao: DailyTaskDao,
    private val userId: String
) : TaskRepository by local, TemplateProvider by local {
    private val outbox = SyncOutboxProcessor(
        userId = userId,
        templateDao = templateDao,
        dailyTaskDao = dailyTaskDao,
        outboxDao = database.syncOutboxDao(),
        shadowDao = database.syncShadowDao()
    )

    override suspend fun toggleCompleted(date: LocalDate, taskId: String) {
        database.withTransaction {
            local.toggleCompletedBlocking(date, taskId)
            dailyTaskDao.getById(date.toString(), taskId)?.let(outbox::queueTaskUpsert)
        }
    }

    override suspend fun markCompleted(date: LocalDate, taskId: String) {
        database.withTransaction {
            local.markCompletedBlocking(date, taskId)
            dailyTaskDao.getById(date.toString(), taskId)?.let(outbox::queueTaskUpsert)
        }
    }

    override suspend fun toggleBig3(date: LocalDate, taskId: String) {
        database.withTransaction {
            local.toggleBig3Blocking(date, taskId)
            dailyTaskDao.getById(date.toString(), taskId)?.let(outbox::queueTaskUpsert)
        }
    }

    override suspend fun setSchedule(date: LocalDate, taskId: String, schedule: ScheduleBlock?) {
        database.withTransaction {
            val before = local.getTaskBlocking(date, taskId)
            val templateId = before?.templateId
            val beforeTemplate = templateId?.let(templateDao::getById)
            val beforeTasks = templateId?.let(dailyTaskDao::getByTemplateId).orEmpty()
            local.setScheduleBlocking(date, taskId, schedule)
            if (templateId != null) {
                queueTemplateChanges(templateId, beforeTemplate, beforeTasks)
            } else {
                dailyTaskDao.getById(date.toString(), taskId)?.let(outbox::queueTaskUpsert)
            }
        }
    }

    override suspend fun addTask(date: LocalDate, title: String): DailyTask {
        var result: DailyTask? = null
        database.withTransaction {
            val task = local.addTaskBlocking(date, title)
            result = task
            dailyTaskDao.getById(date.toString(), task.id)?.let(outbox::queueTaskUpsert)
        }
        return checkNotNull(result)
    }

    override suspend fun upsertTask(input: TaskEditInput): DailyTask {
        var result: DailyTask? = null
        database.withTransaction {
            val previous = input.taskId?.let { local.getTaskBlocking(input.date, it) }
            val previousTemplateId = previous?.templateId ?: input.templateId
            val beforeTemplate = previousTemplateId?.let(templateDao::getById)
            val beforeTasks = previousTemplateId?.let(dailyTaskDao::getByTemplateId).orEmpty()
            val task = local.upsertTaskBlocking(input)
            result = task

            if (previousTemplateId != null) {
                queueTemplateChanges(previousTemplateId, beforeTemplate, beforeTasks)
            }
            if (task.templateId != null && task.templateId != previousTemplateId) {
                queueTemplateChanges(task.templateId, null, emptyList())
            } else if (task.templateId == null) {
                dailyTaskDao.getById(task.date.toString(), task.id)?.let(outbox::queueTaskUpsert)
            }
        }
        return checkNotNull(result)
    }

    override suspend fun deleteTask(date: LocalDate, taskId: String) {
        database.withTransaction {
            val templateId = if (taskId.startsWith("template-")) {
                taskId.removePrefix("template-")
            } else {
                local.getTaskBlocking(date, taskId)?.templateId
            }

            if (templateId != null) {
                val template = templateDao.getById(templateId)
                val tasks = dailyTaskDao.getByTemplateId(templateId)
                local.deleteTaskBlocking(date, taskId)
                template?.let(outbox::queueTemplateDelete)
                tasks.forEach(outbox::queueTaskDelete)
            } else {
                val existing = local.getTaskBlocking(date, taskId)
                val existingEntity = dailyTaskDao.getById(date.toString(), taskId)
                val source = existing?.let { carryOverSourceEntity(it, date) }
                local.deleteTaskBlocking(date, taskId)
                existingEntity?.let(outbox::queueTaskDelete)
                source?.let(outbox::queueTaskDelete)
            }
        }
    }

    override suspend fun carryOverIncompleteTasks(fromDate: LocalDate, toDate: LocalDate): Int {
        var count = 0
        database.withTransaction {
            val beforeIds = dailyTaskDao.getByDate(toDate.toString()).mapTo(mutableSetOf()) { it.id }
            count = local.carryOverIncompleteTasksBlocking(fromDate, toDate)
            dailyTaskDao.getByDate(toDate.toString())
                .filter { it.id !in beforeIds && it.source == DailyTaskSource.CARRY_OVER.name }
                .forEach(outbox::queueTaskUpsert)
        }
        return count
    }

    private fun queueTemplateChanges(
        templateId: String,
        beforeTemplate: TaskTemplateEntity?,
        beforeTasks: List<DailyTaskEntity>
    ) {
        val afterTemplate = templateDao.getById(templateId)
        val afterTasks = dailyTaskDao.getByTemplateId(templateId)
        if (afterTemplate == null) {
            beforeTemplate?.let(outbox::queueTemplateDelete)
        } else {
            outbox.queueTemplateUpsert(afterTemplate)
        }
        val afterIds = afterTasks.mapTo(hashSetOf()) { it.id }
        beforeTasks.filter { it.id !in afterIds }.forEach(outbox::queueTaskDelete)
        afterTasks.forEach(outbox::queueTaskUpsert)
    }

    private fun carryOverSourceEntity(task: DailyTask, toDate: LocalDate): DailyTaskEntity? {
        if (task.source != DailyTaskSource.CARRY_OVER && !task.id.startsWith("carry-")) return null
        val suffix = "-$toDate"
        val sourceId = task.id
            .takeIf { it.startsWith("carry-") && it.endsWith(suffix) }
            ?.removePrefix("carry-")
            ?.removeSuffix(suffix)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return dailyTaskDao.getById(toDate.minusDays(1).toString(), sourceId)
    }
}
