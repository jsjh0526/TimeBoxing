package dev.jsjh.timebox.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

object WidgetAccessStore {
    private const val PREFS_NAME = "widget_access"
    private const val KEY_UNLOCKED_UNTIL = "unlocked_until"
    private const val EXPIRY_REFRESH_REQUEST_CODE = 7407
    private val REWARD_DURATION_MS = TimeUnit.DAYS.toMillis(7)
    private val MAX_EXTENSION_MS = TimeUnit.DAYS.toMillis(28)

    fun unlockedUntilMillis(context: Context): Long =
        prefs(context).getLong(KEY_UNLOCKED_UNTIL, 0L)

    fun remainingMillis(context: Context): Long =
        max(0L, unlockedUntilMillis(context) - System.currentTimeMillis())

    fun isUnlocked(context: Context): Boolean =
        remainingMillis(context) > 0L

    fun canExtend(context: Context): Boolean =
        remainingMillis(context) <= MAX_EXTENSION_MS - REWARD_DURATION_MS

    fun extendByReward(context: Context): Long {
        val now = System.currentTimeMillis()
        val currentBase = max(now, unlockedUntilMillis(context))
        val next = min(currentBase + REWARD_DURATION_MS, now + MAX_EXTENSION_MS)
        prefs(context).edit().putLong(KEY_UNLOCKED_UNTIL, next).apply()
        scheduleExpiryRefresh(context, next)
        return next
    }

    fun scheduleExpiryRefresh(context: Context, unlockedUntilMillis: Long = unlockedUntilMillis(context)) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = expiryPendingIntent(appContext)
        if (unlockedUntilMillis <= System.currentTimeMillis()) {
            alarmManager.cancel(pendingIntent)
            TodoWidgetUpdater.requestUpdate(appContext)
            return
        }

        runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        unlockedUntilMillis,
                        pendingIntent
                    )
                }
                else -> alarmManager.set(AlarmManager.RTC_WAKEUP, unlockedUntilMillis, pendingIntent)
            }
        }.onFailure {
            alarmManager.set(AlarmManager.RTC_WAKEUP, unlockedUntilMillis, pendingIntent)
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun expiryPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            EXPIRY_REFRESH_REQUEST_CODE,
            Intent(context, WidgetAccessExpiryReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
