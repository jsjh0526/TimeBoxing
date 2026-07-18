package dev.jsjh.timebox.review

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.play.core.review.ReviewManagerFactory
import java.util.concurrent.TimeUnit

object InAppReviewPrompter {
    private const val PREFS_NAME = "in_app_review_prompt"
    private const val KEY_LAUNCH_COUNT = "launch_count"
    private const val KEY_LAST_REVIEW_REQUEST_AT = "last_review_request_at"
    private const val MIN_LAUNCH_COUNT = 5
    private val MIN_INSTALLED_AGE_MS = TimeUnit.DAYS.toMillis(3)
    private val REVIEW_COOLDOWN_MS = TimeUnit.DAYS.toMillis(50)

    private var requestedThisProcess = false

    fun recordLaunch(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putInt(KEY_LAUNCH_COUNT, prefs.getInt(KEY_LAUNCH_COUNT, 0) + 1)
        }
    }

    fun requestIfEligible(activity: Activity) {
        if (requestedThisProcess) return
        if (!activity.isReadyForReviewFlow()) return
        val now = System.currentTimeMillis()
        val appContext = activity.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (prefs.getInt(KEY_LAUNCH_COUNT, 0) < MIN_LAUNCH_COUNT) return
        if (now - firstInstallTime(appContext) < MIN_INSTALLED_AGE_MS) return

        val lastRequestAt = prefs.getLong(KEY_LAST_REVIEW_REQUEST_AT, 0L)
        if (lastRequestAt > 0L && now - lastRequestAt < REVIEW_COOLDOWN_MS) return

        requestedThisProcess = true

        val reviewManager = ReviewManagerFactory.create(appContext)
        reviewManager.requestReviewFlow()
            .addOnSuccessListener { reviewInfo ->
                if (!activity.isReadyForReviewFlow()) {
                    requestedThisProcess = false
                    return@addOnSuccessListener
                }

                runCatching {
                    reviewManager.launchReviewFlow(activity, reviewInfo)
                }.onSuccess {
                    prefs.edit { putLong(KEY_LAST_REVIEW_REQUEST_AT, now) }
                }.onFailure {
                    requestedThisProcess = false
                }
            }
            .addOnFailureListener {
                requestedThisProcess = false
            }
    }

    private fun firstInstallTime(context: Context): Long {
        return runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        }.getOrDefault(System.currentTimeMillis())
    }

    private fun Activity.isReadyForReviewFlow(): Boolean {
        if (isFinishing || isDestroyed) return false
        val lifecycleOwner = this as? LifecycleOwner ?: return false
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
        val decorView = window.decorView
        return decorView.isAttachedToWindow && hasWindowFocus()
    }
}
