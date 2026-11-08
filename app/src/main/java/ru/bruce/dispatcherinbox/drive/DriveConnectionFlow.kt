package ru.bruce.dispatcherinbox.drive

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive

internal enum class ConnectionPhase { PICK_ACCOUNT, AUTHORIZE, CONSENT, VERIFY, CONNECTED, CANCELED, ERROR, RESUME }
internal data class ConnectionUiState(val phase: ConnectionPhase, val message: String, val externalLaunched: Boolean = false)
internal class DriveGrant(val scopes: List<String>, val token: String?, val resolution: PendingIntent?) {
    override fun toString() = "DriveGrant(данные скрыты)"
}
internal interface DriveConnectionBackend {
    suspend fun authorize(account: String): DriveGrant
    fun parse(data: Intent?): DriveGrant
    suspend fun verifyAndConnect(account: String, token: String)
    suspend fun record(stage: String, details: String)
    suspend fun status(message: String) {}
    fun errorMessage(error: Exception): String
}

// Сохраняются только этап, выбранный аккаунт и признак запущенного окна, никогда токены/Intent.
internal class DriveConnectionFlow(
    private val saved: SavedStateHandle,
    private val scope: CoroutineScope,
    private val backend: DriveConnectionBackend,
) {
    private var work: Job? = null
    private var revision = 0
    var pendingConsent: PendingIntent? = null
        private set
    private var account: String
        get() = saved["account"] ?: ""
        set(value) { saved["account"] = value }
    private val restored = saved.get<String>("phase")?.let { runCatching { ConnectionPhase.valueOf(it) }.getOrNull() }
    private val initial = when (restored) {
        ConnectionPhase.AUTHORIZE, ConnectionPhase.VERIFY -> ConnectionPhase.RESUME
        else -> restored ?: ConnectionPhase.PICK_ACCOUNT
    }
    private val mutable = MutableStateFlow(ConnectionUiState(initial, message(initial), saved["external"] ?: false))
    val state = mutable.asStateFlow()

    private fun transition(phase: ConnectionPhase, text: String = message(phase), external: Boolean = false) {
        saved["phase"] = phase.name
        saved["external"] = external
        mutable.value = ConnectionUiState(phase, text, external)
        scope.launch { backend.status(text) }
    }
    fun externalStarted() { transition(state.value.phase, state.value.message, true) }
    fun accountResult(name: String?, type: String?, canceled: Boolean) {
        if (state.value.phase != ConnectionPhase.PICK_ACCOUNT) return
        if (canceled) { cancel(); return }
        if (name.isNullOrBlank() || type != "com.google") {
            transition(ConnectionPhase.ERROR, "Не удалось выбрать Google-аккаунт. Повторите подключение")
            return
        }
        account = name
        authorize()
    }
    fun retry() {
        work?.cancel(); revision++; pendingConsent = null
        account = ""
        transition(ConnectionPhase.PICK_ACCOUNT)
    }
    fun resume() {
        if (state.value.phase !in setOf(ConnectionPhase.RESUME, ConnectionPhase.CONSENT, ConnectionPhase.PICK_ACCOUNT)) return
        if (account.isBlank()) retry() else authorize()
    }
    fun cancel() {
        work?.cancel(); revision++; pendingConsent = null
        transition(ConnectionPhase.CANCELED)
    }
    fun launchFailed(error: Exception) { fail(error, state.value.phase.name.lowercase()) }
    private fun authorize() {
        pendingConsent = null
        transition(ConnectionPhase.AUTHORIZE)
        execute("authorize") { accept(backend.authorize(account)) }
    }
    fun consentResult(resultCode: Int, data: Intent?) {
        if (state.value.phase != ConnectionPhase.CONSENT) return
        pendingConsent = null
        // Отмена без payload отличается от ошибки SDK в непустом Intent, даже при resultCode=0.
        if (resultCode == 0 && data == null) { cancel(); return }
        transition(ConnectionPhase.AUTHORIZE, "Проверяем ответ Google…")
        execute("resolution") {
            backend.record("resolution", "resultCode=$resultCode")
            accept(backend.parse(data))
        }
    }
    private suspend fun accept(grant: DriveGrant) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        backend.record("authorization_result", "hasResolution=${grant.resolution != null}; hasAccessToken=${!grant.token.isNullOrBlank()}; driveFileGranted=${DriveAuthorization.DRIVE_FILE in grant.scopes}")
        if (grant.resolution != null) {
            pendingConsent = grant.resolution
            transition(ConnectionPhase.CONSENT)
            return
        }
        check(DriveAuthorization.DRIVE_FILE in grant.scopes && !grant.token.isNullOrBlank())
        transition(ConnectionPhase.VERIFY)
        backend.verifyAndConnect(account, grant.token!!)
        transition(ConnectionPhase.CONNECTED)
    }
    private fun execute(stage: String, action: suspend () -> Unit) {
        val attempt = ++revision
        work = scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (attempt == revision) fail(e, stage) }
        }
    }
    private fun fail(error: Exception, stage: String) {
        pendingConsent = null
        val actualStage = if (state.value.phase == ConnectionPhase.VERIFY) "drive_verify" else stage
        val canceled = (error as? com.google.android.gms.common.api.ApiException)?.statusCode == 16
        transition(if (canceled) ConnectionPhase.CANCELED else ConnectionPhase.ERROR, if (canceled) message(ConnectionPhase.CANCELED) else backend.errorMessage(error))
        scope.launch {
            // Только типы и числовые коды; произвольные сообщения провайдера могут содержать секреты.
            backend.record(actualStage, "exception=${error.javaClass.simpleName}; apiStatus=${(error as? com.google.android.gms.common.api.ApiException)?.statusCode}; httpStatus=${(error as? DriveHttpException)?.code}")
        }
    }
    companion object {
        private fun message(phase: ConnectionPhase) = when (phase) {
            ConnectionPhase.PICK_ACCOUNT -> "Выберите Google-аккаунт для сохранения оригиналов"
            ConnectionPhase.AUTHORIZE -> "Запрашиваем разрешение Google Drive…"
            ConnectionPhase.CONSENT -> "Подтвердите доступ в окне Google. После возвращения подключение продолжится"
            ConnectionPhase.VERIFY -> "Проверяем доступ и папку Catching/Inbox…"
            ConnectionPhase.CONNECTED -> "Google Drive подключён. Сохранённые записи поставлены на отправку"
            ConnectionPhase.CANCELED -> "Подключение отменено. Ваши записи остались на телефоне"
            ConnectionPhase.ERROR -> "Не удалось подключить Google Drive. Записи остались на телефоне"
            ConnectionPhase.RESUME -> "Подключение было прервано. Продолжите проверку доступа"
        }
    }
}
