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
    fun `retryable queue failure keeps worker scheduled`() {
        assertTrue(SyncRetryPolicy.shouldRetryWorker(true))
        assertFalse(SyncRetryPolicy.shouldRetryWorker(false))
    }
}
