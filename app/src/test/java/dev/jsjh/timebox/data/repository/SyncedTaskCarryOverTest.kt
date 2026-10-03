package dev.jsjh.timebox.data.repository

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import dev.jsjh.timebox.data.local.database.TaskDatabase
import dev.jsjh.timebox.data.local.entity.DailyTaskEntity
import dev.jsjh.timebox.data.remote.RemoteSyncSnapshot
import dev.jsjh.timebox.data.remote.RemoteTask
import dev.jsjh.timebox.data.remote.SnapshotMerger
import dev.jsjh.timebox.notification.ReminderScheduler
import dev.jsjh.timebox.notification.ReminderSettings
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncedTaskCarryOverTest {
    private lateinit var database: TaskDatabase
    private lateinit var repository: SyncedTaskRepository
    private lateinit var context: Context
    private val today = LocalDate.now()
    private val dao get() = database.dailyTaskDao()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, TaskDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = SyncedTaskRepository(
            database, RoomTaskRepository(database.taskTemplateDao(), dao, anchorDate = today),
            database.taskTemplateDao(), dao, "carry-over-test-user"
        )
    }

    @After
    fun tearDown() {
        ReminderScheduler.cancelAll(context)
        database.close()
    }

    @Test
    fun moveAndLegacyCleanupQueueUpdatesAndDeletesWithTheCorrectIds() = runBlocking {
        val original = task("root", today.minusDays(5))
        val latest = task("carry-root-${today.minusDays(2)}", today.minusDays(2)).copy(source = "CARRY_OVER")
        val habit = task("habit", today.minusDays(2)).copy(source = "RECURRING", templateId = "template")
        dao.upsertAll(listOf(original, latest, habit))

        assertEquals(1, repository.carryOverPastIncompleteTasks(today, listOf(latest.id)))

        val queued = database.syncOutboxDao().getAll().associateBy { it.entityId }
        assertEquals(setOf(original.id, latest.id), queued.keys)
        assertEquals("DELETE", queued.getValue(original.id).operationType)
        val move = queued.getValue(latest.id)
        assertEquals("UPSERT", move.operationType)
        val payload = Json.decodeFromString<DailyTaskEntity>(checkNotNull(move.payload))
        assertEquals(today.toString(), payload.dateIso)
        assertNull(payload.startMinute)
        assertEquals(habit, dao.getById(habit.dateIso, habit.id))
        assertEquals(0, repository.carryOverPastIncompleteTasks(today, listOf(latest.id)))
        assertEquals(2, database.syncOutboxDao().count())
    }

    @Test
    fun staleRemoteSnapshotCannotRestoreTheOldDateOrDeletedSourceWhileUploadIsPending() = runBlocking {
        val original = task("root", today.minusDays(5))
        val latest = task("carry-root-${today.minusDays(2)}", today.minusDays(2)).copy(source = "CARRY_OVER")
        dao.upsertAll(listOf(original, latest))
        repository.carryOverPastIncompleteTasks(today, listOf(latest.id))
        val snapshot = RemoteSyncSnapshot(
            emptyMap(), listOf(original, latest).associate { row ->
                row.id to RemoteTask(
                    id = row.id, userId = "carry-over-test-user", dateIso = row.dateIso,
                    title = row.title, source = row.source, updatedAt = "2026-10-01T00:00:00Z"
                )
            }
        )

        database.withTransaction {
            SnapshotMerger(database, snapshot, discardPendingRemoteRows = false).merge()
        }

        assertNull(dao.getById(original.dateIso, original.id))
        assertNull(dao.getById(latest.dateIso, latest.id))
        assertEquals(today.toString(), dao.getByDate(today.toString()).single().dateIso)
        assertEquals(2, database.syncOutboxDao().count())
    }

    @Test
    fun outboxFailureRollsBackBothTheMoveAndLegacySourceCleanup() = runBlocking {
        val original = task("root", today.minusDays(5))
        val latest = task("carry-root-${today.minusDays(2)}", today.minusDays(2)).copy(source = "CARRY_OVER")
        dao.upsertAll(listOf(original, latest))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_outbox BEFORE INSERT ON sync_outbox " +
                "BEGIN SELECT RAISE(ABORT, 'test failure'); END"
        )

        val result = runCatching { repository.carryOverPastIncompleteTasks(today, listOf(latest.id)) }

        assertTrue(result.isFailure)
        assertEquals(listOf(original, latest), dao.getAll())
        assertTrue(dao.getByDate(today.toString()).isEmpty())
        assertEquals(0, database.syncOutboxDao().count())
    }

    @Test
    fun largeLegacyBacklogUsesBatchedDeletesOnRealSQLite() = runBlocking {
        val originals = (1..1100).map { age ->
            task("carry-root-${today.minusDays(age.toLong())}", today.minusDays(age.toLong()))
                .copy(source = "CARRY_OVER")
        }
        dao.upsertAll(originals)

        assertEquals(1, repository.carryOverPastIncompleteTasks(today, listOf(originals.first().id)))

        assertEquals(1, dao.count())
        assertEquals(1100, database.syncOutboxDao().count())
    }

    @Test
    fun reminderResyncRemovesPreviousDateKeysButPreservesUnrelatedFutureAlarms() = runBlocking {
        val old = task("old", today.minusDays(2)).copy(startMinute = 90, endMinute = 120, reminderEnabled = true)
        val future = task("future", today.plusDays(2)).copy(startMinute = 600, endMinute = 630, reminderEnabled = true)
        dao.upsertAll(listOf(old, future))
        val prefs = context.getSharedPreferences("timeboxing_scheduled_reminders", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("scheduled_keys", setOf("${old.dateIso}|${old.id}")).commit()

        repository.carryOverPastIncompleteTasks(today, listOf(old.id))
        ReminderScheduler.syncAllTasks(context, repository.getReminderCandidates(), ReminderSettings(), dayStartHour = 3)

        assertEquals(setOf("${future.dateIso}|${future.id}"), prefs.getStringSet("scheduled_keys", emptySet()))
        assertNull(dao.getByDate(today.toString()).single().startMinute)
    }

    @Test
    fun deletingAPastCarryTaskQueuesItsOwnAndOlderSourceDeletions() = runBlocking {
        val original = task("root", today.minusMonths(2))
        val latest = task("carry-root-${today.minusDays(2)}", today.minusDays(2)).copy(source = "CARRY_OVER")
        dao.upsertAll(listOf(original, latest))

        repository.deleteTask(today.minusDays(2), latest.id)

        assertEquals(0, dao.count())
        val entries = database.syncOutboxDao().getAll()
        assertEquals(setOf(original.id, latest.id), entries.map { it.entityId }.toSet())
        assertTrue(entries.all { it.operationType == "DELETE" })
    }

    private fun task(id: String, date: LocalDate) = DailyTaskEntity(
        id = id, templateId = null, dateIso = date.toString(), title = id, note = null,
        tagsSerialized = "[]", isBig3 = false, isCompleted = false, startMinute = null,
        endMinute = null, reminderEnabled = false, source = "ONE_OFF"
    )
}
