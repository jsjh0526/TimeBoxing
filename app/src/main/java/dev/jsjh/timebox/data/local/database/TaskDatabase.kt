package dev.jsjh.timebox.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.jsjh.timebox.data.local.dao.DailyTaskDao
import dev.jsjh.timebox.data.local.dao.TaskTemplateDao
import dev.jsjh.timebox.data.local.entity.DailyTaskEntity
import dev.jsjh.timebox.data.local.entity.TaskTemplateEntity

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
        private val seedTemplateIds = setOf("tpl-standup", "tpl-break")

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
                    .addMigrations(MIGRATION_1_2)
                    .allowMainThreadQueries()
                    .build()
                    .also { instances[key] = it }
            }
        }

        /**
         * 寃뚯뒪??DB???ъ슜?먭? 吏곸젒 留뚮뱺 ?곗씠?곌? ?덈뒗吏 ?뺤씤.
         * ?쒕뱶 ?곗씠??seed-*)???쒖쇅.
         */
        fun hasGuestData(context: Context): Boolean {
            val guestDb = get(context, "guest")
            val templates = guestDb.taskTemplateDao().getAll()
                .filter { it.id !in seedTemplateIds }
            val tasks = guestDb.dailyTaskDao().getAll().filter { !it.id.startsWith("seed-") }
            return templates.isNotEmpty() || tasks.isNotEmpty()
        }

        /**
         * 寃뚯뒪??DB ????怨꾩젙 DB濡??곗씠???댁쟾.
         * 寃뚯뒪?몃줈 ?ъ슜?섎떎 援ш? 濡쒓렇?????ъ슜?먭? "??린湲?瑜??좏깮?덉쓣 ???몄텧.
         *
         * @return ?댁쟾????ぉ ??         */
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task_templates ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily_tasks ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
