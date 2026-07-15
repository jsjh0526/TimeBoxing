package dev.jsjh.timebox.ads

import android.content.Context
import java.time.LocalDate

object OpeningNativeAdGate {
    private const val PREFS_NAME = "opening_native_ad_state"
    private const val KEY_LAUNCH_COUNT = "launch_count"
    private const val KEY_LAST_DISPLAY_DAY = "last_display_day"
    private const val KEY_DISPLAY_COUNT_FOR_DAY = "display_count_for_day"
    private const val MIN_LAUNCH_COUNT = 2
    private const val MAX_DAILY_DISPLAYS = 10

    private var eligibleForCurrentLaunch = false
    private var consumedForCurrentLaunch = false

    fun recordLaunch(context: Context, fromWidget: Boolean) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val launchCount = prefs.getInt(KEY_LAUNCH_COUNT, 0) + 1
        prefs.edit().putInt(KEY_LAUNCH_COUNT, launchCount).apply()

        eligibleForCurrentLaunch = !fromWidget &&
            launchCount >= MIN_LAUNCH_COUNT &&
            canDisplayToday(prefs)
        consumedForCurrentLaunch = false
    }

    fun disableForCurrentLaunch() {
        eligibleForCurrentLaunch = false
    }

    fun canPreloadForCurrentLaunch(): Boolean =
        eligibleForCurrentLaunch && !consumedForCurrentLaunch

    fun consumeEligibility(): Boolean {
        if (!eligibleForCurrentLaunch || consumedForCurrentLaunch) return false
        consumedForCurrentLaunch = true
        return true
    }

    fun markShown(context: Context) {
        val today = todayKey()
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCount = if (prefs.getLong(KEY_LAST_DISPLAY_DAY, Long.MIN_VALUE) == today) {
            prefs.getInt(KEY_DISPLAY_COUNT_FOR_DAY, 0)
        } else {
            0
        }
        prefs.edit()
            .putLong(KEY_LAST_DISPLAY_DAY, today)
            .putInt(KEY_DISPLAY_COUNT_FOR_DAY, currentCount + 1)
            .apply()
    }

    private fun canDisplayToday(prefs: android.content.SharedPreferences): Boolean {
        val today = todayKey()
        val todayCount = if (prefs.getLong(KEY_LAST_DISPLAY_DAY, Long.MIN_VALUE) == today) {
            prefs.getInt(KEY_DISPLAY_COUNT_FOR_DAY, 0)
        } else {
            0
        }
        return todayCount < MAX_DAILY_DISPLAYS
    }

    private fun todayKey(): Long = LocalDate.now().toEpochDay()
}
