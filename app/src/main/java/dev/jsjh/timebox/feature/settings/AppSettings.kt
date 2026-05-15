package dev.jsjh.timebox.feature.settings

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime

data class AppSettings(
    val showSystemNavigationBar: Boolean = true,
    val dayStartHour: Int = 0
)

class AppSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): AppSettings = AppSettings(
        showSystemNavigationBar = prefs.getBoolean(KEY_SHOW_SYSTEM_NAVIGATION_BAR, true),
        dayStartHour = prefs.getInt(KEY_DAY_START_HOUR, 0).coerceIn(0, 6)
    )

    fun write(settings: AppSettings) {
        prefs.edit()
            .putBoolean(KEY_SHOW_SYSTEM_NAVIGATION_BAR, settings.showSystemNavigationBar)
            .putInt(KEY_DAY_START_HOUR, settings.dayStartHour.coerceIn(0, 6))
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "timebox_app_settings"
        const val KEY_SHOW_SYSTEM_NAVIGATION_BAR = "show_system_navigation_bar"
        const val KEY_DAY_START_HOUR = "day_start_hour"
    }
}

fun effectiveToday(dayStartHour: Int, now: LocalDateTime = LocalDateTime.now()): LocalDate {
    val safeHour = dayStartHour.coerceIn(0, 6)
    return if (safeHour > 0 && now.toLocalTime().hour < safeHour) {
        now.toLocalDate().minusDays(1)
    } else {
        now.toLocalDate()
    }
}
