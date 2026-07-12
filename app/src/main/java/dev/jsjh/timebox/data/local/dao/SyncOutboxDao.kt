package dev.jsjh.timebox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.jsjh.timebox.data.local.entity.SyncOutboxEntity

@Dao
interface SyncOutboxDao {
    @Query("SELECT * FROM sync_outbox WHERE queueKey = :queueKey LIMIT 1")
    fun get(queueKey: String): SyncOutboxEntity?

    @Query("SELECT * FROM sync_outbox WHERE nextAttemptAtEpochMs <= :nowEpochMs ORDER BY createdAtEpochMs LIMIT :limit")
    fun getDue(nowEpochMs: Long, limit: Int = 100): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox ORDER BY createdAtEpochMs")
    fun getAll(): List<SyncOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: SyncOutboxEntity)

    @Query("DELETE FROM sync_outbox WHERE queueKey = :queueKey AND generation = :generation")
    fun deleteGeneration(queueKey: String, generation: String)

    @Query("DELETE FROM sync_outbox WHERE entityType = :entityType AND entityId = :entityId")
    fun deleteEntity(entityType: String, entityId: String)

    @Query("DELETE FROM sync_outbox WHERE templateId = :templateId")
    fun deleteByTemplateId(templateId: String)

    @Query("UPDATE sync_outbox SET baseRemoteUpdatedAt = :remoteUpdatedAt WHERE queueKey = :queueKey AND generation != :processedGeneration")
    fun advanceReplacementBase(queueKey: String, processedGeneration: String, remoteUpdatedAt: String)

    @Query("UPDATE sync_outbox SET attemptCount = :attemptCount, nextAttemptAtEpochMs = :nextAttemptAtEpochMs, lastError = :lastError WHERE queueKey = :queueKey AND generation = :generation")
    fun markRetry(
        queueKey: String,
        generation: String,
        attemptCount: Int,
        nextAttemptAtEpochMs: Long,
        lastError: String
    )

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun count(): Int

    @Query("SELECT MIN(nextAttemptAtEpochMs) FROM sync_outbox")
    fun nextAttemptAt(): Long?
}
