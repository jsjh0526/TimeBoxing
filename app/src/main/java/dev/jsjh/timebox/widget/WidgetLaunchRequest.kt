package dev.jsjh.timebox.widget

import android.content.Intent
import android.os.SystemClock
import java.time.LocalDate

data class WidgetTaskTarget(
    val ownerUserId: String,
    val taskId: String,
    val date: LocalDate
)

data class WidgetLaunchRequest(
    val openTodo: Boolean,
    val openAddTask: Boolean,
    val openSettings: Boolean = false,
    val nonce: Long = SystemClock.elapsedRealtime(),
    val taskTarget: WidgetTaskTarget? = null,
    val openTask: Boolean = taskTarget != null
) {
    companion object {
        const val EXTRA_OPEN_TAB = "dev.jsjh.timebox.extra.OPEN_TAB"
        const val EXTRA_OPEN_ADD_TASK = "dev.jsjh.timebox.extra.OPEN_ADD_TASK"
        const val EXTRA_TASK_OWNER = "dev.jsjh.timebox.extra.TASK_OWNER"
        const val EXTRA_TASK_ID = "dev.jsjh.timebox.extra.TASK_ID"
        const val EXTRA_TASK_DATE = "dev.jsjh.timebox.extra.TASK_DATE"
        const val TAB_TODO = "todo"
        const val TAB_SETTINGS = "settings"

        fun from(intent: Intent?): WidgetLaunchRequest? {
            if (intent == null) return null
            val openTab = intent.getStringExtra(EXTRA_OPEN_TAB)
            val openTodo = openTab == TAB_TODO
            val openSettings = openTab == TAB_SETTINGS
            val openAddTask = intent.getBooleanExtra(EXTRA_OPEN_ADD_TASK, false)
            val openTask = listOf(EXTRA_TASK_OWNER, EXTRA_TASK_ID, EXTRA_TASK_DATE).any(intent::hasExtra)
            val target = if (openTask) {
                val owner = intent.getStringExtra(EXTRA_TASK_OWNER)?.takeIf { it.isNotBlank() }
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)?.takeIf { it.isNotBlank() }
                val date = intent.getStringExtra(EXTRA_TASK_DATE)?.let {
                    runCatching { LocalDate.parse(it) }.getOrNull()
                }
                if (owner != null && taskId != null && date != null) WidgetTaskTarget(owner, taskId, date) else null
            } else null
            if (!openTodo && !openSettings && !openAddTask && !openTask) return null
            return WidgetLaunchRequest(
                openTodo = openTodo || openAddTask || openTask,
                openAddTask = openAddTask && !openTask,
                openSettings = openSettings && !openTask,
                taskTarget = target,
                openTask = openTask
            )
        }

        fun todoIntent(context: android.content.Context): Intent =
            Intent(context, dev.jsjh.timebox.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_TAB, TAB_TODO)

        fun addTaskIntent(context: android.content.Context): Intent =
            todoIntent(context).putExtra(EXTRA_OPEN_ADD_TASK, true)

        fun taskIntent(context: android.content.Context, target: WidgetTaskTarget): Intent =
            todoIntent(context)
                .putExtra(EXTRA_TASK_OWNER, target.ownerUserId)
                .putExtra(EXTRA_TASK_ID, target.taskId)
                .putExtra(EXTRA_TASK_DATE, target.date.toString())

        fun settingsIntent(context: android.content.Context): Intent =
            Intent(context, dev.jsjh.timebox.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_TAB, TAB_SETTINGS)
    }
}
