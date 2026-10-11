package dev.jsjh.timebox.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jsjh.timebox.data.repository.FakeDailyTaskDao
import dev.jsjh.timebox.data.repository.FakeTaskTemplateDao
import dev.jsjh.timebox.data.repository.RoomTaskRepository
import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.model.StatisticsWeekStart
import dev.jsjh.timebox.domain.repository.TaskRepository
import dev.jsjh.timebox.feature.root.AppTab
import dev.jsjh.timebox.feature.root.TimeBoxingAppState
import dev.jsjh.timebox.feature.root.visibleAppTabs
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatisticsSettingsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun defaultsAndPersistedChoicesDoNotChangeDayStartOrNavigationPreference() {
        val prefs = context.getSharedPreferences("timebox_app_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = AppSettingsStore(context)
        assertTrue(store.read().showStatistics)
        assertEquals(StatisticsWeekStart.SUNDAY, store.read().statisticsWeekStart)
        val settings = AppSettings(false, 3, false, StatisticsWeekStart.MONDAY)
        store.write(settings)
        assertEquals(settings, AppSettingsStore(context).read())
        prefs.edit().putString("statistics_week_start", "INVALID").commit()
        assertEquals(StatisticsWeekStart.SUNDAY, store.read().statisticsWeekStart)
        assertEquals(3, store.read().dayStartHour)
        assertFalse(store.read().showStatistics)
    }

    @Test fun tabsHaveTheApprovedOrderAndHidingStatisticsLeavesExistingTabs() {
        assertEquals(listOf(AppTab.HOME, AppTab.TODO, AppTab.TIMETABLE, AppTab.STATS, AppTab.SETTINGS), visibleAppTabs(true))
        assertEquals(listOf(AppTab.HOME, AppTab.TODO, AppTab.TIMETABLE, AppTab.SETTINGS), visibleAppTabs(false))
    }

    @Test fun selectingStatisticsDoesNotTriggerGeneralTaskReadsAndHiddenActiveTabReturnsHome() = runBlocking {
        val room = RoomTaskRepository(FakeTaskTemplateDao(), FakeDailyTaskDao())
        var reads = 0
        val repository = object : TaskRepository by room {
            override suspend fun getTasks(date: LocalDate): List<DailyTask> {
                reads++
                return room.getTasks(date)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = TimeBoxingAppState(repository, LocalDate.of(2026, 10, 7), scope)
            scope.coroutineContext[Job]!!.children.toList().joinAll()
            val initialReads = reads
            state.selectTab(AppTab.STATS)
            scope.coroutineContext[Job]!!.children.toList().joinAll()
            assertEquals(AppTab.STATS, state.currentTab)
            assertEquals(initialReads, reads)
            state.ensureStatisticsVisibility(true)
            assertEquals(AppTab.STATS, state.currentTab)
            state.ensureStatisticsVisibility(false)
            assertEquals(AppTab.HOME, state.currentTab)
            scope.coroutineContext[Job]!!.children.toList().joinAll()
        } finally { scope.cancel() }
    }
}
