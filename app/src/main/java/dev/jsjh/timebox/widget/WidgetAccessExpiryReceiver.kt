package dev.jsjh.timebox.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.jsjh.timebox.analytics.TimeBoxAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WidgetAccessExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            try {
                if (!WidgetAccessStore.isUnlocked(appContext)) {
                    TimeBoxAnalytics.initialize(appContext)
                    TimeBoxAnalytics.widgetAccessExpired()
                }
                TodoWidgetUpdater.updateNow(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
