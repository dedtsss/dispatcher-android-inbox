package ru.bruce.dispatcherinbox.ui.capture

import org.junit.Assert.*
import org.junit.Test

class RecordingLevelMeterTest {
    @Test fun silenceLowMidLoudAndPauseRemainDistinct() {
        val meter = RecordingLevelMeter()
        repeat(8) { assertEquals(0f, meter.sample(0).level, 0f) }
        fun hold(raw: Int): Float { var level = 0f; repeat(8) { level = meter.sample(raw).level }; return level }
        val low = hold(256)
        val mid = hold(2048)
        val loud = hold(16000)
        assertTrue("Тихий голос должен быть видим", low in 0.15f..0.4f)
        assertTrue(mid > low + 0.2f && mid < 0.8f)
        assertTrue(loud > mid + 0.15f && loud < 0.99f)
        assertEquals(0f, hold(0), 0f)
        repeat(RecordingLevelMeter.HISTORY_SIZE) { meter.sample(0) }
        assertTrue(meter.sample(0).history.all { it == 0f })
    }

    @Test fun boundedNoiseFloorSuppressesBackgroundButNotSpeechAboveIt() {
        val meter = RecordingLevelMeter()
        val background = listOf(70, 100, 120, 90, 128, 80)
        var frame = meter.reset()
        repeat(200) { frame = meter.sample(background[it % background.size]) }
        assertTrue("Стабильный слабый фон не должен рисовать голос", frame.history.all { it < 0.02f })
        repeat(8) { frame = meter.sample(400) }
        assertTrue("Голос выше фона должен остаться видим", frame.level > 0.2f)
        repeat(32) { frame = meter.sample(2048) }
        assertTrue("Средний уровень не должен насытиться", frame.level < 0.8f)
    }

    @Test fun fastAttackShortReleaseAndRealHistory() {
        val meter = RecordingLevelMeter()
        val loud = meter.sample(16000)
        assertTrue("Первый пик должен сразу появиться", loud.level > 0.7f)
        assertEquals(1, loud.history.count { it > 0f })
        val next = meter.sample(0)
        assertEquals(loud.history.drop(1), next.history.dropLast(1))
        assertTrue(next.level < loud.level && next.level > 0f)
        var paused = next
        repeat(7) { paused = meter.sample(0) }
        assertEquals(0f, paused.level, 0f)
        assertTrue("Предыдущий реальный пик остаётся в истории", paused.history.any { it > 0f })
    }

    @Test fun restartClearsEnvelopeNoiseEstimateAndHistory() {
        val meter = RecordingLevelMeter()
        repeat(100) { meter.sample(16000) }
        assertEquals(RecordingLevel(0f, List(32) { 0f }), meter.reset())
        val fresh = RecordingLevelMeter()
        listOf(0, 20, 256, 2048, 16000, 0, 0, 400).forEach {
            assertEquals("Повторный запуск не наследует прошлую запись", fresh.sample(it), meter.sample(it))
        }
    }

    @Test fun clampsInvalidPeaksWithoutNaNOrInstantSaturation() {
        val meter = RecordingLevelMeter()
        assertEquals(0f, meter.sample(-1).level, 0f)
        val ordinary = meter.sample(2048).level
        assertTrue(ordinary in 0.3f..0.7f)
        var maximum = 0f
        repeat(20) { maximum = meter.sample(Int.MAX_VALUE).level }
        assertTrue(maximum > 0.999f && maximum <= 1f)
        repeat(100) {
            val frame = meter.sample(if (it % 2 == 0) Int.MIN_VALUE else Int.MAX_VALUE)
            assertEquals(32, frame.history.size)
            assertTrue(frame.history.all { value -> value.isFinite() && value in 0f..1f })
        }
    }
}
