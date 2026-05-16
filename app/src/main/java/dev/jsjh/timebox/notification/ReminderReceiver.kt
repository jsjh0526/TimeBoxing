package dev.jsjh.timebox.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.jsjh.timebox.MainActivity
import dev.jsjh.timebox.R
import dev.jsjh.timebox.auth.ActiveUserStore
import dev.jsjh.timebox.auth.initSupabase
import dev.jsjh.timebox.auth.supabase
import dev.jsjh.timebox.data.local.database.TaskDatabase
import dev.jsjh.timebox.data.remote.SupabaseSync
import dev.jsjh.timebox.data.repository.RoomTaskRepository
import io.github.jan.supabase.auth.auth
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ReminderScheduler.ACTION_SHOW_REMINDER -> showReminder(context, intent)
            ReminderScheduler.ACTION_COMPLETE_TASK -> completeTask(context, intent)
        }
    }

    private fun showReminder(context: Context, intent: Intent) {
        val settings = ReminderSettingsStore(context).read()
        if (!settings.notificationsEnabled) return
        if (!hasNotificationPermission(context)) return

        ReminderScheduler.createChannels(context)

        val key = intent.getStringExtra(ReminderScheduler.EXTRA_KEY).orEmpty()
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()
        val timeRange = intent.getStringExtra(ReminderScheduler.EXTRA_TIME_RANGE).orEmpty()
        val completeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderScheduler.ACTION_COMPLETE_TASK
            putExtra(ReminderScheduler.EXTRA_KEY, key)
            putExtra(ReminderScheduler.EXTRA_DATE, intent.getStringExtra(ReminderScheduler.EXTRA_DATE).orEmpty())
            putExtra(ReminderScheduler.EXTRA_TASK_ID, intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID).orEmpty())
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            "$key|complete".hashCode(),
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentIntent = PendingIntent.getActivity(
            context,
            key.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderScheduler.channelId(settings))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "TimeBoxing" })
            .setContentText(timeRange)
            .setStyle(NotificationCompat.BigTextStyle().bigText(timeRange))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_notification, "완료", completePendingIntent)
            .setSilent(!settings.soundEnabled && !settings.vibrationEnabled)
            .build()

        showNotification(context, key.hashCode(), notification)
    }

    private fun completeTask(context: Context, intent: Intent) {
        val key = intent.getStringExtra(ReminderScheduler.EXTRA_KEY).orEmpty()
        if (key.isNotBlank()) {
            NotificationManagerCompat.from(context.applicationContext).cancel(key.hashCode())
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID).orEmpty()
                val date = intent.getStringExtra(ReminderScheduler.EXTRA_DATE)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

                if (key.isNotBlank() && taskId.isNotBlank() && date != null) {
                    val appContext = context.applicationContext
                    val userId = ActiveUserStore.readUserId(appContext)
                    val database = TaskDatabase.get(appContext, userId)
                    val roomRepository = RoomTaskRepository(
                        templateDao = database.taskTemplateDao(),
                        dailyTaskDao = database.dailyTaskDao()
                    )
                    roomRepository.markCompleted(date, taskId)
                    if (userId != "guest") {
                        initSupabase(appContext)
                        val sessionReady = runCatching {
                            supabase.auth.awaitInitialization()
                            supabase.auth.loadFromStorage()
                        }.getOrDefault(false)
                        if (sessionReady) {
                            database.dailyTaskDao().getById(date.toString(), taskId)?.let { entity ->
                                runCatching { SupabaseSync.pushTask(userId, entity) }
                            }
                        }
                    }
                    ReminderRefreshBus.notifyTaskChanged()
                }
            } finally {
                if (key.isNotBlank()) {
                    NotificationManagerCompat.from(context.applicationContext).cancel(key.hashCode())
                }
                pendingResult.finish()
            }
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(context: Context, id: Int, notification: android.app.Notification) {
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}

