package dev.jsjh.timebox.widget

import android.content.Context
import android.content.Intent
import android.os.Parcel
import androidx.test.core.app.ApplicationProvider
import dev.jsjh.timebox.MainActivity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetLaunchRequestTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val target = WidgetTaskTarget("account-a", "task-a", LocalDate.of(2026, 10, 20))

    @Test fun headerOpensTodoWithoutOpeningEditor() {
        val request = parse(WidgetLaunchRequest.todoIntent(context))
        assertTrue(request.openTodo)
        assertFalse(request.openAddTask)
        assertFalse(request.openSettings)
    }

    @Test fun plusOpensTodoAndEditor() {
        val request = parse(WidgetLaunchRequest.addTaskIntent(context))
        assertTrue(request.openTodo)
        assertTrue(request.openAddTask)
        assertFalse(request.openSettings)
    }

    @Test fun lockedWidgetOpensSettingsInsteadOfEditor() {
        val request = parse(WidgetLaunchRequest.settingsIntent(context))
        assertTrue(request.openSettings)
        assertFalse(request.openTodo)
        assertFalse(request.openAddTask)
    }

    @Test fun allEntriesTargetMainActivityAndReuseItWhenAlreadyRunning() {
        listOf(
            WidgetLaunchRequest.todoIntent(context),
            WidgetLaunchRequest.addTaskIntent(context),
            WidgetLaunchRequest.taskIntent(context, target),
            WidgetLaunchRequest.settingsIntent(context)
        ).forEach { intent ->
            assertEquals(MainActivity::class.java.name, intent.component?.className)
            listOf(Intent.FLAG_ACTIVITY_NEW_TASK, Intent.FLAG_ACTIVITY_CLEAR_TOP, Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .forEach { flag -> assertEquals(flag, intent.flags and flag) }
            assertEquals(0, intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    @Test fun taskEntryKeepsAccountDateAndIdentityWithoutOpeningANewTask() {
        val request = parse(WidgetLaunchRequest.taskIntent(context, target))
        assertTrue(request.openTask)
        assertTrue(request.openTodo)
        assertFalse(request.openAddTask)
        assertFalse(request.openSettings)
        assertEquals(target, request.taskTarget)
    }

    @Test fun malformedTaskEntryFallsBackToTodoAndCannotOpenANewTaskOrSettings() {
        listOf(
            WidgetLaunchRequest.taskIntent(context, target).putExtra(WidgetLaunchRequest.EXTRA_TASK_DATE, "invalid"),
            WidgetLaunchRequest.taskIntent(context, target).putExtra(WidgetLaunchRequest.EXTRA_TASK_OWNER, ""),
            WidgetLaunchRequest.taskIntent(context, target).apply { removeExtra(WidgetLaunchRequest.EXTRA_TASK_ID) }
        ).forEach { intent ->
            intent.putExtra(WidgetLaunchRequest.EXTRA_OPEN_ADD_TASK, true)
            intent.putExtra(WidgetLaunchRequest.EXTRA_OPEN_TAB, WidgetLaunchRequest.TAB_SETTINGS)
            val request = parse(intent)
            assertTrue(request.openTodo)
            assertTrue(request.openTask)
            assertNull(request.taskTarget)
            assertFalse(request.openAddTask)
            assertFalse(request.openSettings)
        }
    }

    @Test fun taskTargetSurvivesParcelDeliveryAndSeparateCardsKeepSeparateTargets() {
        val other = target.copy(taskId = "task-b", date = target.date.minusDays(1))
        listOf(target, other).forEach { expected ->
            val parcel = Parcel.obtain()
            try {
                WidgetLaunchRequest.taskIntent(context, expected).writeToParcel(parcel, 0)
                parcel.setDataPosition(0)
                assertEquals(expected, parse(Intent.CREATOR.createFromParcel(parcel)).taskTarget)
            } finally {
                parcel.recycle()
            }
        }
    }

    @Test fun requestExtrasSurviveIntentCopyAndParcelDelivery() {
        val original = WidgetLaunchRequest.addTaskIntent(context)
        val parcel = Parcel.obtain()
        try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            listOf(Intent(original), Intent.CREATOR.createFromParcel(parcel)).forEach { delivered ->
                val pendingRequest = WidgetLaunchRequest.from(delivered)
                assertNotNull(pendingRequest)
                assertTrue(pendingRequest!!.openAddTask)
            }
        } finally {
            parcel.recycle()
        }
        assertNull(WidgetLaunchRequest.from(null))
        assertNull(WidgetLaunchRequest.from(Intent()))
    }

    @Test fun widgetProviderAllowsResizingWithinTheNewMinimumDimensions() {
        val parser = context.resources.getXml(dev.jsjh.timebox.R.xml.todo_widget_info)
        parser.use {
            while (it.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) it.next()
            val android = "http://schemas.android.com/apk/res/android"
            assertEquals("250.0dip", it.getAttributeValue(android, "minWidth"))
            assertEquals("180.0dip", it.getAttributeValue(android, "minHeight"))
            assertEquals("200.0dip", it.getAttributeValue(android, "minResizeWidth"))
            assertEquals("180.0dip", it.getAttributeValue(android, "minResizeHeight"))
            assertEquals(3, it.getAttributeIntValue(android, "resizeMode", 0))
        }
    }

    private fun parse(intent: Intent): WidgetLaunchRequest = WidgetLaunchRequest.from(intent)!!
}
