package ru.bruce.dispatcherinbox.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsDefaultsTest {
    @Test
    fun `settings default to text mode without assistant auto recording`() {
        val settings = AppSettings()

        assertEquals(DefaultOpenMode.TEXT, settings.defaultOpenMode)
        assertFalse(settings.autoStartAssistantRecording)
    }

    @Test
    fun `unknown default mode is normalized to text`() {
        assertEquals(DefaultOpenMode.TEXT, DefaultOpenMode.normalize("unknown"))
        assertEquals(DefaultOpenMode.TEXT, DefaultOpenMode.normalize(null))
        assertEquals(DefaultOpenMode.VOICE, DefaultOpenMode.normalize(DefaultOpenMode.VOICE))
    }
}
