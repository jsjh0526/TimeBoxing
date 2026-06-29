package dev.jsjh.timebox.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.jsjh.timebox.data.local.database.TaskDatabase
import dev.jsjh.timebox.auth.ActiveUserStore
import dev.jsjh.timebox.data.repository.RoomTaskRepository
import dev.jsjh.timebox.feature.settings.AppSettingsStore
import dev.jsjh.timebox.feature.settings.effectiveToday
import dev.jsjh.timebox.widget.TodoWidgetUpdater
import dev.jsjh.timebox.widget.WidgetAccessStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in listOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                "android.intent.action.QUICKBOOT_POWERON",
                "com.htc.intent.action.QUICKBOOT_POWERON"
            )
        ) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                WidgetAccessStore.scheduleExpiryRefresh(appContext)
                TodoWidgetUpdater.updateNow(appContext)

                val settings = ReminderSettingsStore(appContext).read()
                if (settings.notificationsEnabled) {
                    val appSettings = AppSettingsStore(appContext).read()
                    ReminderScheduler.createChannels(appContext)

                    val userId = ActiveUserStore.readUserId(appContext)
                    val database = TaskDatabase.get(appContext, userId)
                    val repository = RoomTaskRepository(
                        templateDao = database.taskTemplateDao(),
                        dailyTaskDao = database.dailyTaskDao(),
                        seedInitialData = false
                    )
                    val today = effectiveToday(appSettings.dayStartHour)
                    val tomorrow = today.plusDays(1)

                    listOf(today, tomorrow).forEach { date ->
                        val tasks = repository.getTasks(date)
                        ReminderScheduler.syncTasks(appContext, date, tasks, settings, appSettings.dayStartHour)
                    }
                }
            } catch (_: Exception) {
                // Ignore boot-time database load failures quietly.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
