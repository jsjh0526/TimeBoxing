package dev.jsjh.timebox.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Small per-account markers for one-time sync transitions. */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val key: String,
    val value: String
)
