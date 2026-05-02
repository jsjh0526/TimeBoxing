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

data class RemoteSyncStatus(val templateCount: Int, val taskCount: Int)

private const val USER_SCOPED_CONFLICT = "user_id,id"

@Serializable
data class RemoteTemplate(
    @SerialName("id")                    val id: String,
    @SerialName("user_id")               val userId: String,
    @SerialName("title")                 val title: String,
    @SerialName("note")                  val note: String?     = null,
    @SerialName("tags")                  val tags: String?     = null,
    @SerialName("recurrence_type")       val recurrenceType: String?  = null,
    @SerialName("repeat_days")           val repeatDays: String?      = null,
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
    @SerialName("note")             val note: String?        = null,
    @SerialName("tags")             val tags: String?        = null,
    @SerialName("is_big3")          val isBig3: Boolean?     = false,
    @SerialName("is_completed")     val isCompleted: Boolean? = false,
    @SerialName("start_minute")     val startMinute: Int?    = null,
    @SerialName("end_minute")       val endMinute: Int?      = null,
    @SerialName("reminder_enabled") val reminderEnabled: Boolean? = false,
    @SerialName("source")           val source: String,
    @SerialName("updated_at")       val updatedAt: String?  = null,
    @SerialName("deleted_at")       val deletedAt: String?  = null
)

@Serializable
private data class RemoteItemStatus(
    @SerialName("id")         val id: String,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
private data class SoftDeletePatch(
    @SerialName("deleted_at") val deletedAt: String
)

object SupabaseSync {

    // ── 다운로드: Supabase → Room ─────────────────────────────────────────────
    // soft-deleted 항목은 로컬에서도 제거
    suspend fun pull(
        userId: String,
        templateDao: TaskTemplateDao,
        dailyTaskDao: DailyTaskDao
    ): RemoteSyncStatus {
        val remoteTemplates = supabase.from("task_templates")
            .select(Columns.ALL) { filter { eq("user_id", userId) } }
            .decodeList<RemoteTemplate>()

        val activeTemplates   = remoteTemplates.filter { it.deletedAt == null }
        val deletedTemplateIds = remoteTemplates.filter { it.deletedAt != null }.map { it.id }

        // Fix 3: 배치 삭제 — N+1 아님
        if (deletedTemplateIds.isNotEmpty()) {
            templateDao.deleteByIds(deletedTemplateIds)
            dailyTaskDao.deleteByTemplateIds(deletedTemplateIds)
        }
        if (activeTemplates.isNotEmpty()) templateDao.upsertAll(activeTemplates.map { it.toEntity() })

        val remoteTasks   = supabase.from("daily_tasks")
            .select(Columns.ALL) { filter { eq("user_id", userId) } }
            .decodeList<RemoteTask>()

        val activeTasks   = remoteTasks.filter { it.deletedAt == null }
        val deletedTaskIds = remoteTasks.filter { it.deletedAt != null }.map { it.id }

        if (deletedTaskIds.isNotEmpty()) dailyTaskDao.deleteByIds(deletedTaskIds)
        if (activeTasks.isNotEmpty()) dailyTaskDao.upsertAll(activeTasks.map { it.toEntity() })

        return RemoteSyncStatus(
            templateCount = activeTemplates.size,
            taskCount     = activeTasks.size
        )
    }

    // ── 전체 동기화: 로컬 우선 (Sync Now) ────────────────────────────────────
    suspend fun syncAll(
        userId: String,
        templateDao: TaskTemplateDao,
        dailyTaskDao: DailyTaskDao
    ): RemoteSyncStatus {
        // Fix 4: 여기서 1번만 auth 체크 — push* 내부에서는 중복 체크 안 함
        requireMatchingAuthUser(userId)

        val templates = templateDao.getAll()
        val tasks     = dailyTaskDao.getAll()

        // Fix 3: remote에만 있는 항목 배치 soft-delete
        softDeleteRemoteItemsMissingLocally(userId, templates, tasks)

        if (templates.isNotEmpty()) pushTemplatesInternal(userId, templates)
        if (tasks.isNotEmpty())     pushTasksInternal(userId, tasks)

        // Fix 1: check() 제거 — status 불일치는 오류가 아니라 다음 sync 때 재시도하면 됨
        // 대신 결과만 반환해서 SyncManager가 UI에 표시
        return status(userId)
    }

    // ── remote에만 있는 항목 배치 soft-delete ────────────────────────────────
    // Fix 3: forEach 건당 요청 → 배치 처리
    private suspend fun softDeleteRemoteItemsMissingLocally(
        userId: String,
        templates: List<TaskTemplateEntity>,
        tasks: List<DailyTaskEntity>
    ) {
        val localTemplateIds = templates.map { it.id }.toSet()
        val remoteTemplates  = supabase.from("task_templates")
            .select(Columns.list("id", "deleted_at")) { filter { eq("user_id", userId) } }
            .decodeList<RemoteItemStatus>()

        val toDeleteTemplateIds = remoteTemplates
            .filter { it.deletedAt == null && it.id !in localTemplateIds }
            .map { it.id }

        // 배치로 한 번에 soft-delete
        if (toDeleteTemplateIds.isNotEmpty()) {
            softDeleteTemplates(userId, toDeleteTemplateIds)
            softDeleteTasksByTemplateIds(userId, toDeleteTemplateIds)
        }

        val localTaskIds = tasks.map { it.id }.toSet()
        val remoteTasks  = supabase.from("daily_tasks")
            .select(Columns.list("id", "deleted_at")) { filter { eq("user_id", userId) } }
            .decodeList<RemoteItemStatus>()

        val toDeleteTaskIds = remoteTasks
            .filter { it.deletedAt == null && it.id !in localTaskIds }
            .map { it.id }

        if (toDeleteTaskIds.isNotEmpty()) {
            softDeleteTasks(userId, toDeleteTaskIds)
        }
    }

    // ── 원격 상태 조회 ────────────────────────────────────────────────────────
    suspend fun status(userId: String): RemoteSyncStatus {
        val remoteTemplates = supabase.from("task_templates")
            .select(Columns.list("id", "deleted_at")) { filter { eq("user_id", userId) } }
            .decodeList<RemoteItemStatus>()

        val remoteTasks = supabase.from("daily_tasks")
            .select(Columns.list("id", "deleted_at")) { filter { eq("user_id", userId) } }
            .decodeList<RemoteItemStatus>()

        return RemoteSyncStatus(
            templateCount = remoteTemplates.count { it.deletedAt == null },
            taskCount     = remoteTasks.count { it.deletedAt == null }
        )
    }

    // ── 공개 업로드 API (SyncedTaskRepository에서 호출) ───────────────────────
    // Fix 4: 개별 push에서는 auth 체크 없음 — 쓰기마다 Supabase 요청 최소화
    suspend fun pushTask(userId: String, entity: DailyTaskEntity) {
        supabase.from("daily_tasks").upsert(entity.toRemote(userId)) {
            onConflict = USER_SCOPED_CONFLICT
        }
    }

    suspend fun pushTasks(userId: String, entities: List<DailyTaskEntity>) {
        if (entities.isEmpty()) return
        supabase.from("daily_tasks").upsert(entities.map { it.toRemote(userId) }) {
            onConflict = USER_SCOPED_CONFLICT
        }
    }

    suspend fun pushTemplate(userId: String, entity: TaskTemplateEntity) {
        supabase.from("task_templates").upsert(entity.toRemote(userId)) {
            onConflict = USER_SCOPED_CONFLICT
        }
    }

    suspend fun pushTemplates(userId: String, entities: List<TaskTemplateEntity>) {
        if (entities.isEmpty()) return
        supabase.from("task_templates").upsert(entities.map { it.toRemote(userId) }) {
            onConflict = USER_SCOPED_CONFLICT
        }
    }

    // ── 내부 업로드 (syncAll 전용 — auth 체크 없음) ──────────────────────────
    private suspend fun pushTasksInternal(userId: String, entities: List<DailyTaskEntity>) {
        supabase.from("daily_tasks").upsert(entities.map { it.toRemote(userId) }) {
            onConflict = USER_SCOPED_CONFLICT
        }
    }

    private suspend fun pushTemplatesInternal(userId: String, entities: List<TaskTemplateEntity>) {
        supabase.from("task_templates").upsert(entities.map { it.toRemote(userId) }) {
            onConflict = USER_SCOPED_CONFLICT
        }
    }

    // ── 공개 삭제 API ─────────────────────────────────────────────────────────
    suspend fun deleteTask(userId: String, taskId: String) {
        softDeleteTasks(userId, listOf(taskId))
    }

    suspend fun deleteTemplate(userId: String, templateId: String) {
        softDeleteTemplates(userId, listOf(templateId))
    }

    suspend fun deleteTasksByTemplateId(userId: String, templateId: String) {
        softDeleteTasksByTemplateIds(userId, listOf(templateId))
    }

    // ── 배치 soft-delete (내부) ───────────────────────────────────────────────
    // Fix 3: id IN (...) 으로 한 번에 처리
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

    private suspend fun softDeleteTasksByTemplateIds(userId: String, templateIds: List<String>) {
        if (templateIds.isEmpty()) return
        templateIds.chunked(100).forEach { chunk ->
            supabase.from("daily_tasks").update(softDeletePatch()) {
                filter {
                    eq("user_id", userId)
                    isIn("template_id", chunk)
                }
            }
        }
    }

    // ── Auth 체크 ─────────────────────────────────────────────────────────────
    private suspend fun requireMatchingAuthUser(userId: String) {
        val authUserId = supabase.auth.currentUserOrNull()?.id
            ?: runCatching {
                supabase.auth.retrieveUserForCurrentSession(updateSession = true).id
            }.getOrNull()

        check(authUserId == userId) {
            "Sync account mismatch: app user=$userId, auth user=${authUserId.orEmpty()}"
        }
    }

    // ── Entity ↔ Remote 변환 ──────────────────────────────────────────────────

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

    // Fix 2: deleted_at = null 명시 → upsert 시 soft-delete 상태 초기화
    // (이전엔 필드 자체가 없어서 Supabase가 기존 deleted_at 값을 보존했음)
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
        source          = source,
        deletedAt       = null   // Fix 2: upsert 시 항상 restore
    )

    private fun TaskTemplateEntity.toRemote(userId: String) = RemoteTemplate(
        id                 = id,
        userId             = userId,
        title              = title,
        note               = note,
        tags               = tagsSerialized,
        recurrenceType     = recurrenceType,
        repeatDays         = repeatDaysSerialized,
        defaultStartMinute = defaultStartMinute,
        defaultEndMinute   = defaultEndMinute,
        reminderEnabled    = reminderEnabled,
        deletedAt          = null   // Fix 2: upsert 시 항상 restore
    )

    private fun softDeletePatch(): SoftDeletePatch =
        SoftDeletePatch(
            OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        )
}
