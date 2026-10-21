package dev.jsjh.timebox.widget

import android.content.Context
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.AppWidgetId
import androidx.test.core.app.ApplicationProvider
import dev.jsjh.timebox.auth.ActiveUserStore
import dev.jsjh.timebox.data.local.database.TaskDatabase
import dev.jsjh.timebox.data.local.entity.DailyTaskEntity
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LegacyWidgetDeleteTest {
    @Test fun oldDeletePendingIntentCanNeverDeleteTheStoredTask() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val owner = "widget-delete-compat-test"
        ActiveUserStore.saveLoggedIn(context, owner)
        val database = TaskDatabase.get(context, owner)
        val date = LocalDate.of(2026, 10, 21)
        val task = DailyTaskEntity("keep", null, date.toString(), "Keep me", null, "[]", false, false,
            null, null, false, "ONE_OFF")
        try {
            withContext(Dispatchers.IO) { database.dailyTaskDao().upsert(task) }
            DeleteWidgetTaskAction().onAction(context, AppWidgetId(123), actionParametersOf(
                ActionParameters.Key<String>("task_id") to task.id,
                ActionParameters.Key<String>("task_date") to task.dateIso
            ))
            val after = withContext(Dispatchers.IO) { database.dailyTaskDao().getById(task.dateIso, task.id) }
            assertEquals(task, after)
        } finally {
            database.close()
            ActiveUserStore.clear(context)
        }
    }
}
