package com.example.timeboxing.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.timeboxing.data.local.entity.TaskTemplateEntity

@Dao
interface TaskTemplateDao {
    @Query("SELECT * FROM task_templates ORDER BY id")
    fun getAll(): List<TaskTemplateEntity>

    @Query("SELECT * FROM task_templates WHERE id = :id LIMIT 1")
    fun getById(id: String): TaskTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: TaskTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entities: List<TaskTemplateEntity>)

    @Query("DELETE FROM task_templates WHERE id = :id")
    fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM task_templates")
    fun count(): Int
}
