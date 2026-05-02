package dev.jsjh.timebox.notification

import android.content.Context

data class ReminderSettings(
    val notificationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true
)

class ReminderSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): ReminderSettings = ReminderSettings(
        notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true),
        soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, true),
        vibrationEnabled = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
    )

    fun write(settings: ReminderSettings) {
        prefs.edit()
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, settings.notificationsEnabled)
            .putBoolean(KEY_SOUND_ENABLED, settings.soundEnabled)
            .putBoolean(KEY_VIBRATION_ENABLED, settings.vibrationEnabled)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "timeboxing_reminder_settings"
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_VIBRATION_ENABLED = "vibration_enabled"
    }
}
