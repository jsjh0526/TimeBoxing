package com.example.timeboxing.data.remote

import com.example.timeboxing.auth.supabase
import com.example.timeboxing.data.local.dao.DailyTaskDao
import com.example.timeboxing.data.local.dao.TaskTemplateDao
import com.example.timeboxing.data.local.entity.DailyTaskEntity
import com.example.timeboxing.data.local.entity.TaskTemplateEntity
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteTemplate(
    @SerialName("id")                    val id: String,
    @SerialName("user_id")               val userId: String,
    @SerialName("title")                 val title: String,
    @SerialName("note")                  val note: String?  = null,
    @SerialName("tags")                  val tags: String?  = null,
    @SerialName("recurrence_type")       val recurrenceType: String?  = null,
    @SerialName("repeat_days")           val repeatDays: String?  = null,
    @SerialName("default_start_minute")  val defaultStartMinute: Int? = null,
    @SerialName("default_end_minute")    val defaultEndMinute: Int?   = null,
    @SerialName("reminder_enabled")      val reminderEnabled: Boolean = false
)

@Serializable
data class RemoteTask(
    @SerialName("id")               val id: String,
    @SerialName("user_id")          val userId: String,
    @SerialName("template_id")      val templateId: String?  = null,
    @SerialName("date_iso")         val dateIso: String,
    @SerialName("title")            val title: String,
    @SerialName("note")             val note: String?  = null,
    @SerialName("tags")             val tags: String?  = null,
    @SerialName("is_big3")          val isBig3: Boolean = false,
    @SerialName("is_completed")     val isCompleted: Boolean = false,
    @SerialName("start_minute")     val startMinute: Int?    = null,
    @SerialName("end_minute")       val endMinute: Int?      = null,
    @SerialName("reminder_enabled") val reminderEnabled: Boolean = false,
    @SerialName("source")           val source: String
)

object SupabaseSync {

    // ── 다운로드 ──────────────────────────────────────────────────────────────
    suspend fun pull(
        userId: String,
        templateDao: TaskTemplateDao,
        dailyTaskDao: DailyTaskDao
    ) {
        val remoteTemplates = supabase.from("task_templates")
            .select(Columns.ALL) { filter { eq("user_id", userId) } }
            .decodeList<RemoteTemplate>()
        if (remoteTemplates.isNotEmpty()) {
            templateDao.upsertAll(remoteTemplates.map { it.toEntity() })
        }

        val remoteTasks = supabase.from("daily_tasks")
            .select(Columns.ALL) { filter { eq("user_id", userId) } }
            .decodeList<RemoteTask>()
        if (remoteTasks.isNotEmpty()) {
            dailyTaskDao.upsertAll(remoteTasks.map { it.toEntity() })
        }
    }

    // ── 전체 동기화 ───────────────────────────────────────────────────────────
    suspend fun syncAll(
        userId: String,
        templateDao: TaskTemplateDao,
        dailyTaskDao: DailyTaskDao
    ) {
        val templates = templateDao.getAll()
        if (templates.isNotEmpty()) pushTemplates(userId, templates)

        val tasks = dailyTaskDao.getAll()
        if (tasks.isNotEmpty()) pushTasks(userId, tasks)

        pull(userId, templateDao, dailyTaskDao)
    }

    // ── 업로드 ───────────────────────────────────────────────────────────────
    suspend fun pushTask(userId: String, entity: DailyTaskEntity) {
        supabase.from("daily_tasks").upsert(entity.toRemote(userId))
    }

    suspend fun pushTasks(userId: String, entities: List<DailyTaskEntity>) {
        if (entities.isEmpty()) return
        supabase.from("daily_tasks").upsert(entities.map { it.toRemote(userId) })
    }

    suspend fun pushTemplate(userId: String, entity: TaskTemplateEntity) {
        supabase.from("task_templates").upsert(entity.toRemote(userId))
    }

    suspend fun pushTemplates(userId: String, entities: List<TaskTemplateEntity>) {
        if (entities.isEmpty()) return
        supabase.from("task_templates").upsert(entities.map { it.toRemote(userId) })
    }

    // ── 삭제: user_id 필터 추가 → 본인 데이터만 삭제 (Fix 1) ─────────────────
    suspend fun deleteTask(userId: String, taskId: String) {
        supabase.from("daily_tasks").delete {
            filter {
                eq("id", taskId)
                eq("user_id", userId)
            }
        }
    }

    suspend fun deleteTemplate(userId: String, templateId: String) {
        supabase.from("task_templates").delete {
            filter {
                eq("id", templateId)
                eq("user_id", userId)
            }
        }
    }

    suspend fun deleteTasksByTemplateId(userId: String, templateId: String) {
        supabase.from("daily_tasks").delete {
            filter {
                eq("template_id", templateId)
                eq("user_id", userId)
            }
        }
    }

    // ── 변환 ─────────────────────────────────────────────────────────────────

    private fun RemoteTemplate.toEntity() = TaskTemplateEntity(
        id                   = id,
        title                = title,
        note                 = note,
        tagsSerialized       = tags.orEmpty(),
        recurrenceType       = recurrenceType,
        repeatDaysSerialized = repeatDays.orEmpty(),
        defaultStartMinute   = defaultStartMinute,
        defaultEndMinute     = defaultEndMinute,
        reminderEnabled      = reminderEnabled
    )

    private fun RemoteTask.toEntity() = DailyTaskEntity(
        id              = id,
        templateId      = templateId,
        dateIso         = dateIso,
        title           = title,
        note            = note,
        tagsSerialized  = tags.orEmpty(),
        isBig3          = isBig3,
        isCompleted     = isCompleted,
        startMinute     = startMinute,
        endMinute       = endMinute,
        reminderEnabled = reminderEnabled,
        source          = source
    )

    private fun DailyTaskEntity.toRemote(userId: String) = RemoteTask(
        id              = id,
        userId          = userId,
        templateId      = templateId,
        dateIso         = dateIso,
        title           = title,
        note            = note,
        tags            = tagsSerialized,
        isBig3          = isBig3,
        isCompleted     = isCompleted,
        startMinute     = startMinute,
        endMinute       = endMinute,
        reminderEnabled = reminderEnabled,
        source          = source
    )

    private fun TaskTemplateEntity.toRemote(userId: String) = RemoteTemplate(
        id                  = id,
        userId              = userId,
        title               = title,
        note                = note,
        tags                = tagsSerialized,
        recurrenceType      = recurrenceType,
        repeatDays          = repeatDaysSerialized,
        defaultStartMinute  = defaultStartMinute,
        defaultEndMinute    = defaultEndMinute,
        reminderEnabled     = reminderEnabled
    )
}
