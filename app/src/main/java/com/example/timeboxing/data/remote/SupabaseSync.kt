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

data class RemoteSyncStatus(val templateCount: Int, val taskCount: Int)

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
    @SerialName("reminder_enabled")      val reminderEnabled: Boolean? = false,
    @SerialName("updated_at")            val updatedAt: String? = null,
    @SerialName("deleted_at")            val deletedAt: String? = null
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
    @SerialName("is_big3")          val isBig3: Boolean? = false,
    @SerialName("is_completed")     val isCompleted: Boolean? = false,
    @SerialName("start_minute")     val startMinute: Int?    = null,
    @SerialName("end_minute")       val endMinute: Int?      = null,
    @SerialName("reminder_enabled") val reminderEnabled: Boolean? = false,
    @SerialName("source")           val source: String,
    @SerialName("updated_at")       val updatedAt: String? = null,
    @SerialName("deleted_at")       val deletedAt: String? = null
)

@Serializable
private data class RemoteTemplateStatus(
    @SerialName("id")         val id: String,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
private data class RemoteTaskStatus(
    @SerialName("id")         val id: String,
    @SerialName("deleted_at") val deletedAt: String? = null
)

object SupabaseSync {

    // ── 다운로드: soft-deleted 항목 제외하고 복원 ─────────────────────────────
    suspend fun pull(
        userId: String,
        templateDao: TaskTemplateDao,
        dailyTaskDao: DailyTaskDao
    ): RemoteSyncStatus {
        val remoteTemplates = supabase.from("task_templates")
            .select(Columns.ALL) { filter { eq("user_id", userId) } }
            .decodeList<RemoteTemplate>()

        val activeTemplates = remoteTemplates.filter { it.deletedAt == null }
        if (activeTemplates.isNotEmpty()) templateDao.upsertAll(activeTemplates.map { it.toEntity() })

        val remoteTasks = supabase.from("daily_tasks")
            .select(Columns.ALL) { filter { eq("user_id", userId) } }
            .decodeList<RemoteTask>()

        val activeTasks = remoteTasks.filter { it.deletedAt == null }
        if (activeTasks.isNotEmpty()) dailyTaskDao.upsertAll(activeTasks.map { it.toEntity() })

        return RemoteSyncStatus(
            templateCount = activeTemplates.size,
            taskCount     = activeTasks.size
        )
    }

    // ── 전체 동기화: pull() 결과로 원격 카운트까지 반환 ─────────────────────
    suspend fun syncAll(
        userId: String,
        templateDao: TaskTemplateDao,
        dailyTaskDao: DailyTaskDao
    ): RemoteSyncStatus {
        val templates = templateDao.getAll()
        if (templates.isNotEmpty()) pushTemplates(userId, templates)

        val tasks = dailyTaskDao.getAll()
        if (tasks.isNotEmpty()) pushTasks(userId, tasks)

        return pull(userId, templateDao, dailyTaskDao)
    }

    // ── 원격 상태 조회: 로컬 DB는 건드리지 않음 ─────────────────────────────
    suspend fun status(userId: String): RemoteSyncStatus {
        val remoteTemplates = supabase.from("task_templates")
            .select(Columns.list("id", "deleted_at")) { filter { eq("user_id", userId) } }
            .decodeList<RemoteTemplateStatus>()

        val remoteTasks = supabase.from("daily_tasks")
            .select(Columns.list("id", "deleted_at")) { filter { eq("user_id", userId) } }
            .decodeList<RemoteTaskStatus>()

        return RemoteSyncStatus(
            templateCount = remoteTemplates.count { it.deletedAt == null },
            taskCount = remoteTasks.count { it.deletedAt == null }
        )
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

    // ── 삭제: user_id 필터로 본인 데이터만 삭제 ─────────────────────────────
    suspend fun deleteTask(userId: String, taskId: String) {
        supabase.from("daily_tasks").delete { filter { eq("id", taskId); eq("user_id", userId) } }
    }

    suspend fun deleteTemplate(userId: String, templateId: String) {
        supabase.from("task_templates").delete { filter { eq("id", templateId); eq("user_id", userId) } }
    }

    suspend fun deleteTasksByTemplateId(userId: String, templateId: String) {
        supabase.from("daily_tasks").delete { filter { eq("template_id", templateId); eq("user_id", userId) } }
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
        reminderEnabled      = reminderEnabled ?: false
    )

    private fun RemoteTask.toEntity() = DailyTaskEntity(
        id              = id,
        templateId      = templateId,
        dateIso         = dateIso,
        title           = title,
        note            = note,
        tagsSerialized  = tags.orEmpty(),
        isBig3          = isBig3 ?: false,
        isCompleted     = isCompleted ?: false,
        startMinute     = startMinute,
        endMinute       = endMinute,
        reminderEnabled = reminderEnabled ?: false,
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
