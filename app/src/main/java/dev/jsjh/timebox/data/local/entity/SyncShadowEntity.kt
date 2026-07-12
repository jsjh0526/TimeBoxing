package dev.jsjh.timebox.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "sync_shadows",
    primaryKeys = ["entityType", "entityId"]
)
data class SyncShadowEntity(
    val entityType: String,
    val entityId: String,
    val remoteUpdatedAt: String,
    val remoteDeletedAt: String?
)
