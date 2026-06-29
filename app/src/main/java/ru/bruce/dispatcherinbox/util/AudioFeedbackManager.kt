package ru.bruce.dispatcherinbox.util

import android.media.AudioManager
import android.media.ToneGenerator

class AudioFeedbackManager {
    fun playStartSound() = play(ToneGenerator.TONE_PROP_BEEP, 90)
    fun playStopSound() = play(ToneGenerator.TONE_PROP_ACK, 120)
    fun playErrorSound() = play(ToneGenerator.TONE_PROP_NACK, 160)

    private fun play(tone: Int, durationMs: Int) {
        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 32).also { generator ->
                generator.startTone(tone, durationMs)
                Thread {
                    try {
                        Thread.sleep((durationMs + 80).toLong())
                    } finally {
                        generator.release()
                    }
                }.start()
            }
        }
    }
}
