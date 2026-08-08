package ru.bruce.dispatcherinbox.ui.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingAmplitudeTest {
    @Test
    fun `silence is rendered as a flat level`() {
        assertEquals(0f, recordingVisualLevel(0), 0f)
        assertEquals(0f, recordingVisualLevel(1_000), 0f)
    }

    @Test
    fun `normal speech is visible and loud speech produces larger peaks`() {
        val normalSpeech = recordingVisualLevel(4_000)
        val loudSpeech = recordingVisualLevel(16_000)

        assertTrue(normalSpeech > 0.2f)
        assertTrue(loudSpeech > normalSpeech)
    }
}
