package dev.jsjh.timebox.feature.settings

import android.content.Context
import dev.jsjh.timebox.domain.model.StatisticsWeekStart
import java.time.LocalDate
import java.time.LocalDateTime

data class AppSettings(
    val showSystemNavigationBar: Boolean = true,
    val dayStartHour: Int = 0,
    val showStatistics: Boolean = true,
    val statisticsWeekStart: StatisticsWeekStart = StatisticsWeekStart.SUNDAY
)

class AppSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): AppSettings = AppSettings(
        showSystemNavigationBar = prefs.getBoolean(KEY_SHOW_SYSTEM_NAVIGATION_BAR, true),
        dayStartHour = prefs.getInt(KEY_DAY_START_HOUR, 0).coerceIn(0, 6),
        showStatistics = prefs.getBoolean(KEY_SHOW_STATISTICS, true),
        statisticsWeekStart = runCatching {
            StatisticsWeekStart.valueOf(prefs.getString(KEY_STATISTICS_WEEK_START, null).orEmpty())
        }.getOrDefault(StatisticsWeekStart.SUNDAY)
    )

    fun write(settings: AppSettings) {
        prefs.edit()
            .putBoolean(KEY_SHOW_SYSTEM_NAVIGATION_BAR, settings.showSystemNavigationBar)
            .putInt(KEY_DAY_START_HOUR, settings.dayStartHour.coerceIn(0, 6))
            .putBoolean(KEY_SHOW_STATISTICS, settings.showStatistics)
            .putString(KEY_STATISTICS_WEEK_START, settings.statisticsWeekStart.name)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "timebox_app_settings"
        const val KEY_SHOW_SYSTEM_NAVIGATION_BAR = "show_system_navigation_bar"
        const val KEY_DAY_START_HOUR = "day_start_hour"
        const val KEY_SHOW_STATISTICS = "show_statistics"
        const val KEY_STATISTICS_WEEK_START = "statistics_week_start"
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
