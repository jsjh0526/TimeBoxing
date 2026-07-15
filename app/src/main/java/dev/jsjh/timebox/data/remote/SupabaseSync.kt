package dev.jsjh.timebox.data.remote

import dev.jsjh.timebox.auth.supabase
import dev.jsjh.timebox.data.local.dao.DailyTaskDao
import dev.jsjh.timebox.data.local.dao.SyncShadowDao
import dev.jsjh.timebox.data.local.dao.TaskTemplateDao
import dev.jsjh.timebox.data.local.entity.DailyTaskEntity
import dev.jsjh.timebox.data.local.entity.SyncShadowEntity
import dev.jsjh.timebox.data.local.entity.TaskTemplateEntity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class RemoteSyncStatus(val templateCount: Int, val taskCount: Int)

data class RemoteSyncSnapshot(
    val templatesById: Map<String, RemoteTemplate>,
    val tasksById: Map<String, RemoteTask>
)

private const val USER_SCOPED_CONFLICT = "user_id,id"

@Serializable
data class RemoteTemplate(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("title") val title: String,
    @SerialName("note") val note: String? = null,
    @SerialName("tags") val tags: String? = null,
    @SerialName("recurrence_type") val recurrenceType: String? = null,
    @SerialName("repeat_days") val repeatDays: String? = null,
    @SerialName("default_start_minute") val defaultStartMinute: Int? = null,
    @SerialName("default_end_minute") val defaultEndMinute: Int? = null,
    @SerialName("reminder_enabled") val reminderEnabled: Boolean? = false,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
data class RemoteTask(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("template_id") val templateId: String? = null,
    @SerialName("date_iso") val dateIso: String,
    @SerialName("title") val title: String,
    @SerialName("note") val note: String? = null,
    @SerialName("tags") val tags: String? = null,
    @SerialName("is_big3") val isBig3: Boolean? = false,
    @SerialName("is_completed") val isCompleted: Boolean? = false,
    @SerialName("start_minute") val startMinute: Int? = null,
    @SerialName("end_minute") val endMinute: Int? = null,
    @SerialName("reminder_enabled") val reminderEnabled: Boolean? = false,
    @SerialName("source") val source: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
private data class RemoteItemStatus(
    @SerialName("id") val id: String,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
private data class SoftDeletePatch(
    @SerialName("deleted_at") val deletedAt: String
)

object SupabaseSync {

    suspend fun pull(
        userId: String,
        templateDao: TaskTemplateDao,
        dailyTaskDao: DailyTaskDao,
        syncShadowDao: SyncShadowDao? = null,
        replaceLocal: Boolean = false
    ): RemoteSyncStatus {
        val remoteTemplates = supabase.from("task_templates")
            .select(Columns.ALL) { filter { eq("user_id", userId) } }
            .decodeList<RemoteTemplate>()

        val activeTemplates = remoteTemplates.filter { it.deletedAt == null }
        val deletedTemplateIds = remoteTemplates.filter { it.deletedAt != null }.map { it.id }

        val remoteTasks = supabase.from("daily_tasks")
            .select(Columns.ALL) { filter { eq("user_id", userId) } }
            .decodeList<RemoteTask>()

        val activeTasks = remoteTasks.filter { it.deletedAt == null }
        val deletedTaskIds = remoteTasks.filter { it.deletedAt != null }.map { it.id }
        val templateStartDates = activeTasks
            .mapNotNull { task -> task.templateId?.let { templateId -> templateId to task.dateIso } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, dates) -> dates.minOrNull() }

        if (replaceLocal) {
            dailyTaskDao.clearAll()
            templateDao.clearAll()
            syncShadowDao?.clearAll()
        }

        if (deletedTemplateIds.isNotEmpty()) {
            templateDao.deleteByIds(deletedTemplateIds)
            dailyTaskDao.deleteByTemplateIds(deletedTemplateIds)
        }
        if (activeTemplates.isNotEmpty()) {
            templateDao.upsertAll(activeTemplates.map { it.toEntity(templateStartDates[it.id]) })
        }

        if (deletedTaskIds.isNotEmpty()) dailyTaskDao.deleteByIds(deletedTaskIds)
        if (activeTasks.isNotEmpty()) dailyTaskDao.upsertAll(activeTasks.map { it.toEntity() })

        syncShadowDao?.upsertAll(
            remoteTemplates.mapNotNull { remote ->
                remote.updatedAt?.let {
                    SyncShadowEntity(TEMPLATE, remote.id, it, remote.deletedAt)
                }
            } + remoteTasks.mapNotNull { remote ->
                remote.updatedAt?.let {
                    SyncShadowEntity(TASK, remote.id, it, remote.deletedAt)
                }
            }
        )

        return RemoteSyncStatus(
            templateCount = activeTemplates.size,
            taskCount = activeTasks.size
        )
    }

    suspend fun status(userId: String): RemoteSyncStatus {
        val remoteTemplates = supabase.from("task_templates")
            .select(Columns.list("id", "deleted_at")) { filter { eq("user_id", userId) } }
            .decodeList<RemoteItemStatus>()

        val remoteTasks = supabase.from("daily_tasks")
            .select(Columns.list("id", "deleted_at")) { filter { eq("user_id", userId) } }
            .decodeList<RemoteItemStatus>()

        return RemoteSyncStatus(
            templateCount = remoteTemplates.count { it.deletedAt == null },
            taskCount = remoteTasks.count { it.deletedAt == null }
        )
    }

    suspend fun fetchSnapshot(userId: String): RemoteSyncSnapshot {
        val templates = supabase.from("task_templates")
            .select(Columns.ALL) { filter { eq("user_id", userId) } }
            .decodeList<RemoteTemplate>()
        val tasks = supabase.from("daily_tasks")
            .select(Columns.ALL) { filter { eq("user_id", userId) } }
            .decodeList<RemoteTask>()
        return RemoteSyncSnapshot(
            templatesById = templates.associateBy { it.id },
            tasksById = tasks.associateBy { it.id }
        )
    }

    suspend fun pushTask(userId: String, entity: DailyTaskEntity) {
        supabase.from("daily_tasks").upsert(entity.toRemote(userId)) {
            onConflict = USER_SCOPED_CONFLICT
        }
    }

    suspend fun pushTemplate(userId: String, entity: TaskTemplateEntity) {
        supabase.from("task_templates").upsert(entity.toRemote(userId)) {
            onConflict = USER_SCOPED_CONFLICT
        }
    }

    suspend fun fetchTask(userId: String, taskId: String): RemoteTask? =
        supabase.from("daily_tasks")
            .select(Columns.ALL) {
                filter {
                    eq("user_id", userId)
                    eq("id", taskId)
                }
                limit(1)
            }
            .decodeList<RemoteTask>()
            .firstOrNull()

    suspend fun fetchTemplate(userId: String, templateId: String): RemoteTemplate? =
        supabase.from("task_templates")
            .select(Columns.ALL) {
                filter {
                    eq("user_id", userId)
                    eq("id", templateId)
                }
                limit(1)
            }
            .decodeList<RemoteTemplate>()
            .firstOrNull()

    suspend fun fetchTasksByTemplate(userId: String, templateId: String): List<RemoteTask> =
        supabase.from("daily_tasks")
            .select(Columns.ALL) {
                filter {
                    eq("user_id", userId)
                    eq("template_id", templateId)
                }
            }
            .decodeList()

    suspend fun deleteTask(userId: String, taskId: String) {
        softDeleteTasks(userId, listOf(taskId))
    }

    suspend fun deleteTemplate(userId: String, templateId: String) {
        softDeleteTemplates(userId, listOf(templateId))
        softDeleteTasksByTemplate(userId, templateId)
    }

    private suspend fun softDeleteTasks(userId: String, taskIds: List<String>) {
        if (taskIds.isEmpty()) return
        taskIds.chunked(100).forEach { chunk ->
            supabase.from("daily_tasks").update(softDeletePatch()) {
                filter {
                    eq("user_id", userId)
                    isIn("id", chunk)
                }
            }
        }
    }

    private suspend fun softDeleteTemplates(userId: String, templateIds: List<String>) {
        if (templateIds.isEmpty()) return
        templateIds.chunked(100).forEach { chunk ->
            supabase.from("task_templates").update(softDeletePatch()) {
                filter {
                    eq("user_id", userId)
                    isIn("id", chunk)
                }
            }
        }
    }

    /** A template tombstone owns every materialized task derived from it, including rows created on other devices. */
    private suspend fun softDeleteTasksByTemplate(userId: String, templateId: String) {
        supabase.from("daily_tasks").update(softDeletePatch()) {
            filter {
                eq("user_id", userId)
                eq("template_id", templateId)
            }
        }
    }

    internal suspend fun requireMatchingAuthUser(userId: String) {
        val authUserId = supabase.auth.currentUserOrNull()?.id
            ?: runCatching {
                supabase.auth.retrieveUserForCurrentSession(updateSession = true).id
            }.getOrNull()

        check(authUserId == userId) {
            "Sync account mismatch: app user=$userId, auth user=${authUserId.orEmpty()}"
        }
    }

    internal fun RemoteTemplate.toEntity(startDateIso: String? = null) = TaskTemplateEntity(
        id = id,
        title = title,
        note = note,
        tagsSerialized = tags.orEmpty(),
        recurrenceType = recurrenceType,
        repeatDaysSerialized = repeatDays.orEmpty(),
        startDateIso = startDateIso,
        defaultStartMinute = defaultStartMinute,
        defaultEndMinute = defaultEndMinute,
        reminderEnabled = reminderEnabled ?: false
    )

    internal fun RemoteTask.toEntity() = DailyTaskEntity(
        id = id,
        templateId = templateId,
        dateIso = dateIso,
        title = title,
        note = note,
        tagsSerialized = tags.orEmpty(),
        isBig3 = isBig3 ?: false,
        isCompleted = isCompleted ?: false,
        startMinute = startMinute,
        endMinute = endMinute,
        reminderEnabled = reminderEnabled ?: false,
        source = source
    )

    private fun DailyTaskEntity.toRemote(userId: String) = RemoteTask(
        id = id,
        userId = userId,
        templateId = templateId,
        dateIso = dateIso,
        title = title,
        note = note,
        tags = tagsSerialized,
        isBig3 = isBig3,
        isCompleted = isCompleted,
        startMinute = startMinute,
        endMinute = endMinute,
        reminderEnabled = reminderEnabled,
        source = source,
        deletedAt = null
    )

    private fun TaskTemplateEntity.toRemote(userId: String) = RemoteTemplate(
        id = id,
        userId = userId,
        title = title,
        note = note,
        tags = tagsSerialized,
        recurrenceType = recurrenceType,
        repeatDays = repeatDaysSerialized,
        defaultStartMinute = defaultStartMinute,
        defaultEndMinute = defaultEndMinute,
        reminderEnabled = reminderEnabled,
        deletedAt = null
    )

    private fun softDeletePatch(): SoftDeletePatch =
        SoftDeletePatch(
            OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        )
}

internal const val TEMPLATE = "TEMPLATE"
internal const val TASK = "TASK"
