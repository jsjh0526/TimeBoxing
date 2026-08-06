package dev.jsjh.timebox.feature.tutorial

import android.content.Context

enum class TutorialLaunchSource(val analyticsValue: String) {
    AUTO_NEW("auto_new"),
    SETTINGS("settings")
}

data class TutorialSession(
    val source: TutorialLaunchSource,
    val isRestart: Boolean = false
)

internal enum class TutorialAutoStatus {
    NEW,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED,
    INELIGIBLE
}

internal enum class TutorialAutoDecision {
    START_FRESH,
    RESTART,
    DO_NOT_START
}

internal fun decideAutoTutorial(
    status: TutorialAutoStatus,
    seededThisLaunch: Boolean
): TutorialAutoDecision = when {
    status == TutorialAutoStatus.IN_PROGRESS -> TutorialAutoDecision.RESTART
    status != TutorialAutoStatus.NEW -> TutorialAutoDecision.DO_NOT_START
    seededThisLaunch -> TutorialAutoDecision.START_FRESH
    else -> TutorialAutoDecision.DO_NOT_START
}

class TutorialOnboardingStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    internal fun readStatus(): TutorialAutoStatus = preferences
        .getString(STATUS_KEY, null)
        ?.let { stored ->
            TutorialAutoStatus.entries.firstOrNull { it.name == stored }
        }
        ?: TutorialAutoStatus.NEW

    fun markInProgress() = write(TutorialAutoStatus.IN_PROGRESS)

    fun markCompleted() = write(TutorialAutoStatus.COMPLETED)

    fun markSkipped() = write(TutorialAutoStatus.SKIPPED)

    fun markIneligible() = write(TutorialAutoStatus.INELIGIBLE)

    private fun write(status: TutorialAutoStatus) {
        preferences.edit().putString(STATUS_KEY, status.name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "tutorial_onboarding"
        const val STATUS_KEY = "auto_tutorial_status_v2"
    }
}
