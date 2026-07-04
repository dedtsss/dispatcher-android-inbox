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
    private var recording = false
    private var overlayActive = true

    fun recordingStarted() {
        recording = true
        updateTimer()
    }

    fun recordingStopped() {
        recording = false
        cancelCountdown()
    }

    fun setOverlayActive(active: Boolean) {
        overlayActive = active
        updateTimer()
    }

    private fun updateTimer() {
        if (!recording || overlayActive) {
            cancelCountdown()
            return
        }
        if (job != null) return
        job = scope.launch {
            delay(timeoutMillis)
            job = null
            recording = false
            onTimeout()
        }
    }

    fun cancel() = recordingStopped()

    private fun cancelCountdown() {
        job?.cancel()
        job = null
    }
}
