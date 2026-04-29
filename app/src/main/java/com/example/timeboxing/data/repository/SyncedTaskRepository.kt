package com.example.timeboxing.data.repository

import android.util.Log
import com.example.timeboxing.data.local.dao.DailyTaskDao
import com.example.timeboxing.data.local.dao.TaskTemplateDao
import com.example.timeboxing.data.remote.SupabaseSync
import com.example.timeboxing.domain.model.DailyTask
import com.example.timeboxing.domain.model.ScheduleBlock
import com.example.timeboxing.domain.model.TaskEditInput
import com.example.timeboxing.domain.model.TaskTemplate
import com.example.timeboxing.domain.repository.TaskRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * RoomTaskRepository를 감싸서 모든 쓰기 작업 후
 * 백그라운드로 Supabase에 동기화하는 Repository.
 *
 * 읽기: Room(로컬) 우선 — 빠르고 오프라인 지원
 * 쓰기: Room 저장 → Supabase 업로드 (실패해도 로컬엔 저장됨)
 */
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
            // Fix 1: user_id 필터 추가 → 본인 데이터만 삭제
            SupabaseSync.deleteTask(userId, taskId)
            existing?.templateId?.let { tplId ->
                SupabaseSync.deleteTemplate(userId, tplId)
                SupabaseSync.deleteTasksByTemplateId(userId, tplId)
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
