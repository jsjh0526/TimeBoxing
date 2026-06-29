package dev.jsjh.timebox.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WidgetAccessExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        TodoWidgetUpdater.requestUpdate(context.applicationContext)
    }
}
