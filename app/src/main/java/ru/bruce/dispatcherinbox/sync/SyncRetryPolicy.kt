package ru.bruce.dispatcherinbox.sync

object SyncRetryPolicy {
    fun isRetryableHttp(code: Int): Boolean = code == 408 || code == 429 || code >= 500

    fun shouldRetryWorker(
        hasRetryableFailure: Boolean,
        isPeriodicWatchdog: Boolean,
    ): Boolean = hasRetryableFailure && !isPeriodicWatchdog
}
