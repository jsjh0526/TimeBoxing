package dev.jsjh.timebox.feature.root

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import dev.jsjh.timebox.BuildConfig
import dev.jsjh.timebox.R
import java.time.LocalDate

data class AppAnnouncement(
    val key: String,
    val revision: Int,
    @param:StringRes val titleRes: Int,
    @param:StringRes val messageRes: Int,
    @param:StringRes val confirmRes: Int,
    val updatedInstallOnly: Boolean = true
) {
    val id: String = "${key}_v$revision"
}

object CurrentAppAnnouncement {
    // Change the copy resources and increment revision for every new announcement.
    val value = AppAnnouncement(
        key = "statistics_and_feature_survey",
        revision = 1,
        titleRes = R.string.announcement_current_title,
        messageRes = R.string.announcement_current_message,
        confirmRes = R.string.announcement_current_confirm
    )
}

object AppAnnouncementStore {
    private const val PREFS_NAME = "app_announcements"

    @Volatile
    private var shownThisLaunchId: String? = null

    fun beginLaunch() = synchronized(this) {
        shownThisLaunchId = null
    }

    fun shouldShow(context: Context, announcement: AppAnnouncement, today: LocalDate = LocalDate.now()): Boolean = synchronized(this) {
        isEligible(context, announcement, today)
    }

    fun markShownIfEligible(context: Context, announcement: AppAnnouncement, today: LocalDate = LocalDate.now()): Int? = synchronized(this) {
        if (!isEligible(context, announcement, today)) return null
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val countKey = displayCountKey(announcement)
        val nextCount = (prefs.getInt(countKey, 0).coerceAtLeast(0).toLong() + 1)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        prefs.edit()
            .putInt(countKey, nextCount)
            .putLong(lastDisplayDayKey(announcement), today.toEpochDay())
            .apply()
        shownThisLaunchId = announcement.id
        nextCount
    }

    fun suppress(context: Context, announcement: AppAnnouncement) = synchronized(this) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(suppressedKey(announcement), true).apply()
    }

    private fun isEligible(context: Context, announcement: AppAnnouncement, today: LocalDate): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val audienceEligible = BuildConfig.DEBUG ||
            !announcement.updatedInstallOnly ||
            isUpdatedInstallation(context)
        return isAppAnnouncementEligible(
            audienceEligible = audienceEligible,
            displayCount = prefs.getInt(displayCountKey(announcement), 0),
            lastDisplayDay = prefs.getLong(lastDisplayDayKey(announcement), Long.MIN_VALUE),
            today = today.toEpochDay(),
            suppressed = prefs.getBoolean(suppressedKey(announcement), false),
            shownThisLaunch = shownThisLaunchId == announcement.id
        )
    }

    private fun displayCountKey(announcement: AppAnnouncement): String =
        "${announcement.id}_display_count"

    private fun lastDisplayDayKey(announcement: AppAnnouncement): String =
        "${announcement.id}_last_display_day"

    private fun suppressedKey(announcement: AppAnnouncement): String =
        "${announcement.id}_suppressed"

    @Suppress("DEPRECATION")
    private fun isUpdatedInstallation(context: Context): Boolean = runCatching {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        packageInfo.lastUpdateTime > packageInfo.firstInstallTime
    }.getOrDefault(false)
}

internal fun isAppAnnouncementEligible(
    audienceEligible: Boolean,
    displayCount: Int,
    lastDisplayDay: Long,
    today: Long,
    suppressed: Boolean,
    shownThisLaunch: Boolean
): Boolean =
    audienceEligible &&
        !suppressed &&
        !shownThisLaunch &&
        (displayCount < 3 || lastDisplayDay != today)
