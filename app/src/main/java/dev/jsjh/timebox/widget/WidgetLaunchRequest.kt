package dev.jsjh.timebox.widget

import android.content.Intent
import android.os.SystemClock

data class WidgetLaunchRequest(
    val openTodo: Boolean,
    val openAddTask: Boolean,
    val nonce: Long = SystemClock.elapsedRealtime()
) {
    companion object {
        const val EXTRA_OPEN_TAB = "dev.jsjh.timebox.extra.OPEN_TAB"
        const val EXTRA_OPEN_ADD_TASK = "dev.jsjh.timebox.extra.OPEN_ADD_TASK"
        const val TAB_TODO = "todo"

        fun from(intent: Intent?): WidgetLaunchRequest? {
            if (intent == null) return null
            val openTodo = intent.getStringExtra(EXTRA_OPEN_TAB) == TAB_TODO
            val openAddTask = intent.getBooleanExtra(EXTRA_OPEN_ADD_TASK, false)
            if (!openTodo && !openAddTask) return null
            return WidgetLaunchRequest(openTodo = openTodo || openAddTask, openAddTask = openAddTask)
        }

        fun todoIntent(context: android.content.Context): Intent =
            Intent(context, dev.jsjh.timebox.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_OPEN_TAB, TAB_TODO)

        fun addTaskIntent(context: android.content.Context): Intent =
            todoIntent(context).putExtra(EXTRA_OPEN_ADD_TASK, true)
    }
}
