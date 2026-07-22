package ru.bruce.dispatcherinbox

import org.junit.Assert.assertEquals
import org.junit.Test

class AppEntryPolicyTest {
    @Test
    fun `queue and settings can always resolve back to capture`() {
        assertEquals(AppDestination.CAPTURE, AppEntryPolicy.destination("capture"))
        assertEquals(AppDestination.QUEUE, AppEntryPolicy.destination("queue"))
        assertEquals(AppDestination.SETTINGS, AppEntryPolicy.destination("settings"))
    }

    @Test
    fun `assistant entry ignores previously selected tab`() {
        assertEquals(AppDestination.CAPTURE, AppEntryPolicy.destination("settings", assistantEntry = true))
        assertEquals(AppDestination.CAPTURE, AppEntryPolicy.destination("queue", assistantEntry = true))
    }
}
