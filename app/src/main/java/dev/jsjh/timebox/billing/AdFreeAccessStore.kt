package dev.jsjh.timebox.billing

import android.content.Context
import android.os.SystemClock
import dev.jsjh.timebox.BuildConfig

internal class AdFreeAccessStore(
    context: Context,
    private val enabled: Boolean = BuildConfig.MONETIZATION_ENABLED,
    private val now: () -> Long = System::currentTimeMillis,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime
) {
    private val prefs = context.applicationContext.getSharedPreferences("ad_free_internal", Context.MODE_PRIVATE)

    fun hasAccess(): Boolean = enabled && cachedAccessIsValid(
        prefs.getLong("checked_at", 0), prefs.getLong("checked_elapsed", -1), now(), elapsed()
    )

    fun cacheExpiresAtMillis(): Long =
        if (hasAccess()) prefs.getLong("checked_at", 0) + AdFreeCacheDurationMillis else 0

    fun recordPlayResult(hasAccess: Boolean) {
        if (!enabled) return
        if (hasAccess) {
            prefs.edit().putLong("checked_at", now()).putLong("checked_elapsed", elapsed()).apply()
        } else if (prefs.contains("checked_at")) {
            prefs.edit().remove("checked_at").remove("checked_elapsed").apply()
        }
    }
}
