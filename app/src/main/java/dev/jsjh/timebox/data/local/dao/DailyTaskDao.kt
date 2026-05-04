package dev.jsjh.timebox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.jsjh.timebox.data.local.entity.DailyTaskEntity

@Dao
interface DailyTaskDao {
    @Query("SELECT * FROM daily_tasks WHERE dateIso = :dateIso ORDER BY CASE WHEN startMinute IS NULL THEN 1 ELSE 0 END, startMinute, title")
    fun getByDate(dateIso: String): List<DailyTaskEntity>

    @Query("SELECT * FROM daily_tasks WHERE dateIso = :dateIso AND id = :taskId LIMIT 1")
    fun getById(dateIso: String, taskId: String): DailyTaskEntity?

    @Query("SELECT * FROM daily_tasks ORDER BY dateIso")
    fun getAll(): List<DailyTaskEntity>

    @Query("SELECT * FROM daily_tasks WHERE templateId = :templateId")
    fun getByTemplateId(templateId: String): List<DailyTaskEntity>

    @Query("SELECT DISTINCT dateIso FROM daily_tasks ORDER BY dateIso")
    fun getCachedDates(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: DailyTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entities: List<DailyTaskEntity>)

    @Query("DELETE FROM daily_tasks WHERE dateIso = :dateIso AND id = :taskId")
    fun deleteById(dateIso: String, taskId: String)

    @Query("DELETE FROM daily_tasks WHERE id IN (:taskIds)")
    fun deleteByIds(taskIds: List<String>)

    @Query("DELETE FROM daily_tasks WHERE templateId = :templateId")
    fun deleteByTemplateId(templateId: String)

    @Query("DELETE FROM daily_tasks WHERE templateId IN (:templateIds)")
    fun deleteByTemplateIds(templateIds: List<String>)

    @Query("DELETE FROM daily_tasks WHERE dateIso = :dateIso AND templateId = :templateId")
    fun deleteByDateAndTemplateId(dateIso: String, templateId: String)

    @Query("DELETE FROM daily_tasks WHERE dateIso = :dateIso AND source = :source")
    fun deleteByDateAndSource(dateIso: String, source: String)

    @Query("DELETE FROM daily_tasks")
    fun clearAll()

    @Query("SELECT COUNT(*) FROM daily_tasks")
    fun count(): Int
}
