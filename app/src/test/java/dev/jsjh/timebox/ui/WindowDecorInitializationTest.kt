package dev.jsjh.timebox.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.lifecycle.findViewTreeLifecycleOwner
import dev.jsjh.timebox.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33, 36])
class WindowDecorInitializationTest {
    @Test fun runtimeWindowCanBePreparedBeforeSuperOnCreate() {
        val controller = Robolectric.buildActivity(PreparedWindowActivity::class.java).setup()
        try {
            val activity = controller.get()
            assertSame(activity.preparedDecor, activity.window.decorView)
            assertNotNull(activity.window.decorView.findViewById<ViewGroup>(android.R.id.content))
            assertSame(activity.window.decorView, activity.content.rootView)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun laterSdkWindowReadsCannotCreateASecondDecorTree() {
        val controller = Robolectric.buildActivity(PreparedWindowActivity::class.java).setup()
        try {
            val activity = controller.get()
            val originalContent = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
            repeat(20) {
                assertSame(activity.preparedDecor, activity.window.decorView)
                assertSame(originalContent, activity.window.decorView.findViewById(android.R.id.content))
            }
            assertEquals(1, originalContent.childCount)
            assertSame(activity.content, originalContent.getChildAt(0))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun earlyPreparationStillInstallsTheActivityLifecycleOwner() {
        val controller = Robolectric.buildActivity(PreparedWindowActivity::class.java).setup()
        try {
            assertSame(controller.get(), controller.get().content.findViewTreeLifecycleOwner())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun repeatedActivityCreationUsesFreshContentTrees() {
        var previousDecor: View? = null
        repeat(5) {
            val controller = Robolectric.buildActivity(PreparedWindowActivity::class.java).setup()
            try {
                val activity = controller.get()
                assertNotSame(previousDecor, activity.preparedDecor)
                assertSame(activity.preparedDecor, activity.content.rootView)
                previousDecor = activity.preparedDecor
            } finally {
                controller.pause().stop().destroy()
            }
        }
    }

    // Exercise the native-window ordering without starting ads, auth, or network clients.
    class PreparedWindowActivity : ComponentActivity() {
        lateinit var preparedDecor: View
        lateinit var content: View

        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.Theme_TimeBoxing)
            preparedDecor = window.decorView
            super.onCreate(savedInstanceState)
            content = View(this)
            setContentView(content)
        }
    }
}
