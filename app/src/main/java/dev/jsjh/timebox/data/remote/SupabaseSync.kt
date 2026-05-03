package dev.jsjh.timebox.data.remote

import dev.jsjh.timebox.auth.supabase
import dev.jsjh.timebox.data.local.dao.DailyTaskDao
import dev.jsjh.timebox.data.local.dao.TaskTemplateDao
import dev.jsjh.timebox.data.local.entity.DailyTaskEntity
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

    // ?? ?ㅼ슫濡쒕뱶: Supabase ??Room ?????????????????????????????????????????????
    // soft-deleted ??ぉ? 濡쒖뺄?먯꽌???쒓굅
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

        // Fix 3: 諛곗튂 ??젣 ??N+1 ?꾨떂
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

    // ?? ?꾩껜 ?숆린?? 濡쒖뺄 ?곗꽑 (Sync Now) ????????????????????????????????????
    suspend fun syncAll(
        userId: String,
        templateDao: TaskTemplateDao,
        dailyTaskDao: DailyTaskDao
    ): RemoteSyncStatus {
        // Fix 4: ?ш린??1踰덈쭔 auth 泥댄겕 ??push* ?대??먯꽌??以묐났 泥댄겕 ????        requireMatchingAuthUser(userId)

        val templates = templateDao.getAll()
        val tasks     = dailyTaskDao.getAll()

        // Fix 3: remote?먮쭔 ?덈뒗 ??ぉ 諛곗튂 soft-delete
        softDeleteRemoteItemsMissingLocally(userId, templates, tasks)

        if (templates.isNotEmpty()) pushTemplatesInternal(userId, templates)
        if (tasks.isNotEmpty())     pushTasksInternal(userId, tasks)

        // Fix 1: check() ?쒓굅 ??status 遺덉씪移섎뒗 ?ㅻ쪟媛 ?꾨땲???ㅼ쓬 sync ???ъ떆?꾪븯硫???        // ???寃곌낵留?諛섑솚?댁꽌 SyncManager媛 UI???쒖떆
        return status(userId)
    }

    // ?? remote?먮쭔 ?덈뒗 ??ぉ 諛곗튂 soft-delete ????????????????????????????????
    // Fix 3: forEach 嫄대떦 ?붿껌 ??諛곗튂 泥섎━
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

        // 諛곗튂濡???踰덉뿉 soft-delete
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

    // ?? ?먭꺽 ?곹깭 議고쉶 ????????????????????????????????????????????????????????
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

    // ?? 怨듦컻 ?낅줈??API (SyncedTaskRepository?먯꽌 ?몄텧) ???????????????????????
    // Individual push calls skip auth checks; syncAll validates the active user once.
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

    // ?? ?대? ?낅줈??(syncAll ?꾩슜 ??auth 泥댄겕 ?놁쓬) ??????????????????????????
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

    // ?? 怨듦컻 ??젣 API ?????????????????????????????????????????????????????????
    suspend fun deleteTask(userId: String, taskId: String) {
        softDeleteTasks(userId, listOf(taskId))
    }

    suspend fun deleteTemplate(userId: String, templateId: String) {
        softDeleteTemplates(userId, listOf(templateId))
    }

    suspend fun deleteTasksByTemplateId(userId: String, templateId: String) {
        softDeleteTasksByTemplateIds(userId, listOf(templateId))
    }

    // ?? 諛곗튂 soft-delete (?대?) ???????????????????????????????????????????????
    // Fix 3: id IN (...) ?쇰줈 ??踰덉뿉 泥섎━
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

    // ?? Auth 泥댄겕 ?????????????????????????????????????????????????????????????
    private suspend fun requireMatchingAuthUser(userId: String) {
        val authUserId = supabase.auth.currentUserOrNull()?.id
            ?: runCatching {
                supabase.auth.retrieveUserForCurrentSession(updateSession = true).id
            }.getOrNull()

        check(authUserId == userId) {
            "Sync account mismatch: app user=$userId, auth user=${authUserId.orEmpty()}"
        }
    }

    // ?? Entity ??Remote 蹂????????????????????????????????????????????????????

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

    // Fix 2: deleted_at = null 紐낆떆 ??upsert ??soft-delete ?곹깭 珥덇린??    // (?댁쟾???꾨뱶 ?먯껜媛 ?놁뼱??Supabase媛 湲곗〈 deleted_at 媛믪쓣 蹂댁〈?덉쓬)
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
        deletedAt       = null   // Fix 2: upsert ????긽 restore
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
        deletedAt          = null   // Fix 2: upsert ????긽 restore
    )

    private fun softDeletePatch(): SoftDeletePatch =
        SoftDeletePatch(
            OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        )
}
