package dev.jsjh.timebox.diagnostics

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupTraceTest {
    private val time = AtomicLong(100L)
    private val lines = mutableListOf<String>()
    private val trace = StartupTrace(clock = time::get, sink = lines::add)

    @Test fun successfulStepsPreserveResultsAndRecordElapsedTime() {
        val result = trace.traceStep("content.install") {
            time.addAndGet(25)
            "result"
        }
        trace.reportPending()

        assertEquals("result", result)
        assertTrue(lines.any { "START" in it && "stage=content.install" in it })
        assertTrue(lines.any { "END" in it && "durationMs=25" in it })
        assertTrue(lines.last().endsWith("WAITING stages=none"))
    }

    @Test fun failuresAreRethrownWithoutRecordingSensitiveMessages() {
        val expected = IllegalStateException("private-account-token")
        try {
            trace.traceStep("auth.restore") { throw expected }
        } catch (actual: IllegalStateException) {
            assertSame(expected, actual)
        }
        trace.reportPending()

        assertTrue(lines.any { "FAILED" in it && "errorType=IllegalStateException" in it })
        assertFalse(lines.any { "private-account-token" in it })
        assertTrue(lines.last().endsWith("WAITING stages=none"))
    }

    @Test fun suspendedStepsRemainVisibleUntilCancelled() = runBlocking {
        val neverCompletes = CompletableDeferred<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            trace.traceStep("restore.initial") {
                trace.traceStep("sync.account_lock") { neverCompletes.await() }
            }
        }
        trace.reportPending()
        assertTrue(lines.last().endsWith("WAITING stages=restore.initial,sync.account_lock"))

        job.cancelAndJoin()
        trace.reportPending()
        assertEquals(2, lines.count { "CANCELLED" in it })
        assertTrue(lines.last().endsWith("WAITING stages=none"))
    }

    @Test fun aBrokenLogSinkCannotPreventTheApplicationOperation() {
        val brokenTrace = StartupTrace(clock = time::get, sink = { error("logger failure") })
        var calls = 0
        assertEquals(42, brokenTrace.traceStep("widget.handle") { calls++; 42 })
        brokenTrace.reportPending()
        assertEquals(1, calls)
    }

    @Test fun disabledTracingDoesNotRepeatAnOperationReturningNull() {
        var calls = 0
        val disabled: StartupTrace? = null
        val result = disabled.traceStep("optional") { calls++; null }
        assertNull(result)
        assertEquals(1, calls)
    }

    @Test fun activitiesHaveSeparateCorrelationIdsAndPendingStages() {
        val otherLines = mutableListOf<String>()
        val otherTrace = StartupTrace(clock = time::get, sink = otherLines::add)
        val span = trace.begin("restore.initial")
        trace.reportPending()
        otherTrace.reportPending()
        assertTrue(lines.last().endsWith("WAITING stages=restore.initial"))
        assertTrue(otherLines.last().endsWith("WAITING stages=none"))
        assertFalse(lines.last().substringBefore(' ') == otherLines.last().substringBefore(' '))
        span.finish()
        span.finish()
        assertEquals(1, lines.count { "END" in it })
    }
}
