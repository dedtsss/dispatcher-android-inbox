package ru.bruce.dispatcherinbox.ui

import org.junit.Assert.*
import org.junit.Test
import ru.bruce.dispatcherinbox.ui.capture.CaptureEntryPolicy
import ru.bruce.dispatcherinbox.drive.AuthorizationDiagnostics
import ru.bruce.dispatcherinbox.data.InboxStatus

class EntryAndDeliveryTest {
    @Test fun launcherNeverRecordsRegardlessOfAssistantPreferences() {
        for (mode in listOf("text", "voice")) for (auto in listOf(false, true)) for (force in listOf(false, true)) {
            assertFalse(CaptureEntryPolicy.autoRecord(false, mode, auto, force))
        }
    }
    @Test fun assistantTextOverridesForcedVoiceAndAutoFlag() {
        assertEquals("text", CaptureEntryPolicy.mode(true, "voice", "text", true))
        assertFalse(CaptureEntryPolicy.autoRecord(true, "text", true, true))
    }
    @Test fun assistantVoiceWaitsUnlessExplicitlyEnabled() {
        assertFalse(CaptureEntryPolicy.autoRecord(true, "voice", false, true))
        assertTrue(CaptureEntryPolicy.autoRecord(true, "voice", true, false))
    }
    @Test fun localSavedErrorsAreNeutralUntilDestinationConnected() {
        for (status in listOf(InboxStatus.QUEUED, InboxStatus.ERROR, InboxStatus.SYNCING)) {
            val local = deliveryStatus(status, false)
            assertFalse(local.isError)
            assertTrue(local.waitingForDrive)
        }
        assertTrue(deliveryStatus(InboxStatus.ERROR, true).isError)
        assertFalse(deliveryStatus(InboxStatus.QUEUED, true).isError)
        assertEquals("Отправлено", deliveryStatus(InboxStatus.SYNCED, false).label)
    }
    @Test fun unknownResultIsNeverReportedAsCancellationOrMissingClient() {
        assertFalse(AuthorizationDiagnostics.message(null).contains("отмен", ignoreCase = true))
        assertTrue(AuthorizationDiagnostics.message(16).contains("отмен"))
        assertTrue(AuthorizationDiagnostics.message(10).contains("SHA-1"))
        assertTrue(AuthorizationDiagnostics.message(null, 403, "accessNotConfigured").contains("Drive API"))
    }
    @Test fun diagnosticDoesNotExposeAccountOrOpaqueSecrets() {
        val result = AuthorizationDiagnostics.safeMessage("user@example.test " + "secret".repeat(20))
        assertFalse(result.contains("user@example.test"))
        assertFalse(result.contains("secret"))
    }
}
