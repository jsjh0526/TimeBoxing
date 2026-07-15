package dev.jsjh.timebox.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import dev.jsjh.timebox.BuildConfig

object TimeBoxAnalytics {
    const val PLACEMENT_OPENING = "opening"
    const val PLACEMENT_SETTINGS_BANNER = "settings_banner"
    const val PLACEMENT_SUPPORT_REWARDED = "support_rewarded"
    const val PLACEMENT_WIDGET_REWARDED = "widget_rewarded"

    private var analytics: FirebaseAnalytics? = null

    fun initialize(context: Context) {
        analytics = FirebaseAnalytics.getInstance(context.applicationContext)
        analytics?.setUserProperty("build_type", if (BuildConfig.DEBUG) "debug" else "release")
    }

    fun setUserContext(isGuest: Boolean, language: String) {
        analytics?.setUserProperty("auth_mode", if (isGuest) "guest" else "signed_in")
        analytics?.setUserProperty("app_language", language.ifBlank { "system" })
    }

    fun screenViewed(screenName: String) = log(
        FirebaseAnalytics.Event.SCREEN_VIEW,
        FirebaseAnalytics.Param.SCREEN_NAME to screenName,
        FirebaseAnalytics.Param.SCREEN_CLASS to "TimeBoxingApp"
    )

    fun taskCreated(source: String, hasSchedule: Boolean, isRecurring: Boolean) = log(
        "task_created",
        "source" to source,
        "has_schedule" to hasSchedule,
        "is_recurring" to isRecurring
    )

    fun taskCompleted(completed: Boolean) = log(
        if (completed) "task_completed" else "task_reopened"
    )

    fun timeboxScheduled(source: String, durationMinutes: Int) = log(
        "timebox_scheduled",
        "source" to source,
        "duration_minutes" to durationMinutes.toLong()
    )

    fun timeboxRemoved() = log("timebox_removed")

    fun tasksCarriedOver(count: Int) = log("tasks_carried_over", "count" to count.toLong())

    fun adLoadResult(placement: String, loaded: Boolean, errorCode: Int? = null) = log(
        "ad_load_result",
        "placement" to placement,
        "result" to if (loaded) "loaded" else "failed",
        "error_code" to errorCode?.toLong()
    )

    fun openingAdRequested() = log("opening_ad_requested")

    fun openingAdWindowOpened() = log("opening_ad_window_opened")

    fun openingAdShown() = log("opening_ad_shown")

    fun openingAdTimedOut() = log("opening_ad_timed_out")

    fun openingAdDismissed(source: String) = log("opening_ad_dismissed", "source" to source)

    fun rewardedAdShown(placement: String) = log("rewarded_ad_shown", "placement" to placement)

    fun rewardedAdDismissed(placement: String) = log("rewarded_ad_dismissed", "placement" to placement)

    fun rewardedAdShowFailed(placement: String, errorCode: Int) = log(
        "rewarded_ad_show_failed",
        "placement" to placement,
        "error_code" to errorCode.toLong()
    )

    fun rewardedAdEarned(placement: String) = log("rewarded_ad_earned", "placement" to placement)

    fun widgetAccessExtended() = log("widget_access_extended")

    private fun log(name: String, vararg parameters: Pair<String, Any?>) {
        val instance = analytics ?: return
        val bundle = Bundle()
        parameters.forEach { (key, value) ->
            when (value) {
                null -> Unit
                is String -> bundle.putString(key, value)
                is Long -> bundle.putLong(key, value)
                is Int -> bundle.putLong(key, value.toLong())
                is Double -> bundle.putDouble(key, value)
                is Boolean -> bundle.putLong(key, if (value) 1L else 0L)
            }
        }
        instance.logEvent(name, bundle)
    }
}
