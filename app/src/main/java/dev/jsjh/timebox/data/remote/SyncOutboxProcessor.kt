package dev.jsjh.timebox.data.remote

import androidx.room.withTransaction
import dev.jsjh.timebox.data.local.dao.DailyTaskDao
import dev.jsjh.timebox.data.local.dao.SyncMetadataDao
import dev.jsjh.timebox.data.local.dao.SyncOutboxDao
import dev.jsjh.timebox.data.local.dao.SyncShadowDao
import dev.jsjh.timebox.data.local.dao.TaskTemplateDao
import dev.jsjh.timebox.data.local.database.TaskDatabase
import dev.jsjh.timebox.data.local.entity.DailyTaskEntity
import dev.jsjh.timebox.data.local.entity.SyncMetadataEntity
import dev.jsjh.timebox.data.local.entity.SyncOutboxEntity
import dev.jsjh.timebox.data.local.entity.SyncShadowEntity
import dev.jsjh.timebox.data.local.entity.TaskTemplateEntity
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val UPSERT = "UPSERT"
internal const val DELETE = "DELETE"
private const val BOOTSTRAP_KEY = "sync_bootstrap_v1"

data class OutboxDrainResult(
    val processedCount: Int,
    val pendingCount: Int,
    val firstFailure: Throwable?
)

data class DurableSyncResult(
    val remoteStatus: RemoteSyncStatus
)

/**
 * Phase 1 sync policy:
 * - only rows explicitly queued by a local mutation are uploaded;
 * - queued local mutations are offered to the server before reconciliation;
 * - a failed upload remains local and queued instead of being overwritten by
 *   the server snapshot;
 * - there is never an absence-based remote delete.
 */
object DurableSync {
    suspend fun reconcile(userId: String, database: TaskDatabase): RemoteSyncStatus =
        withContext(Dispatchers.IO) {
            SyncWorkCoordinator.withUserLock(userId) {
                SupabaseSync.requireMatchingAuthUser(userId)
                val processor = processorFor(userId, database)
                if (processor.pendingCount() > 0) {
                    // Best effort: failures are recorded on the outbox row. We still fetch
                    // other server changes, but the failed local row must remain protected.
                    processor.drain(force = true)
                }
                val snapshot = SupabaseSync.fetchSnapshot(userId)
                database.withTransaction {
                    SnapshotMerger(
                        database = database,
                        snapshot = snapshot,
                        discardPendingRemoteRows = false
                    ).merge()
                }
                snapshot.status()
            }
        }

    suspend fun syncAll(userId: String, database: TaskDatabase): DurableSyncResult =
        withContext(Dispatchers.IO) {
            SyncWorkCoordinator.withUserLock(userId) {
                SupabaseSync.requireMatchingAuthUser(userId)
                val processor = processorFor(userId, database)
                val drain = processor.drain(force = true)
                drain.firstFailure?.let { throw it }
                check(drain.pendingCount == 0) {
                    "Network sync has ${drain.pendingCount} queued operation(s) pending retry"
                }
                val snapshot = SupabaseSync.fetchSnapshot(userId)
                database.withTransaction {
                    SnapshotMerger(
                        database = database,
                        snapshot = snapshot,
                        discardPendingRemoteRows = false
                    ).merge()
                }
                DurableSyncResult(snapshot.status())
            }
        }

    suspend fun uploadPending(userId: String, database: TaskDatabase): OutboxDrainResult =
        withContext(Dispatchers.IO) {
            SyncWorkCoordinator.withUserLock(userId) {
                SupabaseSync.requireMatchingAuthUser(userId)
                processorFor(userId, database).drain(force = true)
            }
        }

    /** Queues only rows which the currently known server snapshot does not contain. */
    fun queueLocalRowsMissingFromServer(database: TaskDatabase) {
        val processor = processorFor("", database)
        val shadows = database.syncShadowDao()
        database.taskTemplateDao().getAll()
            .filter { shadows.get(TEMPLATE, it.id) == null }
            .forEach(processor::queueTemplateUpsert)
        database.dailyTaskDao().getAll()
            .filter { shadows.get(TASK, it.id) == null }
            .forEach(processor::queueTaskUpsert)
    }

    fun queueSeedData(userId: String, database: TaskDatabase) {
        val processor = processorFor(userId, database)
        database.taskTemplateDao().getAll().forEach(processor::queueTemplateUpsert)
        database.dailyTaskDao().getAll().forEach(processor::queueTaskUpsert)
    }

    private fun processorFor(userId: String, database: TaskDatabase) = SyncOutboxProcessor(
        userId = userId,
        templateDao = database.taskTemplateDao(),
        dailyTaskDao = database.dailyTaskDao(),
        outboxDao = database.syncOutboxDao(),
        shadowDao = database.syncShadowDao()
    )
}

/** Durable queue shared by all local mutation paths. */
class SyncOutboxProcessor(
    private val userId: String,
    private val templateDao: TaskTemplateDao,
    private val dailyTaskDao: DailyTaskDao,
    private val outboxDao: SyncOutboxDao,
    private val shadowDao: SyncShadowDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun queueTaskUpsert(entity: DailyTaskEntity) = queue(TASK, entity.id, entity.templateId, UPSERT, json.encodeToString(entity))
    fun queueTaskDelete(entity: DailyTaskEntity) = queue(TASK, entity.id, entity.templateId, DELETE, json.encodeToString(entity))
    fun queueTemplateUpsert(entity: TaskTemplateEntity) = queue(TEMPLATE, entity.id, entity.id, UPSERT, json.encodeToString(entity))
    fun queueTemplateDelete(entity: TaskTemplateEntity) = queue(TEMPLATE, entity.id, entity.id, DELETE, json.encodeToString(entity))

    fun pendingCount(): Int = outboxDao.count()

    /**
     * Uploads the immutable queue entries that existed at the start of this run.
     * One full snapshot verifies all writes, avoiding one remote GET per item.
     */
    suspend fun drain(force: Boolean): OutboxDrainResult {
        val entries = if (force) outboxDao.getAll() else outboxDao.getDue(System.currentTimeMillis())
        val sent = mutableListOf<SyncOutboxEntity>()
        var firstFailure: Throwable? = null

        entries.forEach { entry ->
            if (outboxDao.get(entry.queueKey)?.generation != entry.generation) return@forEach
            runCatching {
                when (entry.entityType) {
                    TASK -> sendTask(entry)
                    TEMPLATE -> sendTemplate(entry)
                    else -> error("Unknown sync entity type: ${entry.entityType}")
                }
                sent += entry
            }.onFailure { error ->
                if (firstFailure == null) firstFailure = error
                scheduleRetry(entry, error)
            }
        }

        if (sent.isNotEmpty()) {
            runCatching { SupabaseSync.fetchSnapshot(userId) }
                .onSuccess { snapshot -> sent.forEach { finishFromSnapshot(it, snapshot) } }
                .onFailure { error ->
                    if (firstFailure == null) firstFailure = error
                    sent.forEach { scheduleRetry(it, error) }
                }
        }

        return OutboxDrainResult(
            processedCount = sent.size,
            pendingCount = outboxDao.count(),
            firstFailure = firstFailure
        )
    }

    private suspend fun sendTask(entry: SyncOutboxEntity) {
        when (entry.operationType) {
            UPSERT -> SupabaseSync.pushTask(userId, decodeTask(entry))
            DELETE -> SupabaseSync.deleteTask(userId, entry.entityId)
            else -> error("Unknown sync operation: ${entry.operationType}")
        }
    }

    private suspend fun sendTemplate(entry: SyncOutboxEntity) {
        when (entry.operationType) {
            UPSERT -> SupabaseSync.pushTemplate(userId, decodeTemplate(entry))
            DELETE -> SupabaseSync.deleteTemplate(userId, entry.entityId)
            else -> error("Unknown sync operation: ${entry.operationType}")
        }
    }

    private fun finishFromSnapshot(entry: SyncOutboxEntity, snapshot: RemoteSyncSnapshot) {
        val remote = when (entry.entityType) {
            TASK -> snapshot.tasksById[entry.entityId]
            TEMPLATE -> snapshot.templatesById[entry.entityId]
            else -> null
        } ?: run {
            scheduleRetry(entry, IllegalStateException("Sync verification failed for ${entry.queueKey}"))
            return
        }
        val updatedAt = when (remote) {
            is RemoteTask -> remote.updatedAt
            is RemoteTemplate -> remote.updatedAt
            else -> null
        } ?: run {
            scheduleRetry(entry, IllegalStateException("Sync verification failed: updated_at missing for ${entry.queueKey}"))
            return
        }
        val deletedAt = when (remote) {
            is RemoteTask -> remote.deletedAt
            is RemoteTemplate -> remote.deletedAt
            else -> null
        }
        shadowDao.upsert(SyncShadowEntity(entry.entityType, entry.entityId, updatedAt, deletedAt))
        outboxDao.deleteGeneration(entry.queueKey, entry.generation)
        outboxDao.advanceReplacementBase(entry.queueKey, entry.generation, updatedAt)
    }

    private fun queue(
        entityType: String,
        entityId: String,
        templateId: String?,
        operationType: String,
        payload: String
    ) {
        val queueKey = "$entityType:$entityId"
        val existing = outboxDao.get(queueKey)
        val base = existing?.baseRemoteUpdatedAt ?: shadowDao.get(entityType, entityId)?.remoteUpdatedAt
        val now = System.currentTimeMillis()
        outboxDao.upsert(
            SyncOutboxEntity(
                queueKey = queueKey,
                generation = UUID.randomUUID().toString(),
                entityType = entityType,
                entityId = entityId,
                templateId = templateId,
                operationType = operationType,
                payload = payload,
                baseRemoteUpdatedAt = base,
                createdAtEpochMs = now,
                attemptCount = 0,
                nextAttemptAtEpochMs = now,
                lastError = null
            )
        )
    }

    private fun scheduleRetry(entry: SyncOutboxEntity, error: Throwable) {
        outboxDao.markRetry(
            queueKey = entry.queueKey,
            generation = entry.generation,
            attemptCount = entry.attemptCount + 1,
            nextAttemptAtEpochMs = System.currentTimeMillis() + 5_000L,
            lastError = error.message.orEmpty().take(500)
        )
    }

    private fun decodeTask(entry: SyncOutboxEntity): DailyTaskEntity =
        json.decodeFromString(entry.payload ?: error("Task payload missing"))

    private fun decodeTemplate(entry: SyncOutboxEntity): TaskTemplateEntity =
        json.decodeFromString(entry.payload ?: error("Template payload missing"))
}

internal class SnapshotMerger(
    private val database: TaskDatabase,
    private val snapshot: RemoteSyncSnapshot,
    private val discardPendingRemoteRows: Boolean
) {
    private val templates = database.taskTemplateDao()
    private val tasks = database.dailyTaskDao()
    private val outbox = database.syncOutboxDao()
    private val shadows = database.syncShadowDao()
    private val metadata: SyncMetadataDao = database.syncMetadataDao()

    fun merge() {
        val firstBootstrap = metadata.getValue(BOOTSTRAP_KEY) == null
        val remoteTemplateIds = snapshot.templatesById.keys
        val remoteTaskIds = snapshot.tasksById.keys
        val deletedRemoteTemplateIds = snapshot.templatesById.values
            .asSequence()
            .filter { it.deletedAt != null }
            .filter { remote ->
                // A queued local template upsert intentionally wins over an older remote tombstone.
                outbox.get("$TEMPLATE:${remote.id}")?.operationType != UPSERT
            }
            .mapTo(hashSetOf()) { it.id }

        val remoteTemplateStartDates = snapshot.tasksById.values
            .asSequence()
            .filter { it.deletedAt == null }
            .mapNotNull { it.templateId?.let { templateId -> templateId to it.dateIso } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, dates) -> dates.minOrNull() }

        snapshot.templatesById.values.forEach { remote ->
            if (!discardPendingRemoteRows && hasPending(TEMPLATE, remote.id)) {
                remember(TEMPLATE, remote.id, remote.updatedAt, remote.deletedAt)
                return@forEach
            }
            if (remote.deletedAt == null) {
                templates.upsert(SupabaseSync.run { remote.toEntity(remoteTemplateStartDates[remote.id]) })
                if (discardPendingRemoteRows) outbox.deleteEntity(TEMPLATE, remote.id)
            } else {
                templates.deleteById(remote.id)
                tasks.deleteByTemplateId(remote.id)
                if (discardPendingRemoteRows) outbox.deleteByTemplateId(remote.id)
            }
            remember(TEMPLATE, remote.id, remote.updatedAt, remote.deletedAt)
        }

        snapshot.tasksById.values.forEach { remote ->
            if (remote.templateId in deletedRemoteTemplateIds) {
                // Never resurrect a materialized child whose parent template is tombstoned.
                tasks.deleteByIds(listOf(remote.id))
                if (outbox.get("$TASK:${remote.id}")?.operationType == UPSERT) {
                    outbox.deleteEntity(TASK, remote.id)
                }
                remember(TASK, remote.id, remote.updatedAt, remote.deletedAt)
                return@forEach
            }
            if (!discardPendingRemoteRows && hasPending(TASK, remote.id)) {
                remember(TASK, remote.id, remote.updatedAt, remote.deletedAt)
                return@forEach
            }
            if (remote.deletedAt == null) {
                tasks.upsert(SupabaseSync.run { remote.toEntity() })
            } else {
                tasks.deleteByIds(listOf(remote.id))
            }
            if (discardPendingRemoteRows) outbox.deleteEntity(TASK, remote.id)
            remember(TASK, remote.id, remote.updatedAt, remote.deletedAt)
        }

        if (firstBootstrap) {
            val processor = SyncOutboxProcessor(
                userId = "",
                templateDao = templates,
                dailyTaskDao = tasks,
                outboxDao = outbox,
                shadowDao = shadows
            )
            templates.getAll().filter { it.id !in remoteTemplateIds }.forEach(processor::queueTemplateUpsert)
            tasks.getAll().filter { it.id !in remoteTaskIds }.forEach(processor::queueTaskUpsert)
            metadata.upsert(SyncMetadataEntity(BOOTSTRAP_KEY, "complete"))
        }
    }

    private fun remember(type: String, id: String, updatedAt: String?, deletedAt: String?) {
        updatedAt ?: return
        shadows.upsert(SyncShadowEntity(type, id, updatedAt, deletedAt))
    }

    private fun hasPending(type: String, id: String): Boolean =
        outbox.get("$type:$id") != null
}

private fun RemoteSyncSnapshot.status() = RemoteSyncStatus(
    templateCount = templatesById.values.count { it.deletedAt == null },
    taskCount = tasksById.values.count { it.deletedAt == null }
)
