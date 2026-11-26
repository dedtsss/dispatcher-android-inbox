package ru.bruce.dispatcherinbox.ui.capture

import kotlin.math.log10
import kotlin.math.pow

/** История пиков MediaRecorder, не PCM-осциллограмма, спектр или измеритель dB SPL. */
internal class RecordingLevelMeter {
    private val recentDb = ArrayDeque<Double>()
    private val history = ArrayDeque<Float>()
    private var noiseFloorDb = -60.0
    private var envelope = 0f

    fun reset(): RecordingLevel {
        recentDb.clear()
        history.clear()
        repeat(HISTORY_SIZE) { history.addLast(0f) }
        noiseFloorDb = -60.0
        envelope = 0f
        return RecordingLevel(0f, history.toList())
    }

    init { reset() }

    fun sample(rawPeak: Int): RecordingLevel {
        val peak = rawPeak.coerceIn(0, MAX_PEAK)
        val db = if (peak == 0) -96.0 else 20.0 * log10(peak.toDouble() / MAX_PEAK)
        recentDb.addLast(db)
        if (recentDb.size > NOISE_WINDOW) recentDb.removeFirst()
        // Нижняя пятая часть окна оценивает фон; диапазон адаптации ограничен 6 dB.
        // Это консервативный визуальный порог, а не распознавание речи/шумоподавление аудио.
        if (recentDb.size >= 12) {
            val quiet = recentDb.sorted()[(recentDb.size - 1) / 5].coerceIn(-60.0, -54.0)
            noiseFloorDb += (quiet - noiseFloorDb) * if (quiet < noiseFloorDb) 0.25 else 0.08
        }
        val gateDb = noiseFloorDb + 6.0 // -54..-48 dB относительно условного full scale 32767.
        val target = ((db - gateDb) / -gateDb).coerceIn(0.0, 1.0).pow(0.75).toFloat()
        val coefficient = if (target > envelope) 0.85f else 0.45f
        envelope += (target - envelope) * coefficient
        if (target == 0f && envelope < 0.02f) envelope = 0f
        history.removeFirst()
        history.addLast(envelope)
        return RecordingLevel(envelope, history.toList())
    }

    companion object {
        const val SAMPLE_INTERVAL_MS = 80L
        const val HISTORY_SIZE = 32
        const val MAX_PEAK = 32767
        private const val NOISE_WINDOW = 50
        fun emptyHistory(): List<Float> = List(HISTORY_SIZE) { 0f }
    }
}

internal data class RecordingLevel(val level: Float, val history: List<Float>)
