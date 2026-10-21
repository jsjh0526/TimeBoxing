package dev.jsjh.timebox.diagnostics

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException

/** Records stages only. Never pass account, task, token, or exception-message data. */
class StartupTrace internal constructor(
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val sink: (String) -> Unit = { Log.i(TAG, it) }
) {
    private val activityId = nextActivityId.incrementAndGet()
    private val startedAt = clock()
    private val nextSpanId = AtomicInteger()
    private val pending = ConcurrentHashMap<Int, String>()

    internal fun event(name: String) {
        val line = "activity=$activityId elapsedMs=${clock() - startedAt} $name"
        runCatching { sink(line) }
    }

    internal fun begin(stage: String): Span {
        val id = nextSpanId.incrementAndGet()
        pending[id] = stage
        event("START span=$id stage=$stage")
        return Span(id, stage, clock())
    }

    internal fun reportPending() {
        event("WAITING stages=${pending.toSortedMap().values.joinToString(",").ifEmpty { "none" }}")
    }

    internal inner class Span(
        private val id: Int,
        private val stage: String,
        private val startedAt: Long
    ) {
        private val finished = AtomicBoolean()

        fun finish(error: Throwable? = null) {
            if (!finished.compareAndSet(false, true)) return
            pending.remove(id)
            val result = when (error) {
                null -> "END"
                is CancellationException -> "CANCELLED"
                else -> "FAILED"
            }
            val errorType = error?.let { " errorType=${it.javaClass.simpleName}" }.orEmpty()
            event("$result span=$id stage=$stage durationMs=${clock() - startedAt}$errorType")
        }
    }

    companion object {
        const val TAG = "TimeBoxStartup"
        private val nextActivityId = AtomicInteger()
    }
}

internal inline fun <T> StartupTrace?.traceStep(stage: String, block: () -> T): T {
    val span = this?.begin(stage)
    try {
        val result = block()
        span?.finish()
        return result
    } catch (error: Throwable) {
        span?.finish(error)
        throw error
    }
}
