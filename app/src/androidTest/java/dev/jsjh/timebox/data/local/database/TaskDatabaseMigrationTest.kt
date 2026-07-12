package dev.jsjh.timebox.data.local.database

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDatabaseMigrationTest {

    @Test
    fun migratesV3ToV5_withoutLosingExistingTasks() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val userId = "migration_v3_test"
        val databaseName = "timeboxing_${userId}.db"
        context.deleteDatabase(databaseName)

        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null).use { database ->
            createVersion3Tables(database)
            database.execSQL(
                """
                INSERT INTO daily_tasks (
                    id, templateId, dateIso, title, note, tagsSerialized,
                    isBig3, isCompleted, startMinute, endMinute, reminderEnabled, source
                ) VALUES (
                    'legacy-task', NULL, '2026-07-10', 'Existing task', 'kept during migration', 'work',
                    0, 0, NULL, NULL, 0, 'ONE_OFF'
                )
                """.trimIndent()
            )
            database.execSQL("PRAGMA user_version = 3")
        }

        val room = TaskDatabase.get(context, userId)
        val migratedTask = withContext(Dispatchers.IO) {
            room.dailyTaskDao().getById("2026-07-10", "legacy-task")
        }

        assertEquals("Existing task", migratedTask?.title)
        assertEquals("kept during migration", migratedTask?.note)
        assertEquals(
            0,
            withContext(Dispatchers.IO) { room.syncOutboxDao().count() }
        )
        assertNull(
            withContext(Dispatchers.IO) { room.syncShadowDao().get("TASK", "legacy-task") }
        )
        assertNull(
            withContext(Dispatchers.IO) { room.syncMetadataDao().getValue("sync_bootstrap_v1") }
        )
    }

    private fun createVersion3Tables(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE task_templates (
                id TEXT NOT NULL,
                title TEXT NOT NULL,
                note TEXT,
                tagsSerialized TEXT NOT NULL,
                recurrenceType TEXT,
                repeatDaysSerialized TEXT NOT NULL,
                startDateIso TEXT,
                defaultStartMinute INTEGER,
                defaultEndMinute INTEGER,
                reminderEnabled INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE daily_tasks (
                id TEXT NOT NULL,
                templateId TEXT,
                dateIso TEXT NOT NULL,
                title TEXT NOT NULL,
                note TEXT,
                tagsSerialized TEXT NOT NULL,
                isBig3 INTEGER NOT NULL,
                isCompleted INTEGER NOT NULL,
                startMinute INTEGER,
                endMinute INTEGER,
                reminderEnabled INTEGER NOT NULL,
                source TEXT NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
    }
}
