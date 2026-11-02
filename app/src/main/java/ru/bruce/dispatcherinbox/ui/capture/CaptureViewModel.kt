package ru.bruce.dispatcherinbox.ui.capture

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.bruce.dispatcherinbox.data.AttachmentDraft
import ru.bruce.dispatcherinbox.data.InboxRepository
import ru.bruce.dispatcherinbox.data.InboxSource
import ru.bruce.dispatcherinbox.data.InboxStatus
import ru.bruce.dispatcherinbox.data.InboxItemWithAttachments
import ru.bruce.dispatcherinbox.data.SettingsRepository
import ru.bruce.dispatcherinbox.util.AudioFeedbackManager
import ru.bruce.dispatcherinbox.util.VoiceRecorder

data class CaptureUiState(
    val text: String = "",
    val attachments: List<AttachmentDraft> = emptyList(),
    val isRecording: Boolean = false,
    val recordingSeconds: Long = 0,
    val driveConnected: Boolean = false,
    val assistantOpenMode: String? = null,
    val autoStartAssistantRecording: Boolean = false,
    val recordingAmplitude: Float = 0f,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val defaultOpenMode: String? = null,
    val recentItems: List<InboxItemWithAttachments> = emptyList(),
)

class CaptureViewModel(
    application: Application,
    private val repository: InboxRepository,
    settingsRepository: SettingsRepository,
    private val source: String,
) : AndroidViewModel(application) {
    private val recorder = VoiceRecorder(application)
    private val audioFeedback = AudioFeedbackManager()
    private val localState = MutableStateFlow(CaptureUiState())
    private val closeEvents = Channel<Unit>(Channel.BUFFERED)
    private var amplitudeJob: kotlinx.coroutines.Job? = null
    private var closeAfterSave = false
    private var lastSavedId: String? = null

    val close = closeEvents.receiveAsFlow()

    val state: StateFlow<CaptureUiState> = combine(
        localState,
        settingsRepository.settings,
        repository.items,
    ) { local, settings, items ->
        closeAfterSave = settings.closeAfterSave
        val saved = lastSavedId?.let { id -> items.firstOrNull { it.item.id == id } }
        val waitingForDrive = settings.driveAccount.isBlank() && saved != null && saved.item.status != InboxStatus.SYNCED
        val syncMessage = if (waitingForDrive) "На телефоне · Ждёт Drive" else when (saved?.item?.status) {
            InboxStatus.SYNCING -> "Отправляется"
            InboxStatus.SYNCED -> "Отправлено"
            InboxStatus.ERROR -> saved.item.lastError ?: "Ошибка отправки"
            InboxStatus.QUEUED -> "В очереди"
            else -> local.statusMessage
        }
        local.copy(
            statusMessage = syncMessage,
            isError = (!waitingForDrive && saved?.item?.status == InboxStatus.ERROR) || local.isError,
            driveConnected = settings.driveAccount.isNotBlank(),
            assistantOpenMode = settings.assistantOpenMode,
            autoStartAssistantRecording = settings.autoStartAssistantRecording,
            defaultOpenMode = settings.defaultOpenMode,
            recentItems = items.take(4),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CaptureUiState(),
    )

    fun setText(value: String) {
        if (value != localState.value.text) lastSavedId = null
        localState.value = localState.value.copy(text = value, statusMessage = null, isError = false)
    }

    fun addAttachment(uri: Uri) {
        addAttachments(listOf(uri))
    }

    fun addAttachments(uris: List<Uri>) {
        val selectedUris = uris.distinct()
        if (selectedUris.isEmpty()) return
        lastSavedId = null
        viewModelScope.launch {
            localState.value = localState.value.copy(
                isBusy = true,
                statusMessage = if (selectedUris.size == 1) "Копируем файл…"
                else "Копируем файлов: ${selectedUris.size}…",
                isError = false,
            )
            val added = mutableListOf<AttachmentDraft>()
            var failedCount = 0
            selectedUris.forEach { uri ->
                runCatching { repository.stage(uri) }
                    .onSuccess(added::add)
                    .onFailure { failedCount += 1 }
            }
            localState.value = localState.value.copy(
                attachments = localState.value.attachments + added,
                isBusy = false,
                statusMessage = when {
                    failedCount == 0 && added.size == 1 -> "Файл добавлен"
                    failedCount == 0 -> "Добавлено файлов: ${added.size}"
                    added.isNotEmpty() -> "Добавлено: ${added.size}, ошибок: $failedCount"
                    else -> "Не удалось добавить выбранные файлы"
                },
                isError = failedCount > 0,
            )
        }
    }

    fun removeAttachment(draft: AttachmentDraft) {
        viewModelScope.launch {
            repository.discardDraft(draft)
            localState.value = localState.value.copy(
                attachments = localState.value.attachments - draft,
                statusMessage = null,
            )
        }
    }

    fun startRecording() {
        if (localState.value.isRecording || localState.value.isBusy) return
        lastSavedId = null
        runCatching {
            recorder.start(repository.newVoiceFile())
            audioFeedback.playStartSound()
            localState.value = localState.value.copy(
                isRecording = true,
                recordingAmplitude = 0f,
                recordingSeconds = 0,
                statusMessage = "Идёт запись",
                isError = false,
            )

            val started = android.os.SystemClock.elapsedRealtime()
            amplitudeJob?.cancel()
            amplitudeJob = viewModelScope.launch {
                while (localState.value.isRecording) {
                    val raw = recorder.currentAmplitude().coerceIn(0, 32767)
                    val level = (kotlin.math.ln(1.0 + raw) / kotlin.math.ln(32768.0)).toFloat()
                    localState.value = localState.value.copy(
                        recordingAmplitude = localState.value.recordingAmplitude * 0.65f + level * 0.35f,
                        recordingSeconds = (android.os.SystemClock.elapsedRealtime() - started) / 1000,
                    )
                    kotlinx.coroutines.delay(100)
                }
            }
        }.onFailure {
            audioFeedback.playErrorSound()
            showError("Не удалось начать запись")
        }
    }

    fun stopRecordingAndSave() {
        if (!localState.value.isRecording) return
        amplitudeJob?.cancel()
        localState.value = localState.value.copy(
            isRecording = false,
            recordingAmplitude = 0f,
            isBusy = true,
        )
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { recorder.stop() }
                audioFeedback.playStopSound()
                val voice = repository.voiceDraft(file)
                saveInternal(localState.value.attachments + voice)
            } catch (error: Exception) {
                audioFeedback.playErrorSound()
                showError(error.message ?: "Не удалось сохранить запись")
            }
        }
    }

    fun onAudioPermissionDenied() {
        audioFeedback.playErrorSound()
        showError("Без доступа к микрофону запись голоса невозможна")
    }

    fun save() {
        if (localState.value.isBusy || localState.value.isRecording) return
        viewModelScope.launch { saveInternal(localState.value.attachments) }
    }

    private suspend fun saveInternal(drafts: List<AttachmentDraft>) {
        val text = localState.value.text
        if (text.isBlank() && drafts.isEmpty()) {
            showError("Введите текст или добавьте файл")
            return
        }
        localState.value = localState.value.copy(attachments = drafts, isBusy = true, statusMessage = "Сохраняем…")
        runCatching { repository.save(text, drafts, source) }
            .onSuccess { item ->
                lastSavedId = item.id
                localState.value = CaptureUiState(
                    statusMessage = "В очереди",
                    defaultOpenMode = localState.value.defaultOpenMode,
                )
                if (closeAfterSave) closeEvents.trySend(Unit)
            }
            .onFailure { showError(it.message ?: "Не удалось сохранить запись") }
    }

    private fun showError(message: String) {
        amplitudeJob?.cancel()
        localState.value = localState.value.copy(
            isBusy = false,
            isRecording = false,
            recordingAmplitude = 0f,
            statusMessage = message,
            isError = true,
        )
    }

    override fun onCleared() {
        amplitudeJob?.cancel()
        if (localState.value.isRecording) recorder.cancel()
        super.onCleared()
    }

    class Factory(
        private val application: Application,
        private val repository: InboxRepository,
        private val settingsRepository: SettingsRepository,
        private val source: String = InboxSource.MANUAL,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CaptureViewModel(application, repository, settingsRepository, source) as T
    }
}
