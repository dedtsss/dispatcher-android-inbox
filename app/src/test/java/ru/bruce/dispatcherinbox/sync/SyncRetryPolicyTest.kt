package ru.bruce.dispatcherinbox.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRetryPolicyTest {
    @Test
    fun `temporary HTTP failures are retryable`() {
        assertTrue(SyncRetryPolicy.isRetryableHttp(408))
        assertTrue(SyncRetryPolicy.isRetryableHttp(429))
        assertTrue(SyncRetryPolicy.isRetryableHttp(500))
        assertTrue(SyncRetryPolicy.isRetryableHttp(503))
    }

    @Test
    fun `permanent HTTP failures are not retried forever`() {
        assertFalse(SyncRetryPolicy.isRetryableHttp(200))
        assertFalse(SyncRetryPolicy.isRetryableHttp(400))
        assertFalse(SyncRetryPolicy.isRetryableHttp(401))
        assertFalse(SyncRetryPolicy.isRetryableHttp(403))
        assertFalse(SyncRetryPolicy.isRetryableHttp(404))
    }

    @Test
    fun `immediate worker keeps retryable queue failure scheduled`() {
        assertTrue(
            SyncRetryPolicy.shouldRetryWorker(
                hasRetryableFailure = true,
                isPeriodicWatchdog = false,
            ),
        )
        assertFalse(
            SyncRetryPolicy.shouldRetryWorker(
                hasRetryableFailure = false,
                isPeriodicWatchdog = false,
            ),
        )
    }

    @Test
    fun `watchdog completes so it runs again on its fixed interval`() {
        assertFalse(
            SyncRetryPolicy.shouldRetryWorker(
                hasRetryableFailure = true,
                isPeriodicWatchdog = true,
            ),
        )
    }
}
