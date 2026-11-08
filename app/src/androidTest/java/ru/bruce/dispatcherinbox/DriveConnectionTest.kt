package ru.bruce.dispatcherinbox

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import ru.bruce.dispatcherinbox.drive.*

// Протокольные фикстуры; ни один тест не объявляет реальную авторизацию Google успешной.
class DriveConnectionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun <T> main(block: () -> T): T {
        var value: T? = null
        instrumentation.runOnMainSync { value = block() }
        @Suppress("UNCHECKED_CAST") return value as T
    }
    private fun consent(): PendingIntent = PendingIntent.getActivity(instrumentation.targetContext, 72,
        Intent(instrumentation.targetContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun granted() = DriveGrant(listOf(DriveAuthorization.DRIVE_FILE), "synthetic-access", null)
    private class Backend : DriveConnectionBackend {
        var requested = ""
        var authorized = 0
        var verified = 0
        var parsed = 0
        var answer: suspend () -> DriveGrant = { DriveGrant(listOf(DriveAuthorization.DRIVE_FILE), "synthetic-access", null) }
        var decoded: () -> DriveGrant = { DriveGrant(listOf(DriveAuthorization.DRIVE_FILE), "synthetic-access", null) }
        val diagnostics = mutableListOf<String>()
        override suspend fun authorize(account: String): DriveGrant { requested = account; authorized++; return answer() }
        override fun parse(data: Intent?): DriveGrant { parsed++; return decoded() }
        override suspend fun verifyAndConnect(account: String, token: String) { assertEquals("synthetic-access", token); verified++ }
        override suspend fun record(stage: String, details: String) { diagnostics += "$stage: $details" }
        override fun errorMessage(error: Exception) = AuthorizationDiagnostics.message((error as? ApiException)?.statusCode)
    }
    private suspend fun await(flow: DriveConnectionFlow, phase: ConnectionPhase) {
        withTimeout(5000) { while (flow.state.value.phase != phase) delay(10) }
    }
    private suspend fun fixture(block: suspend (DriveConnectionFlow, Backend, SavedStateHandle, CoroutineScope) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val saved = SavedStateHandle()
        val backend = Backend()
        val flow = main { DriveConnectionFlow(saved, scope, backend) }
        try { block(flow, backend, saved, scope) } finally { scope.cancel() }
    }
    @Test fun selectedAccountAndActualGrantRequiredBeforeDrive() = runBlocking {
        fixture { flow, backend, saved, _ ->
            main { flow.externalStarted() }
            assertEquals(0, backend.authorized)
            main { flow.accountResult("person@example.test", "com.google", false) }
            await(flow, ConnectionPhase.CONNECTED)
            assertEquals("person@example.test", backend.requested)
            assertEquals(1, backend.verified)
            assertEquals(setOf("phase", "account", "external"), saved.keys())
            assertFalse(saved.keys().any { saved.get<Any>(it).toString().contains("synthetic-access") })
            assertFalse(backend.diagnostics.joinToString().contains("person@example.test"))
            assertFalse(backend.diagnostics.joinToString().contains("synthetic-access"))
            main { flow.consentResult(-1, Intent()) }
            assertEquals(1, backend.verified)
        }
    }
    @Test fun missingScopeOrTokenCannotReachDrive() = runBlocking {
        for (answer in listOf(DriveGrant(emptyList(), "synthetic-access", null), DriveGrant(listOf(DriveAuthorization.DRIVE_FILE), null, null))) {
            fixture { flow, backend, _, _ ->
                backend.answer = { answer }
                main { flow.accountResult("person@example.test", "com.google", false) }
                await(flow, ConnectionPhase.ERROR)
                assertEquals(0, backend.verified)
            }
        }
    }
    @Test fun resolutionErrorEightIsNotReportedAsCancellation() = runBlocking {
        fixture { flow, backend, _, _ ->
            backend.answer = { DriveGrant(emptyList(), null, consent()) }
            backend.decoded = { throw ApiException(Status(8)) }
            main { flow.accountResult("person@example.test", "com.google", false) }
            await(flow, ConnectionPhase.CONSENT)
            main { flow.externalStarted(); flow.consentResult(0, Intent()) }
            await(flow, ConnectionPhase.ERROR)
            assertEquals(1, backend.parsed)
            assertEquals(0, backend.verified)
            assertFalse(flow.state.value.message.contains("отмен"))
        }
    }
    @Test fun restoredConsentConsumesResultOnceWithoutNewAuthorization() = runBlocking {
        fixture { flow, backend, saved, scope ->
            backend.answer = { DriveGrant(emptyList(), null, consent()) }
            main { flow.accountResult("person@example.test", "com.google", false) }
            await(flow, ConnectionPhase.CONSENT)
            main { flow.externalStarted() }
            val fresh = Backend()
            val restored = main { DriveConnectionFlow(saved, scope, fresh) }
            assertTrue(restored.state.value.externalLaunched)
            assertNull(restored.pendingConsent)
            main { restored.consentResult(-1, Intent()) }
            await(restored, ConnectionPhase.CONNECTED)
            main { restored.consentResult(-1, Intent()) }
            assertEquals(0, fresh.authorized)
            assertEquals(1, fresh.verified)
        }
    }
    @Test fun interruptedNetworkPhasesRequireFreshTokenOnResume() = runBlocking {
        for (phase in listOf(ConnectionPhase.AUTHORIZE, ConnectionPhase.VERIFY)) {
            fixture { _, backend, _, scope ->
                val restored = main { DriveConnectionFlow(SavedStateHandle(mapOf("phase" to phase.name, "account" to "person@example.test")), scope, backend) }
                assertEquals(ConnectionPhase.RESUME, restored.state.value.phase)
                assertEquals(0, backend.authorized)
                main { restored.resume() }
                await(restored, ConnectionPhase.CONNECTED)
                assertEquals(1, backend.authorized)
            }
        }
    }
    @Test fun cancellationAndInvalidAccountKeepDriveUntouched() = runBlocking {
        fixture { flow, backend, _, _ ->
            main { flow.accountResult(null, null, true) }
            assertEquals(ConnectionPhase.CANCELED, flow.state.value.phase)
            main { flow.retry(); flow.accountResult("person@example.test", "other", false) }
            assertEquals(ConnectionPhase.ERROR, flow.state.value.phase)
            assertEquals(0, backend.authorized)
            assertEquals(0, backend.verified)
        }
    }
    @Test fun canceledConsentWithoutPayloadAndLateRepliesAreIgnored() = runBlocking {
        fixture { flow, backend, _, _ ->
            backend.answer = { DriveGrant(emptyList(), null, consent()) }
            main { flow.accountResult("person@example.test", "com.google", false) }
            await(flow, ConnectionPhase.CONSENT)
            main { flow.consentResult(0, null); flow.consentResult(-1, Intent()) }
            assertEquals(ConnectionPhase.CANCELED, flow.state.value.phase)
            assertEquals(0, backend.parsed)
            assertEquals(0, backend.verified)
        }
    }
    @Test fun canceledInFlightAuthorizationCannotCommitLateSuccess() = runBlocking {
        fixture { flow, backend, _, _ ->
            val gate = CompletableDeferred<DriveGrant>()
            backend.answer = { gate.await() }
            main { flow.accountResult("person@example.test", "com.google", false); flow.cancel() }
            gate.complete(granted())
            delay(100)
            assertEquals(ConnectionPhase.CANCELED, flow.state.value.phase)
            assertEquals(0, backend.verified)
        }
    }
}
