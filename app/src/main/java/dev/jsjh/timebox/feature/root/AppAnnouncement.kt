package dev.jsjh.timebox.feature.root

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import dev.jsjh.timebox.BuildConfig
import dev.jsjh.timebox.R

data class AppAnnouncement(
    val key: String,
    val revision: Int,
    @param:StringRes val titleRes: Int,
    @param:StringRes val messageRes: Int,
    @param:StringRes val confirmRes: Int,
    val maxDisplayCount: Int = 3,
    val updatedInstallOnly: Boolean = true
) {
    val id: String = "${key}_v$revision"
}

object CurrentAppAnnouncement {
    // Change the copy resources and increment revision for every new announcement.
    val value = AppAnnouncement(
        key = "language_expansion",
        revision = 3,
        titleRes = R.string.announcement_current_title,
        messageRes = R.string.announcement_current_message,
        confirmRes = R.string.announcement_current_confirm
    )
}

object AppAnnouncementStore {
    private const val PREFS_NAME = "app_announcements"

    @Volatile
    private var shownThisProcessId: String? = null

    fun shouldShow(context: Context, announcement: AppAnnouncement): Boolean = synchronized(this) {
        isEligible(context, announcement)
    }

    fun markShownIfEligible(context: Context, announcement: AppAnnouncement): Boolean = synchronized(this) {
        if (!isEligible(context, announcement)) return false
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val countKey = displayCountKey(announcement)
        val nextCount = prefs.getInt(countKey, 0) + 1
        prefs.edit().putInt(countKey, nextCount).apply()
        shownThisProcessId = announcement.id
        true
    }

    private fun isEligible(context: Context, announcement: AppAnnouncement): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val audienceEligible = BuildConfig.DEBUG ||
            !announcement.updatedInstallOnly ||
            isUpdatedInstallation(context)
        return isAppAnnouncementEligible(
            audienceEligible = audienceEligible,
            displayCount = prefs.getInt(displayCountKey(announcement), 0),
            maxDisplayCount = announcement.maxDisplayCount,
            shownThisProcess = shownThisProcessId == announcement.id
        )
    }

    private fun displayCountKey(announcement: AppAnnouncement): String =
        "${announcement.id}_display_count"

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
    maxDisplayCount: Int,
    shownThisProcess: Boolean
): Boolean =
    audienceEligible &&
        !shownThisProcess &&
        displayCount < maxDisplayCount
