package dev.jsjh.timebox.notification

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.content.edit
import dev.jsjh.timebox.R
import dev.jsjh.timebox.domain.model.DailyTask
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.zip.CRC32

object ReminderScheduler {
    const val ACTION_SHOW_REMINDER = "dev.jsjh.timebox.notification.SHOW_REMINDER"
    const val ACTION_COMPLETE_TASK = "dev.jsjh.timebox.notification.COMPLETE_TASK"
    const val EXTRA_KEY = "extra_key"
    const val EXTRA_DATE = "extra_date"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_TIME_RANGE = "extra_time_range"
    const val EXTRA_TASK_ID = "extra_task_id"

    private const val SCHEDULED_PREFS = "timeboxing_scheduled_reminders"
    private const val KEY_SCHEDULED = "scheduled_keys"

    private const val CHANNEL_SOUND_VIBRATION = "reminders_sound_vibration"
    private const val CHANNEL_SOUND = "reminders_sound"
    private const val CHANNEL_VIBRATION = "reminders_vibration"
    private const val CHANNEL_SILENT = "reminders_silent"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channels = listOf(
            channel(context, CHANNEL_SOUND_VIBRATION, context.getString(R.string.notification_channel_reminders), soundUri, audioAttributes, vibration = true),
            channel(context, CHANNEL_SOUND, context.getString(R.string.notification_channel_reminders_sound), soundUri, audioAttributes, vibration = false),
            channel(context, CHANNEL_VIBRATION, context.getString(R.string.notification_channel_reminders_vibration), sound = null, audioAttributes = null, vibration = true),
            channel(context, CHANNEL_SILENT, context.getString(R.string.notification_channel_reminders_silent), sound = null, audioAttributes = null, vibration = false)
        )
        manager.createNotificationChannels(channels)
    }

    fun channelId(settings: ReminderSettings): String = when {
        settings.soundEnabled && settings.vibrationEnabled -> CHANNEL_SOUND_VIBRATION
        settings.soundEnabled -> CHANNEL_SOUND
        settings.vibrationEnabled -> CHANNEL_VIBRATION
        else -> CHANNEL_SILENT
    }

    fun syncTasks(context: Context, date: LocalDate, tasks: List<DailyTask>, settings: ReminderSettings) {
        createChannels(context)
        val prefs = scheduledPrefs(context)
        val existing = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        val datePrefix = "$date|"
        val desired = tasks
            .filter { shouldSchedule(it, settings) }
            .map { reminderKey(it.date, it.id) }
            .toSet()

        existing
            .filter { it.startsWith(datePrefix) && it !in desired }
            .forEach { cancelKey(context, it) }

        tasks.forEach { task ->
            if (shouldSchedule(task, settings)) {
                schedule(context, task)
            } else {
                cancelKey(context, reminderKey(task.date, task.id))
            }
        }

        val next = existing.filterNot { it.startsWith(datePrefix) }.toMutableSet()
        next.addAll(desired)
        prefs.edit {
            putStringSet(KEY_SCHEDULED, next)
        }
    }

    fun cancelAll(context: Context) {
        val prefs = scheduledPrefs(context)
        val existing = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        existing.forEach { cancelKey(context, it) }
        prefs.edit {
            remove(KEY_SCHEDULED)
        }
    }

    fun removeScheduledKey(context: Context, date: LocalDate, taskId: String) {
        val key = reminderKey(date, taskId)
        val prefs = scheduledPrefs(context)
        val updated = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().toMutableSet()
        if (updated.remove(key)) {
            prefs.edit {
                putStringSet(KEY_SCHEDULED, updated)
            }
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun schedule(context: Context, task: DailyTask) {
        val schedule = task.schedule ?: return
        val triggerAtMillis = triggerAtMillis(task) ?: return

        val key = reminderKey(task.date, task.id)
        cancelKey(context, key)
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SHOW_REMINDER
            putExtra(EXTRA_KEY, key)
            putExtra(EXTRA_DATE, task.date.toString())
            putExtra(EXTRA_TITLE, task.title)
            putExtra(EXTRA_TIME_RANGE, "${formatMinute(schedule.startMinute)} - ${formatMinute(schedule.endMinute)}")
            putExtra(EXTRA_TASK_ID, task.id)
        }
        val pendingIntent = reminderPendingIntent(context, key, intent) ?: return
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun shouldSchedule(task: DailyTask, settings: ReminderSettings): Boolean {
        val schedule = task.schedule ?: return false
        return settings.notificationsEnabled &&
            schedule.reminderEnabled &&
            !task.isCompleted &&
            (triggerAtMillis(task) ?: 0L) > System.currentTimeMillis()
    }

    private fun triggerAtMillis(task: DailyTask): Long? {
        val schedule = task.schedule ?: return null
        return task.date
            .atStartOfDay(ZoneId.systemDefault())
            .plusMinutes(schedule.startMinute.toLong())
            .toInstant()
            .toEpochMilli()
    }

    private fun cancelKey(context: Context, key: String) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SHOW_REMINDER
        }
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        listOf(reminderRequestCode(key), legacyReminderRequestCode(key)).distinct().forEach { requestCode ->
            val pendingIntent = reminderPendingIntent(context, requestCode, intent, PendingIntent.FLAG_NO_CREATE)
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun reminderPendingIntent(
        context: Context,
        key: String,
        intent: Intent
    ): PendingIntent? {
        return reminderPendingIntent(context, reminderRequestCode(key), intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun reminderPendingIntent(
        context: Context,
        requestCode: Int,
        intent: Intent,
        mutableFlag: Int
    ): PendingIntent? {
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            mutableFlag or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun channel(
        context: Context,
        id: String,
        name: String,
        sound: android.net.Uri?,
        audioAttributes: AudioAttributes?,
        vibration: Boolean
    ): NotificationChannel {
        return NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.notification_channel_description)
            setSound(sound, audioAttributes)
            enableVibration(vibration)
            if (vibration) vibrationPattern = longArrayOf(0, 180, 80, 180)
        }
    }

    private fun scheduledPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(SCHEDULED_PREFS, Context.MODE_PRIVATE)

    private fun reminderKey(date: LocalDate, taskId: String): String = "$date|$taskId"

    private fun reminderRequestCode(key: String): Int {
        val crc = CRC32()
        crc.update(key.toByteArray(Charsets.UTF_8))
        return (crc.value and 0x7fffffff).toInt()
    }

    private fun legacyReminderRequestCode(key: String): Int = key.hashCode()

    private fun formatMinute(totalMinutes: Int): String {
        val h = (totalMinutes / 60).coerceIn(0, 23)
        val m = (totalMinutes % 60).coerceIn(0, 59)
        return String.format(Locale.ENGLISH, "%02d:%02d", h, m)
    }
}
