package ru.bruce.dispatcherinbox.ui.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.bruce.dispatcherinbox.data.DefaultOpenMode

class CaptureLaunchPolicyTest {
    @Test
    fun `launcher never auto starts recording`() {
        assertFalse(
            CaptureLaunchPolicy.shouldAutoStartRecording(
                isAssistantMode = false,
                defaultOpenMode = DefaultOpenMode.VOICE,
                autoStartAssistantRecording = true,
            ),
        )
    }

    @Test
    fun `assistant requires voice mode and enabled auto start`() {
        assertTrue(
            CaptureLaunchPolicy.shouldAutoStartRecording(
                isAssistantMode = true,
                defaultOpenMode = DefaultOpenMode.VOICE,
                autoStartAssistantRecording = true,
            ),
        )
        assertFalse(
            CaptureLaunchPolicy.shouldAutoStartRecording(
                isAssistantMode = true,
                defaultOpenMode = DefaultOpenMode.VOICE,
                autoStartAssistantRecording = false,
            ),
        )
        assertFalse(
            CaptureLaunchPolicy.shouldAutoStartRecording(
                isAssistantMode = true,
                defaultOpenMode = DefaultOpenMode.TEXT,
                autoStartAssistantRecording = true,
            ),
        )
    }
}
