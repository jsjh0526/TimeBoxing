package com.example.timeboxing.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.timeboxing.data.local.dao.DailyTaskDao
import com.example.timeboxing.data.local.dao.TaskTemplateDao
import com.example.timeboxing.data.local.entity.DailyTaskEntity
import com.example.timeboxing.data.local.entity.TaskTemplateEntity

@Database(
    entities = [TaskTemplateEntity::class, DailyTaskEntity::class],
    version = 2,
    exportSchema = false
)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskTemplateDao(): TaskTemplateDao
    abstract fun dailyTaskDao(): DailyTaskDao

    companion object {
        private val instances = mutableMapOf<String, TaskDatabase>()

        fun get(context: Context, userId: String): TaskDatabase {
            val key = userId
                .ifBlank { "unknown_user" }
                .replace(Regex("[^a-zA-Z0-9_]"), "_")
                .take(64)

            return instances[key] ?: synchronized(this) {
                instances[key] ?: Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "timeboxing_$key.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .allowMainThreadQueries()
                    .build()
                    .also { instances[key] = it }
            }
        }
    }
}
