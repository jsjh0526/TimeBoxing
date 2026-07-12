package dev.jsjh.timebox.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.jsjh.timebox.data.local.dao.DailyTaskDao
import dev.jsjh.timebox.data.local.dao.SyncOutboxDao
import dev.jsjh.timebox.data.local.dao.SyncMetadataDao
import dev.jsjh.timebox.data.local.dao.SyncShadowDao
import dev.jsjh.timebox.data.local.dao.TaskTemplateDao
import dev.jsjh.timebox.data.local.entity.DailyTaskEntity
import dev.jsjh.timebox.data.local.entity.SyncOutboxEntity
import dev.jsjh.timebox.data.local.entity.SyncMetadataEntity
import dev.jsjh.timebox.data.local.entity.SyncShadowEntity
import dev.jsjh.timebox.data.local.entity.TaskTemplateEntity

@Database(
    entities = [
        TaskTemplateEntity::class,
        DailyTaskEntity::class,
        SyncOutboxEntity::class,
        SyncShadowEntity::class,
        SyncMetadataEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskTemplateDao(): TaskTemplateDao
    abstract fun dailyTaskDao(): DailyTaskDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun syncShadowDao(): SyncShadowDao
    abstract fun syncMetadataDao(): SyncMetadataDao

    companion object {
        private val instances = mutableMapOf<String, TaskDatabase>()
        private val seedTemplateIds = setOf("tpl-standup", "tpl-break")

        fun exists(context: Context, userId: String): Boolean {
            return context.applicationContext.getDatabasePath(databaseName(userId)).exists()
        }

        fun get(context: Context, userId: String): TaskDatabase {
            val key = databaseKey(userId)
            return instances[key] ?: synchronized(this) {
                instances[key] ?: Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    databaseName(userId)
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instances[key] = it }
            }
        }

        private fun databaseName(userId: String): String = "timeboxing_${databaseKey(userId)}.db"

        private fun databaseKey(userId: String): String = userId
            .ifBlank { "unknown_user" }
            .replace(Regex("[^a-zA-Z0-9_]"), "_")
            .take(64)

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
         * Migrates guest data into the signed-in user's database.
         *
         * @return migrated item count
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task_templates ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily_tasks ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task_templates ADD COLUMN startDateIso TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_outbox (
                        queueKey TEXT NOT NULL,
                        generation TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        templateId TEXT,
                        operationType TEXT NOT NULL,
                        payload TEXT,
                        baseRemoteUpdatedAt TEXT,
                        createdAtEpochMs INTEGER NOT NULL,
                        attemptCount INTEGER NOT NULL,
                        nextAttemptAtEpochMs INTEGER NOT NULL,
                        lastError TEXT,
                        PRIMARY KEY(queueKey)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sync_outbox_nextAttemptAtEpochMs_createdAtEpochMs ON sync_outbox(nextAttemptAtEpochMs, createdAtEpochMs)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_shadows (
                        entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        remoteUpdatedAt TEXT NOT NULL,
                        remoteDeletedAt TEXT,
                        PRIMARY KEY(entityType, entityId)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_metadata (
                        `key` TEXT NOT NULL,
                        value TEXT NOT NULL,
                        PRIMARY KEY(`key`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
