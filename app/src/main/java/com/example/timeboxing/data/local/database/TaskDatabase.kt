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
        private val seedTemplateIds = setOf("tpl-standup", "tpl-break", "tpl-funny")

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

        /**
         * 게스트 DB에 사용자가 직접 만든 데이터가 있는지 확인.
         * 시드 데이터(seed-*)는 제외.
         */
        fun hasGuestData(context: Context): Boolean {
            val guestDb = get(context, "guest")
            val templates = guestDb.taskTemplateDao().getAll()
                .filter { it.id !in seedTemplateIds }
            val tasks = guestDb.dailyTaskDao().getAll().filter { !it.id.startsWith("seed-") }
            return templates.isNotEmpty() || tasks.isNotEmpty()
        }

        /**
         * 게스트 DB → 새 계정 DB로 데이터 이전.
         * 게스트로 사용하다 구글 로그인 시 사용자가 "옮기기"를 선택했을 때 호출.
         *
         * @return 이전된 항목 수
         */
        fun migrateGuestData(context: Context, newUserId: String): Int {
            val guestDb = get(context, "guest")
            val userDb  = get(context, newUserId)

            val templates     = guestDb.taskTemplateDao().getAll()
                .filter { it.id !in seedTemplateIds }
            val filteredTasks = guestDb.dailyTaskDao().getAll().filter { !it.id.startsWith("seed-") }

            if (templates.isEmpty() && filteredTasks.isEmpty()) return 0

            if (templates.isNotEmpty()) userDb.taskTemplateDao().upsertAll(templates)
            if (filteredTasks.isNotEmpty()) userDb.dailyTaskDao().upsertAll(filteredTasks)

            return templates.size + filteredTasks.size
        }
    }
}
