package dev.jsjh.timebox.widget

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import dev.jsjh.timebox.R
import dev.jsjh.timebox.domain.model.DailyTask
import java.time.LocalDate
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class TodoWidgetRenderingTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val today = LocalDate.of(2026, 10, 20)

    @Test fun realRemoteViewsContainScrollableListAndNoMoreButton() {
        val root = render(tasks = (1..25).map { task(it) })
        val list = root.descendants().filterIsInstance<ListView>().single()
        assertFalse(list.hasOnClickListeners())
        val labels = root.descendants().filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(context.getString(R.string.widget_today) in labels)
        assertFalse(labels.any { it.contains("more in TimeBox") })
        assertTrue(list.adapter != null)
        assertEquals(25, list.adapter.count)
        val ids = todoWidgetItemIds((1..25).map { task(it) })
        (0 until list.adapter.count).forEach { position ->
            assertEquals(ids.getValue(task(position + 1).widgetItemKey()), list.adapter.getItemId(position))
        }
    }

    @Test fun completionAndDeleteCallbacksKeepTheirTaskIdentityAfterListChanges() {
        val original = (1..4).map { task(it) }
        listOf(original, original.reversed(), original.drop(1)).forEach { tasks ->
            val root = render(tasks)
            val list = root.descendants().filterIsInstance<ListView>().single()
            tasks.forEachIndexed { position, task ->
                val row = list.adapter.getView(position, null, list)
                val intents = row.descendants().filter { it.hasOnClickListeners() }.map { callbackIntent(it) }
                assertEquals(setOf(ToggleWidgetTaskAction::class.java.name, DeleteWidgetTaskAction::class.java.name),
                    intents.map { it.getStringExtra("ActionCallbackBroadcastReceiver:callbackClass") }.toSet())
                intents.forEach { intent ->
                    val parameters = intent.getBundleExtra("ActionCallbackBroadcastReceiver:parameters")!!
                    assertEquals(task.id, parameters.getString("task_id"))
                    assertEquals(task.date.toString(), parameters.getString("task_date"))
                }
            }
        }
    }

    @Test fun emptyStateHasNoScrollListButKeepsAppEntryAndAddButton() {
        val root = render(emptyList())
        val views = root.descendants()
        assertFalse(views.any { it is ListView })
        assertTrue(views.filterIsInstance<TextView>().any { it.text.toString() == context.getString(R.string.widget_no_tasks) })
        assertTrue(views.single { it.contentDescription == context.getString(R.string.home_add_task) }.clickTarget() != null)
        assertTrue(views.filterIsInstance<TextView>().single { it.text.toString() == context.getString(R.string.widget_today) }.clickTarget() != null)
    }

    @Test fun headerAndPlusReallySendDifferentActivityIntents() {
        val root = render(listOf(task(1)))
        val labels = root.descendants().filterIsInstance<TextView>()
        assertTrue(labels.single { it.text.toString() == context.getString(R.string.widget_today) }.clickTarget()!!.performClick())
        val headerIntent = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
        assertTrue(WidgetLaunchRequest.from(headerIntent)!!.openTodo)
        assertFalse(WidgetLaunchRequest.from(headerIntent)!!.openAddTask)
        val expectedFlags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        assertEquals(expectedFlags, headerIntent.flags and expectedFlags)
        assertTrue(root.descendants().single { it.contentDescription == context.getString(R.string.home_add_task) }.clickTarget()!!.performClick())
        val addIntent = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
        assertTrue(WidgetLaunchRequest.from(addIntent)!!.openAddTask)
        assertEquals(expectedFlags, addIntent.flags and expectedFlags)
    }

    @Test fun lockedWidgetSendsSettingsIntent() {
        val root = render(emptyList(), unlocked = false)
        root.descendants().filterIsInstance<TextView>()
            .single { it.text.toString() == context.getString(R.string.widget_locked_action) }
            .clickTarget()!!.performClick()
        val intent = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
        assertTrue(WidgetLaunchRequest.from(intent)!!.openSettings)
    }

    @Test fun actualFirstCardAndControlsFitAtMinimumDefaultAndLargeSizesWithLargeTextAndRtl() {
        listOf("en", "ko", "fa").forEach { language ->
            listOf(1f, 2f).forEach { scale ->
                val configuration = Configuration(context.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(language))
                    fontScale = scale
                    densityDpi = 160
                }
                val localized = context.createConfigurationContext(configuration)
                (TodoWidgetSizes + DpSize(600.dp, 500.dp)).forEach { size ->
                    val root = render(listOf(task(1).copy(isBig3 = true), task(2)), size, localized)
                    root.measure(exact(size.width.value.toInt()), exact(size.height.value.toInt()))
                    root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                    assertEquals(configuration.layoutDirection, root.layoutDirection)
                    val list = root.descendants().filterIsInstance<ListView>().single()
                    root.descendants().filterIsInstance<TextView>().forEach { label ->
                        assertTrue("$language $scale $size: clipped header ${label.text}",
                            label.layout.getLineBottom(0) <= label.height - label.compoundPaddingTop - label.compoundPaddingBottom)
                    }
                    val row = list.adapter.getView(0, null, list)
                    row.measure(exact(list.width), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    row.layout(0, 0, row.measuredWidth, row.measuredHeight)
                    assertTrue("$language $scale $size: ${row.height} > ${list.height}", row.height <= list.height)
                    val title = row.descendants().filterIsInstance<TextView>().single { it.text.toString() == "Task 1" }
                    assertFalse(title.hasOnClickListeners())
                    row.descendants().filterIsInstance<TextView>().forEach { label ->
                        assertTrue("$language $scale $size: clipped ${label.text} (${label.layout.getLineBottom(0)}/${label.height})",
                            label.layout.getLineBottom(0) <= label.height - label.compoundPaddingTop - label.compoundPaddingBottom)
                    }
                    val buttons = row.descendants().filter { it.hasOnClickListeners() }
                    assertEquals(2, buttons.size)
                    val titleBounds = boundsIn(row, title)
                    buttons.forEach { button -> assertFalse(Rect.intersects(titleBounds, boundsIn(row, button))) }
                    val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                    root.draw(Canvas(bitmap))
                    val directory = File("build/reports/widget-previews").apply { mkdirs() }
                    File(directory, "$language-$scale-${size.width.value.toInt()}x${size.height.value.toInt()}.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        }
    }

    private fun render(
        tasks: List<DailyTask>,
        size: DpSize = DpSize(250.dp, 220.dp),
        hostContext: Context = context,
        unlocked: Boolean = true
    ): View {
        val views = runBlocking {
            GlanceRemoteViews().compose(hostContext, size) {
                TodoWidgetContent(
                    TodoWidgetState(today, tasks, tasks.count { !it.isCompleted }, tasks.size, unlocked),
                    hostContext
                )
            }.remoteViews
        }
        return views.apply(hostContext, AppWidgetHostView(hostContext)).apply {
            layoutDirection = hostContext.resources.configuration.layoutDirection
        }
    }

    private fun task(index: Int) = DailyTask(id = "task-$index", date = today, title = "Task $index")

    private fun callbackIntent(button: View): Intent {
        // Inspect generated collection fill-in intents without invoking database mutations.
        val listener = shadowOf(button).onClickListener!!
        val action = listener.javaClass.declaredFields.single {
            it.type.name.endsWith("\$SetOnClickResponse")
        }.apply { isAccessible = true }.get(listener)
        val response = ReflectionHelpers.getField<Any>(action, "mResponse")
        val fillIn = ReflectionHelpers.getField<Intent>(response, "mFillIntent")
        return intentTree(fillIn).single { it.hasExtra("ActionCallbackBroadcastReceiver:callbackClass") }
    }

    private fun intentTree(intent: Intent): List<Intent> = listOf(intent) + bundleIntents(intent.extras)

    @Suppress("DEPRECATION")
    private fun bundleIntents(bundle: Bundle?): List<Intent> = bundle?.keySet()?.flatMap { key ->
        when (val value = bundle.get(key)) {
            is Intent -> intentTree(value)
            is Bundle -> bundleIntents(value)
            else -> emptyList()
        }
    }.orEmpty()

    private fun View.descendants(): List<View> = listOf(this) +
        if (this is ViewGroup) (0 until childCount).flatMap { getChildAt(it).descendants() } else emptyList()

    private fun View.clickTarget(): View? = if (hasOnClickListeners()) this else (parent as? View)?.clickTarget()

    private fun exact(value: Int) = View.MeasureSpec.makeMeasureSpec(value, View.MeasureSpec.EXACTLY)

    private fun boundsIn(root: View, child: View): Rect = Rect(0, 0, child.width, child.height).also {
        (root as ViewGroup).offsetDescendantRectToMyCoords(child, it)
    }
}
