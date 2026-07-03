package ru.bruce.dispatcherinbox.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RecordingSafetyTimer(
    private val scope: CoroutineScope,
    private val timeoutMillis: Long,
    private val onTimeout: () -> Unit,
) {
    private var job: Job? = null

    fun start() {
        cancel()
        job = scope.launch {
            delay(timeoutMillis)
            job = null
            onTimeout()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
