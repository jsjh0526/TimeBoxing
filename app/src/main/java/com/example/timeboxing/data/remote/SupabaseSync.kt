package com.example.timeboxing.data.remote

import com.example.timeboxing.auth.supabase
import com.example.timeboxing.data.local.dao.DailyTaskDao
import com.example.timeboxing.data.local.dao.TaskTemplateDao
import com.example.timeboxing.data.local.entity.DailyTaskEntity
import com.example.timeboxing.data.local.entity.TaskTemplateEntity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

@Serializable
private data class SoftDeletePatch(
    @SerialName("deleted_at") val deletedAt: String
)

object SupabaseSync {
    private const val USER_SCOPED_CONFLICT = "user_id,id"

    suspend fun pull(
        userId: String,
        templateDao: TaskTemplateDao,
        dailyTaskDao: DailyTaskDao
    ): RemoteSyncStatus {
        val remoteTemplates = supabase.from("task_templates")
            .select(Columns.ALL) { filter { eq("user_id", userId) } }
            .decodeList<RemoteTemplate>()

        val activeTemplates = remoteTemplates.filter { it.deletedAt == null }
        val deletedTemplateIds = remoteTemplates.filter { it.deletedAt != null }.map { it.id }
        if (deletedTemplateIds.isNotEmpty()) {
            templateDao.deleteByIds(deletedTemplateIds)
            deletedTemplateIds.forEach { templateId ->
                dailyTaskDao.deleteByTemplateId(templateId)
            }
        }
        if (activeTemplates.isNotEmpty()) templateDao.upsertAll(activeTemplates.map { it.toEntity() })

        val remoteTasks = supabase.from("daily_tasks")
            .select(Columns.ALL) { filter { eq("user_id", userId) } }
            .decodeList<RemoteTask>()

        val activeTasks = remoteTasks.filter { it.deletedAt == null }
        val deletedTaskIds = remoteTasks.filter { it.deletedAt != null }.map { it.id }
        if (deletedTaskIds.isNotEmpty()) dailyTaskDao.deleteByIds(deletedTaskIds)
        if (activeTasks.isNotEmpty()) dailyTaskDao.upsertAll(activeTasks.map { it.toEntity() })

        return RemoteSyncStatus(
            templateCount = activeTemplates.size,
            taskCount     = activeTasks.size
        )
    }

    suspend fun syncAll(
        userId: String,
        templateDao: TaskTemplateDao,
        dailyTaskDao: DailyTaskDao
    ): RemoteSyncStatus {
        requireMatchingAuthUser(userId)

        val templates = templateDao.getAll()
        val tasks = dailyTaskDao.getAll()

        // Manual Sync Now is a backup action: the current device should win.
        // Otherwise a contaminated remote backup can overwrite local Big3/completed state.
        softDeleteRemoteItemsMissingLocally(userId, templates, tasks)

        if (templates.isNotEmpty()) pushTemplateRows(userId, templates)

        if (tasks.isNotEmpty()) pushTaskRows(userId, tasks)

        val remoteStatus = status(userId)
        check(remoteStatus.templateCount == templates.size && remoteStatus.taskCount == tasks.size) {
            "Sync verification failed: local=${templates.size} habits/${tasks.size} tasks, " +
                "remote=${remoteStatus.templateCount} habits/${remoteStatus.taskCount} tasks"
        }
        return remoteStatus
    }

    private suspend fun softDeleteRemoteItemsMissingLocally(
        userId: String,
        templates: List<TaskTemplateEntity>,
        tasks: List<DailyTaskEntity>
    ) {
        val localTemplateIds = templates.map { it.id }.toSet()
        val remoteTemplates = supabase.from("task_templates")
            .select(Columns.list("id", "deleted_at")) { filter { eq("user_id", userId) } }
            .decodeList<RemoteTemplateStatus>()

        val missingTemplateIds = remoteTemplates
            .filter { it.deletedAt == null && it.id !in localTemplateIds }
            .map { it.id }
        softDeleteTemplates(userId, missingTemplateIds)
        softDeleteTasksByTemplateIds(userId, missingTemplateIds)

        val localTaskIds = tasks.map { it.id }.toSet()
        val remoteTasks = supabase.from("daily_tasks")
            .select(Columns.list("id", "deleted_at")) { filter { eq("user_id", userId) } }
            .decodeList<RemoteTaskStatus>()
        val missingTaskIds = remoteTasks
            .filter { it.deletedAt == null && it.id !in localTaskIds }
            .map { it.id }
        softDeleteTasks(userId, missingTaskIds)
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
        requireMatchingAuthUser(userId)
        pushTaskRows(userId, listOf(entity))
    }

    suspend fun pushTasks(userId: String, entities: List<DailyTaskEntity>) {
        if (entities.isEmpty()) return
        requireMatchingAuthUser(userId)
        pushTaskRows(userId, entities)
    }

    suspend fun pushTemplate(userId: String, entity: TaskTemplateEntity) {
        requireMatchingAuthUser(userId)
        pushTemplateRows(userId, listOf(entity))
    }

    suspend fun pushTemplates(userId: String, entities: List<TaskTemplateEntity>) {
        if (entities.isEmpty()) return
        requireMatchingAuthUser(userId)
        pushTemplateRows(userId, entities)
    }

    private suspend fun pushTaskRows(userId: String, entities: List<DailyTaskEntity>) {
        if (entities.isEmpty()) return
        supabase.from("daily_tasks").upsert(entities.map { it.toRemoteJson(userId) }) {
            onConflict = USER_SCOPED_CONFLICT
        }
    }

    private suspend fun pushTemplateRows(userId: String, entities: List<TaskTemplateEntity>) {
        if (entities.isEmpty()) return
        supabase.from("task_templates").upsert(entities.map { it.toRemoteJson(userId) }) {
            onConflict = USER_SCOPED_CONFLICT
        }
    }

    private suspend fun requireMatchingAuthUser(userId: String) {
        val authUserId = supabase.auth.currentUserOrNull()?.id
            ?: runCatching { supabase.auth.retrieveUserForCurrentSession(updateSession = true).id }.getOrNull()

        check(authUserId == userId) {
            "Sync account mismatch: app user=$userId, auth user=${authUserId.orEmpty()}"
        }
    }

    suspend fun deleteTask(userId: String, taskId: String) {
        softDeleteTasks(userId, listOf(taskId))
    }

    suspend fun deleteTemplate(userId: String, templateId: String) {
        softDeleteTemplates(userId, listOf(templateId))
    }

    suspend fun deleteTasksByTemplateId(userId: String, templateId: String) {
        softDeleteTasksByTemplateIds(userId, listOf(templateId))
    }

    private suspend fun softDeleteTasks(userId: String, ids: List<String>) {
        ids.distinct().chunked(100).forEach { chunk ->
            supabase.from("daily_tasks").update(softDeletePatch()) {
                filter {
                    eq("user_id", userId)
                    isIn("id", chunk)
                }
            }
        }
    }

    private suspend fun softDeleteTemplates(userId: String, ids: List<String>) {
        ids.distinct().chunked(100).forEach { chunk ->
            supabase.from("task_templates").update(softDeletePatch()) {
                filter {
                    eq("user_id", userId)
                    isIn("id", chunk)
                }
            }
        }
    }

    private suspend fun softDeleteTasksByTemplateIds(userId: String, templateIds: List<String>) {
        templateIds.distinct().chunked(100).forEach { chunk ->
            supabase.from("daily_tasks").update(softDeletePatch()) {
                filter {
                    eq("user_id", userId)
                    isIn("template_id", chunk)
                }
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

    private fun DailyTaskEntity.toRemoteJson(userId: String): JsonObject = buildJsonObject {
        put("id", id)
        put("user_id", userId)
        putNullable("template_id", templateId)
        put("date_iso", dateIso)
        put("title", title)
        putNullable("note", note)
        put("tags", tagsSerialized)
        put("is_big3", isBig3)
        put("is_completed", isCompleted)
        putNullable("start_minute", startMinute)
        putNullable("end_minute", endMinute)
        put("reminder_enabled", reminderEnabled)
        put("source", source)
        put("deleted_at", JsonNull)
    }

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

    private fun TaskTemplateEntity.toRemoteJson(userId: String): JsonObject = buildJsonObject {
        put("id", id)
        put("user_id", userId)
        put("title", title)
        putNullable("note", note)
        put("tags", tagsSerialized)
        putNullable("recurrence_type", recurrenceType)
        put("repeat_days", repeatDaysSerialized)
        putNullable("default_start_minute", defaultStartMinute)
        putNullable("default_end_minute", defaultEndMinute)
        put("reminder_enabled", reminderEnabled)
        put("deleted_at", JsonNull)
    }

    private fun softDeletePatch(): SoftDeletePatch =
        SoftDeletePatch(OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: String?) {
        put(key, value?.let { JsonPrimitive(it) } ?: JsonNull)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: Int?) {
        put(key, value?.let { JsonPrimitive(it) } ?: JsonNull)
    }
}
