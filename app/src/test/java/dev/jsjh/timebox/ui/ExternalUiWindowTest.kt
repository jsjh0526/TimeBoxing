package dev.jsjh.timebox.ui

import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ExternalUiWindowTest {
    private lateinit var controller: ActivityController<ComponentActivity>
    private val activity get() = controller.get()

    @Before fun createActivity() {
        controller = Robolectric.buildActivity(ComponentActivity::class.java).create().start()
        activity.setContentView(FrameLayout(activity))
    }

    @After fun destroyActivity() {
        if (!activity.isDestroyed) controller.pause().stop().destroy()
    }

    @Test fun requiresResumedAttachedAndFocusedWindow() {
        assertFalse(activity.isReadyForExternalUi())
        controller.resume().visible()
        controller.windowFocusChanged(false)
        assertFalse(activity.isReadyForExternalUi())
        controller.windowFocusChanged(true)
        assertTrue(activity.isReadyForExternalUi())
        controller.pause()
        assertFalse(activity.isReadyForExternalUi())
    }

    @Test fun finishingActivityCannotLaunchUi() {
        resumeWithFocus()
        activity.finish()
        assertFalse(activity.isReadyForExternalUi())
        var cancelled = 0
        activity.runWhenWindowReady(onCancelled = { cancelled++ }) { error("Must not launch") }
        assertEquals(1, cancelled)
    }

    @Test fun executesExactlyOnceAfterStableDelay() {
        resumeWithFocus()
        var launches = 0
        activity.runWhenWindowReady { launches++ }
        advance(799)
        assertEquals(0, launches)
        advance(1)
        assertEquals(1, launches)
        controller.windowFocusChanged(false).windowFocusChanged(true)
        advance(2_000)
        assertEquals(1, launches)
    }

    @Test fun requestBeforeResumeWaitsForWindow() {
        var launches = 0
        activity.runWhenWindowReady { launches++ }
        advance(2_000)
        assertEquals(0, launches)
        resumeWithFocus()
        advance(800)
        assertEquals(1, launches)
    }

    @Test fun focusLossRestartsFullSettlingInterval() {
        resumeWithFocus()
        var launches = 0
        activity.runWhenWindowReady { launches++ }
        advance(500)
        controller.windowFocusChanged(false)
        advance(2_000)
        assertEquals(0, launches)
        controller.windowFocusChanged(true)
        advance(799)
        assertEquals(0, launches)
        advance(1)
        assertEquals(1, launches)
    }

    @Test fun pauseSuspendsPendingUiUntilResumed() {
        resumeWithFocus()
        var launches = 0
        activity.runWhenWindowReady { launches++ }
        advance(500)
        controller.pause().stop()
        advance(2_000)
        assertEquals(0, launches)
        controller.restart().start().resume().windowFocusChanged(true)
        advance(799)
        assertEquals(0, launches)
        advance(1)
        assertEquals(1, launches)
    }

    @Test fun destroyCancelsWithoutLaunchingOnOldActivity() {
        resumeWithFocus()
        var launches = 0
        var cancelled = 0
        val cancel = activity.runWhenWindowReady(onCancelled = { cancelled++ }) { launches++ }
        advance(500)
        controller.pause().stop().destroy()
        advance(2_000)
        cancel()
        assertEquals(0, launches)
        assertEquals(1, cancelled)
    }

    @Test fun explicitCancellationIsIdempotent() {
        resumeWithFocus()
        var launches = 0
        var cancelled = 0
        val cancel = activity.runWhenWindowReady(onCancelled = { cancelled++ }) { launches++ }
        cancel()
        cancel()
        controller.windowFocusChanged(false).windowFocusChanged(true)
        advance(2_000)
        assertEquals(0, launches)
        assertEquals(1, cancelled)
    }

    @Test fun detachedDecorCannotLaunchUi() {
        resumeWithFocus()
        var launches = 0
        activity.runWhenWindowReady { launches++ }
        advance(500)
        val decor = activity.window.decorView
        val params = decor.layoutParams as WindowManager.LayoutParams
        activity.windowManager.removeViewImmediate(decor)
        try {
            assertFalse(activity.isReadyForExternalUi())
            advance(2_000)
            assertEquals(0, launches)
        } finally {
            // Restore the controller-owned decor before Robolectric tears down the Activity.
            activity.windowManager.addView(decor, params)
        }
    }

    @Test fun reattachedDecorWaitsAgainBeforeLaunching() {
        resumeWithFocus()
        var launches = 0
        activity.runWhenWindowReady { launches++ }
        advance(500)
        val decor = activity.window.decorView
        val params = decor.layoutParams as WindowManager.LayoutParams
        activity.windowManager.removeViewImmediate(decor)
        activity.windowManager.addView(decor, params)
        decor.visibility = View.VISIBLE
        controller.windowFocusChanged(true)
        advance(799)
        assertEquals(0, launches)
        advance(1)
        assertEquals(1, launches)
    }

    @Test fun completedRequestDoesNotReportCancellationLater() {
        resumeWithFocus()
        var launches = 0
        var cancelled = 0
        val cancel = activity.runWhenWindowReady(onCancelled = { cancelled++ }) { launches++ }
        advance(800)
        cancel()
        controller.pause().stop().destroy()
        assertEquals(1, launches)
        assertEquals(0, cancelled)
    }

    private fun resumeWithFocus() {
        controller.resume().visible().windowFocusChanged(true)
        assertTrue(activity.isReadyForExternalUi())
    }

    private fun advance(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(millis, TimeUnit.MILLISECONDS)
    }
}
