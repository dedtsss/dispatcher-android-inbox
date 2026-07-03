package ru.bruce.dispatcherinbox.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingSafetyTimerTest {
    @Test
    fun `fires once after recording limit`() = runTest {
        var fired = false
        val timer = RecordingSafetyTimer(this, timeoutMillis = 30_000L) { fired = true }

        timer.start()
        advanceTimeBy(29_999L)
        runCurrent()
        assertFalse(fired)

        advanceTimeBy(1L)
        runCurrent()
        assertTrue(fired)
    }

    @Test
    fun `manual cancellation prevents timeout`() = runTest {
        var fired = false
        val timer = RecordingSafetyTimer(this, timeoutMillis = 30_000L) { fired = true }

        timer.start()
        timer.cancel()
        advanceTimeBy(30_000L)
        runCurrent()

        assertFalse(fired)
    }
}
