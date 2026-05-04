package dev.jsjh.timebox.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.jsjh.timebox.data.local.database.TaskDatabase
import dev.jsjh.timebox.auth.ActiveUserStore
import dev.jsjh.timebox.data.repository.RoomTaskRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 湲곌린 ?щ?????AlarmManager ?뚮엺???щ벑濡?
 * Android???щ?????紐⑤뱺 ?뚮엺??吏?곌린 ?뚮Ц???꾩닔.
 */
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
                val today    = LocalDate.now()
                val tomorrow = today.plusDays(1)

                listOf(today, tomorrow).forEach { date ->
                    val tasks = repository.getTasks(date)
                    ReminderScheduler.syncTasks(context, date, tasks, settings)
                }
            } catch (_: Exception) {
                // ?щ???吏곹썑??DB 濡쒕뱶 ?ㅽ뙣?????덉쓬 ??議곗슜??臾댁떆
            }
        }
    }
}
