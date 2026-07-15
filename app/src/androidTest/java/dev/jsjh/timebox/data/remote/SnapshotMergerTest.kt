package dev.jsjh.timebox.data.remote

import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jsjh.timebox.data.local.database.TaskDatabase
import dev.jsjh.timebox.data.local.entity.DailyTaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnapshotMergerTest {

    @Test
    fun merge_preservesPendingLocalEditWhileApplyingOtherRemoteRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val userId = "pending_merge_test"
        context.deleteDatabase("timeboxing_${userId}.db")
        val database = TaskDatabase.get(context, userId)

        val pendingLocal = DailyTaskEntity(
            id = "task-x",
            templateId = null,
            dateIso = "2026-07-12",
            title = "Local completion",
            note = null,
            tagsSerialized = "",
            isBig3 = false,
            isCompleted = true,
            startMinute = null,
            endMinute = null,
            reminderEnabled = false,
            source = "ONE_OFF"
        )
        val remoteOther = RemoteTask(
            id = "task-y",
            userId = userId,
            dateIso = "2026-07-12",
            title = "Other device change",
            isCompleted = true,
            source = "ONE_OFF",
            updatedAt = "2026-07-12T00:00:00Z"
        )

        withContext(Dispatchers.IO) {
            database.dailyTaskDao().upsert(pendingLocal)
            SyncOutboxProcessor(
                userId = userId,
                templateDao = database.taskTemplateDao(),
                dailyTaskDao = database.dailyTaskDao(),
                outboxDao = database.syncOutboxDao(),
                shadowDao = database.syncShadowDao()
            ).queueTaskUpsert(pendingLocal)

            database.withTransaction {
                SnapshotMerger(
                    database = database,
                    snapshot = RemoteSyncSnapshot(
                        templatesById = emptyMap(),
                        tasksById = mapOf(
                            pendingLocal.id to RemoteTask(
                                id = pendingLocal.id,
                                userId = userId,
                                dateIso = pendingLocal.dateIso,
                                title = pendingLocal.title,
                                isCompleted = false,
                                source = pendingLocal.source,
                                updatedAt = "2026-07-11T00:00:00Z"
                            ),
                            remoteOther.id to remoteOther
                        )
                    ),
                    discardPendingRemoteRows = false
                ).merge()
            }
        }

        val preserved = withContext(Dispatchers.IO) {
            database.dailyTaskDao().getById(pendingLocal.dateIso, pendingLocal.id)
        }
        val mergedOther = withContext(Dispatchers.IO) {
            database.dailyTaskDao().getById(remoteOther.dateIso, remoteOther.id)
        }

        assertEquals(true, preserved?.isCompleted)
        assertEquals(1, withContext(Dispatchers.IO) { database.syncOutboxDao().count() })
        assertNotNull(mergedOther)
        assertEquals(true, mergedOther?.isCompleted)
    }

    @Test
    fun merge_doesNotResurrectActiveChildOfDeletedTemplate() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val userId = "deleted_template_child_test"
        val templateId = "habit-template"
        val childId = "habit-child-from-other-device"
        context.deleteDatabase("timeboxing_${userId}.db")
        val database = TaskDatabase.get(context, userId)

        val localGhost = DailyTaskEntity(
            id = childId,
            templateId = templateId,
            dateIso = "2026-07-20",
            title = "Should stay deleted",
            note = null,
            tagsSerialized = "",
            isBig3 = false,
            isCompleted = false,
            startMinute = null,
            endMinute = null,
            reminderEnabled = false,
            source = "RECURRING"
        )

        withContext(Dispatchers.IO) {
            database.dailyTaskDao().upsert(localGhost)
            database.withTransaction {
                SnapshotMerger(
                    database = database,
                    snapshot = RemoteSyncSnapshot(
                        templatesById = mapOf(
                            templateId to RemoteTemplate(
                                id = templateId,
                                userId = userId,
                                title = "Deleted habit",
                                updatedAt = "2026-07-20T00:00:00Z",
                                deletedAt = "2026-07-20T00:00:00Z"
                            )
                        ),
                        tasksById = mapOf(
                            childId to RemoteTask(
                                id = childId,
                                userId = userId,
                                templateId = templateId,
                                dateIso = localGhost.dateIso,
                                title = localGhost.title,
                                isCompleted = false,
                                source = localGhost.source,
                                updatedAt = "2026-07-19T00:00:00Z",
                                deletedAt = null
                            )
                        )
                    ),
                    discardPendingRemoteRows = false
                ).merge()
            }
        }

        assertNull(
            withContext(Dispatchers.IO) {
                database.dailyTaskDao().getById(localGhost.dateIso, childId)
            }
        )
    }
}
