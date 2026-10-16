package dev.jsjh.timebox.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

internal fun Activity.isReadyForExternalUi(): Boolean {
    if (isFinishing || isDestroyed) return false
    val owner = this as? LifecycleOwner ?: return false
    if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
    val decor = window.peekDecorView() ?: return false
    return decor.isAttachedToWindow && decor.windowToken != null && hasWindowFocus()
}

@MainThread
internal fun Activity.runWhenWindowReady(
    settleDelayMillis: Long = 800L,
    onCancelled: () -> Unit = {},
    onReady: () -> Unit
): () -> Unit {
    val owner = this as? LifecycleOwner
    if (owner == null || isFinishing || isDestroyed) {
        onCancelled()
        return {}
    }
    val request = WindowReadyRequest(this, owner, settleDelayMillis, onCancelled, onReady)
    request.start()
    return request::cancel
}

private class WindowReadyRequest(
    private val activity: Activity,
    private val owner: LifecycleOwner,
    private val settleDelayMillis: Long,
    private val onCancelled: () -> Unit,
    private val onReady: () -> Unit
) : LifecycleEventObserver, View.OnAttachStateChangeListener,
    ViewTreeObserver.OnWindowFocusChangeListener, Runnable {
    private val handler = Handler(Looper.getMainLooper())
    private var decor: View? = null
    private var focusObserver: ViewTreeObserver? = null
    private var expectedToken: android.os.IBinder? = null
    private var scheduled = false
    private var finished = false

    fun start() {
        owner.lifecycle.addObserver(this)
        update()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) = update()

    override fun onWindowFocusChanged(hasFocus: Boolean) = update()

    override fun onViewAttachedToWindow(v: View) = update()

    override fun onViewDetachedFromWindow(v: View) {
        clearSchedule()
        removeFocusObserver()
    }

    private fun update() {
        if (finished) return
        if (activity.isFinishing || activity.isDestroyed || owner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            cancel()
            return
        }
        val currentDecor = activity.window.peekDecorView()
        if (decor !== currentDecor) {
            clearSchedule()
            removeFocusObserver()
            decor?.removeOnAttachStateChangeListener(this)
            decor = currentDecor
            currentDecor?.addOnAttachStateChangeListener(this)
        }
        if (currentDecor?.isAttachedToWindow == true && focusObserver !== currentDecor.viewTreeObserver) {
            removeFocusObserver()
            focusObserver = currentDecor.viewTreeObserver.also { it.addOnWindowFocusChangeListener(this) }
        }
        if (!activity.isReadyForExternalUi()) {
            clearSchedule()
            return
        }
        // A new window token or focus/resume event must receive its own settling interval.
        if (scheduled && expectedToken != currentDecor?.windowToken) clearSchedule()
        if (!scheduled) {
            expectedToken = currentDecor?.windowToken
            scheduled = true
            handler.postDelayed(this, settleDelayMillis)
        }
    }

    override fun run() {
        if (finished) return
        scheduled = false
        if (
            activity.isReadyForExternalUi() &&
            decor === activity.window.peekDecorView() &&
            expectedToken != null && expectedToken == decor?.windowToken
        ) {
            finished = true
            cleanUp()
            onReady()
        } else {
            update()
        }
    }

    fun cancel() {
        if (finished) return
        finished = true
        cleanUp()
        onCancelled()
    }

    private fun cleanUp() {
        clearSchedule()
        removeFocusObserver()
        decor?.removeOnAttachStateChangeListener(this)
        owner.lifecycle.removeObserver(this)
    }

    private fun clearSchedule() {
        handler.removeCallbacks(this)
        scheduled = false
        expectedToken = null
    }

    private fun removeFocusObserver() {
        focusObserver?.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(this)
        focusObserver = null
    }
}
