package dev.jsjh.timebox.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_outbox",
    indices = [Index(value = ["nextAttemptAtEpochMs", "createdAtEpochMs"])]
)
data class SyncOutboxEntity(
    @PrimaryKey val queueKey: String,
    val generation: String,
    val entityType: String,
    val entityId: String,
    val templateId: String?,
    val operationType: String,
    val payload: String?,
    val baseRemoteUpdatedAt: String?,
    val createdAtEpochMs: Long,
    val attemptCount: Int,
    val nextAttemptAtEpochMs: Long,
    val lastError: String?
)
