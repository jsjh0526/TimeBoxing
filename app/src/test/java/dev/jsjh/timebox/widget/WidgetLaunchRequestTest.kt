package dev.jsjh.timebox.widget

import android.content.Context
import android.content.Intent
import android.os.Parcel
import androidx.test.core.app.ApplicationProvider
import dev.jsjh.timebox.MainActivity
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
            WidgetLaunchRequest.settingsIntent(context)
        ).forEach { intent ->
            assertEquals(MainActivity::class.java.name, intent.component?.className)
            listOf(Intent.FLAG_ACTIVITY_NEW_TASK, Intent.FLAG_ACTIVITY_CLEAR_TOP, Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .forEach { flag -> assertEquals(flag, intent.flags and flag) }
            assertEquals(0, intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
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
