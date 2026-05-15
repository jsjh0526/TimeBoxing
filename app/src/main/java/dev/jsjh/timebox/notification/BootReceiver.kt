package dev.jsjh.timebox.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.jsjh.timebox.data.local.database.TaskDatabase
import dev.jsjh.timebox.auth.ActiveUserStore
import dev.jsjh.timebox.data.repository.RoomTaskRepository
import dev.jsjh.timebox.feature.settings.AppSettingsStore
import dev.jsjh.timebox.feature.settings.effectiveToday
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in listOf(
                Intent.ACTION_BOOT_COMPLETED,
                "android.intent.action.QUICKBOOT_POWERON",
                "com.htc.intent.action.QUICKBOOT_POWERON"
            )
        ) return

        val settings = ReminderSettingsStore(context).read()
        if (!settings.notificationsEnabled) return
        val appSettings = AppSettingsStore(context).read()

        ReminderScheduler.createChannels(context)

        val userId = ActiveUserStore.readUserId(context)
        val scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                val database     = TaskDatabase.get(context, userId)
                val repository   = RoomTaskRepository(
                    templateDao  = database.taskTemplateDao(),
                    dailyTaskDao = database.dailyTaskDao(),
                    seedInitialData = false
                )
                val today    = effectiveToday(appSettings.dayStartHour)
                val tomorrow = today.plusDays(1)

                listOf(today, tomorrow).forEach { date ->
                    val tasks = repository.getTasks(date)
                    ReminderScheduler.syncTasks(context, date, tasks, settings)
                }
            } catch (_: Exception) {
                // Ignore boot-time database load failures quietly.
            }
        }
    }
}
