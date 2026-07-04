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
    fun `active overlay does not start timeout`() = runTest {
        var fired = false
        val timer = RecordingSafetyTimer(this, timeoutMillis = 60_000L) { fired = true }

        timer.recordingStarted()
        advanceTimeBy(120_000L)
        runCurrent()
        assertFalse(fired)
    }

    @Test
    fun `inactive overlay starts 60 second timeout`() = runTest {
        var fired = false
        val timer = RecordingSafetyTimer(this, timeoutMillis = 60_000L) { fired = true }

        timer.recordingStarted()
        timer.setOverlayActive(false)
        advanceTimeBy(59_999L)
        runCurrent()
        assertFalse(fired)
        advanceTimeBy(1L)
        runCurrent()
        assertTrue(fired)
    }

    @Test
    fun `returning overlay cancels and resets timeout`() = runTest {
        var fired = false
        val timer = RecordingSafetyTimer(this, timeoutMillis = 60_000L) { fired = true }

        timer.recordingStarted()
        timer.setOverlayActive(false)
        advanceTimeBy(30_000L)
        timer.setOverlayActive(true)
        advanceTimeBy(60_000L)
        runCurrent()
        assertFalse(fired)

        timer.setOverlayActive(false)
        advanceTimeBy(60_000L)
        runCurrent()
        assertTrue(fired)
    }
}
