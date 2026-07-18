package dev.jsjh.timebox.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import dev.jsjh.timebox.BuildConfig
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object TimeBoxAnalytics {
    const val PLACEMENT_OPENING = "opening"
    const val PLACEMENT_SETTINGS_BANNER = "settings_banner"
    const val PLACEMENT_SUPPORT_REWARDED = "support_rewarded"
    const val PLACEMENT_WIDGET_REWARDED = "widget_rewarded"

    private const val MILESTONE_PREFERENCES = "analytics_milestones"
    private const val MEANINGFUL_ACTIVE_DAY_KEY = "meaningful_active_day_last_date"

    private var analytics: FirebaseAnalytics? = null
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
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

    fun taskCreated(source: String, hasSchedule: Boolean, isRecurring: Boolean) {
        log(
            "task_created",
            "source" to source,
            "has_schedule" to hasSchedule,
            "is_recurring" to isRecurring
        )
        logOnce("first_task_created")
        meaningfulActiveDay("task_created")
    }

    fun taskCompleted(
        completed: Boolean,
        source: String,
        isBig3: Boolean,
        isTutorial: Boolean
    ) {
        log(
            if (completed) "task_completed" else "task_reopened",
            "source" to source,
            "is_big3" to isBig3,
            "is_tutorial" to isTutorial
        )
        if (completed) {
            meaningfulActiveDay("task_completed")
            if (isTutorial) {
                log("tutorial_task_completed", "source" to source)
            }
        }
    }

    fun timeboxScheduled(source: String, durationMinutes: Int) {
        log(
            "timebox_scheduled",
            "source" to source,
            "duration_minutes" to durationMinutes.toLong()
        )
        logOnce("first_timebox_scheduled")
        meaningfulActiveDay("timebox_scheduled")
    }

    fun timeboxRemoved() = log("timebox_removed")

    fun tasksCarriedOver(count: Int) {
        log("tasks_carried_over", "count" to count.toLong())
        meaningfulActiveDay("tasks_carried_over")
    }

    fun big3Changed(enabled: Boolean, source: String, isTutorial: Boolean) {
        log(
            "big3_changed",
            "enabled" to enabled,
            "source" to source,
            "is_tutorial" to isTutorial
        )
        meaningfulActiveDay("big3_changed")
    }

    fun reminderChanged(enabled: Boolean, source: String) = log(
        "reminder_changed",
        "enabled" to enabled,
        "source" to source
    )

    fun notificationSettingsChanged(enabled: Boolean) = log(
        "notification_settings_changed",
        "enabled" to enabled
    )

    fun notificationPermissionResult(granted: Boolean) = log(
        "notification_permission_result",
        "result" to if (granted) "granted" else "denied"
    )

    fun notificationShown() = log("notification_shown")

    fun notificationOpened() = log("notification_opened")

    fun notificationTaskCompleted() {
        log("notification_task_completed")
        meaningfulActiveDay("notification_task_completed")
    }

    fun widgetAdded() = log("widget_added")

    fun widgetRemoved() = log("widget_removed")

    fun widgetOpened(destination: String) = log(
        "widget_opened",
        "destination" to destination
    )

    fun widgetAccessExpired() = log("widget_access_expired")

    fun adLoadResult(placement: String, loaded: Boolean, errorCode: Int? = null) = log(
        "ad_load_result",
        "placement" to placement,
        "result" to if (loaded) "loaded" else "failed",
        "error_code" to errorCode?.toLong()
    )

    fun adImpressionRecorded(placement: String, adFormat: String) = log(
        "ad_impression_recorded",
        "placement" to placement,
        "ad_format" to adFormat
    )

    fun adClicked(placement: String, adFormat: String) = log(
        "ad_clicked",
        "placement" to placement,
        "ad_format" to adFormat
    )

    fun adRevenuePaid(
        placement: String,
        adFormat: String,
        valueMicros: Long,
        currencyCode: String,
        precisionType: Int
    ) = log(
        "ad_revenue_paid",
        "placement" to placement,
        "ad_format" to adFormat,
        FirebaseAnalytics.Param.VALUE to valueMicros / 1_000_000.0,
        FirebaseAnalytics.Param.CURRENCY to currencyCode,
        "precision_type" to precisionType.toLong()
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

    fun languageChanged(fromLanguage: String, toLanguage: String) = log(
        "language_changed",
        "from_language" to fromLanguage,
        "to_language" to toLanguage
    )

    fun tutorialSeeded(language: String) = log(
        "tutorial_seeded",
        "language" to language
    )

    fun announcementShown(announcementId: String) = log(
        "announcement_shown",
        "announcement_id" to announcementId
    )

    fun announcementDismissed(announcementId: String, source: String) = log(
        "announcement_dismissed",
        "announcement_id" to announcementId,
        "source" to source
    )

    private fun logOnce(eventName: String) {
        val context = applicationContext ?: return
        if (analytics == null) return
        val key = "${eventName}_v1_${if (BuildConfig.DEBUG) "debug" else "release"}"
        synchronized(this) {
            val preferences = context.getSharedPreferences(MILESTONE_PREFERENCES, Context.MODE_PRIVATE)
            if (preferences.getBoolean(key, false)) return
            log(eventName)
            preferences.edit().putBoolean(key, true).apply()
        }
    }

    private fun meaningfulActiveDay(firstAction: String) {
        val context = applicationContext ?: return
        if (analytics == null) return
        val today = LocalDate.now()
        val key = "${MEANINGFUL_ACTIVE_DAY_KEY}_${if (BuildConfig.DEBUG) "debug" else "release"}"
        synchronized(this) {
            val preferences = context.getSharedPreferences(MILESTONE_PREFERENCES, Context.MODE_PRIVATE)
            val previousDate = preferences.getString(key, null)
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (previousDate == today) return
            val daysSincePrevious = previousDate
                ?.let { ChronoUnit.DAYS.between(it, today) }
                ?.takeIf { it >= 0L }
            log(
                "meaningful_active_day",
                "first_action" to firstAction,
                "days_since_previous" to daysSincePrevious
            )
            preferences.edit().putString(key, today.toString()).apply()
        }
    }

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
