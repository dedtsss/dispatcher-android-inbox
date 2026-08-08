package ru.bruce.dispatcherinbox.ui.capture

import kotlin.math.sqrt

private const val SILENCE_THRESHOLD = 0.035f

/** Converts MediaRecorder's amplitude into a gated, voice-friendly visual level. */
internal fun recordingVisualLevel(rawAmplitude: Int): Float {
    val normalized = rawAmplitude.coerceIn(0, 32_767) / 32_767f
    if (normalized <= SILENCE_THRESHOLD) return 0f

    val aboveGate = (normalized - SILENCE_THRESHOLD) / (1f - SILENCE_THRESHOLD)
    return (sqrt(aboveGate) * 1.18f).coerceIn(0f, 1f)
}
