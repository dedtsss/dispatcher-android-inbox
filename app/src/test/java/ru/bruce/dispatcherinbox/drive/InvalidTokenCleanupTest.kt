package ru.bruce.dispatcherinbox.drive

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class InvalidTokenCleanupTest {
    @Test fun unauthorizedClearsOnlyUsedTokenAndKeepsOriginalFailure() = runTest {
        val original = DriveHttpException(401, "invalid_token")
        val cleared = mutableListOf<String>()
        try { withInvalidTokenCleanup("synthetic-access", { cleared += it }) { throw original } }
        catch (e: DriveHttpException) { assertSame(original, e) }
        assertEquals(listOf("synthetic-access"), cleared)
    }
    @Test fun forbiddenDoesNotClearOrRevoke() = runTest {
        var calls = 0
        val original = DriveHttpException(403, "accessNotConfigured")
        try { withInvalidTokenCleanup("synthetic-access", { calls++ }) { throw original } }
        catch (e: DriveHttpException) { assertSame(original, e) }
        assertEquals(0, calls)
    }
    @Test fun cleanupFailureDoesNotHideUnauthorized() = runTest {
        val original = DriveHttpException(401, "invalid_token")
        try { withInvalidTokenCleanup("synthetic-access", { error("synthetic failure") }) { throw original } }
        catch (e: DriveHttpException) { assertSame(original, e) }
    }
    @Test fun cleanupCancellationIsPropagated() = runTest {
        val canceled = CancellationException("synthetic cancellation")
        try { withInvalidTokenCleanup("synthetic-access", { throw canceled }) { throw DriveHttpException(401, "invalid_token") }; fail("Отмена потеряна") }
        catch (e: CancellationException) { assertSame(canceled, e) }
    }
}
