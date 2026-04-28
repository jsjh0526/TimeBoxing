package com.example.timeboxing.notification

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
import com.example.timeboxing.MainActivity
import com.example.timeboxing.R

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_SHOW_REMINDER) return

        val settings = ReminderSettingsStore(context).read()
        if (!settings.notificationsEnabled) return
        if (!hasNotificationPermission(context)) return

        ReminderScheduler.createChannels(context)

        val key = intent.getStringExtra(ReminderScheduler.EXTRA_KEY).orEmpty()
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()
        val timeRange = intent.getStringExtra(ReminderScheduler.EXTRA_TIME_RANGE).orEmpty()
        val contentIntent = PendingIntent.getActivity(
            context,
            key.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderScheduler.channelId(settings))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time block starts now")
            .setContentText(if (timeRange.isBlank()) title else "$title · $timeRange")
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOf(title, timeRange).filter { it.isNotBlank() }.joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setSilent(!settings.soundEnabled && !settings.vibrationEnabled)
            .build()

        showNotification(context, key.hashCode(), notification)
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
