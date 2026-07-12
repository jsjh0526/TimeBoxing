package dev.jsjh.timebox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.jsjh.timebox.data.local.entity.SyncShadowEntity

@Dao
interface SyncShadowDao {
    @Query("SELECT * FROM sync_shadows WHERE entityType = :entityType AND entityId = :entityId LIMIT 1")
    fun get(entityType: String, entityId: String): SyncShadowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: SyncShadowEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entities: List<SyncShadowEntity>)

    @Query("DELETE FROM sync_shadows WHERE entityType = :entityType AND entityId = :entityId")
    fun delete(entityType: String, entityId: String)

    @Query("DELETE FROM sync_shadows")
    fun clearAll()
}
