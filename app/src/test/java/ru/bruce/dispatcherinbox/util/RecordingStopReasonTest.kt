package ru.bruce.dispatcherinbox.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingStopReasonTest {
    @Test
    fun `cancel and error create no queue item`() {
        assertFalse(RecordingStopReason.CANCEL.shouldSave)
        assertFalse(RecordingStopReason.ERROR.shouldSave)
    }

    @Test
    fun `send and timeout save queue item`() {
        assertTrue(RecordingStopReason.SEND.shouldSave)
        assertTrue(RecordingStopReason.TIMEOUT.shouldSave)
    }
}
