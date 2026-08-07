package ru.bruce.dispatcherinbox.ui.capture

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
import ru.bruce.dispatcherinbox.recording.RecordingForegroundService
import ru.bruce.dispatcherinbox.util.AudioFeedbackManager
import ru.bruce.dispatcherinbox.util.RecordingSafetyTimer
import ru.bruce.dispatcherinbox.util.RecordingStopReason
import ru.bruce.dispatcherinbox.util.VoiceRecorder

data class CaptureUiState(
    val text: String = "",
    val attachments: List<AttachmentDraft> = emptyList(),
    val hasInitialContent: Boolean = false,
    val voiceDurationMillis: Long? = null,
    val isRecording: Boolean = false,
    val recordingAmplitude: Float = 0f,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val defaultOpenMode: String? = null,
    val autoStartAssistantRecording: Boolean = false,
    val recentItems: List<InboxItemWithAttachments> = emptyList(),
)

class CaptureViewModel(
    application: Application,
    private val repository: InboxRepository,
    settingsRepository: SettingsRepository,
    private val source: String,
    private val applicationScope: CoroutineScope,
    initialText: String? = null,
    initialUris: List<Uri> = emptyList(),
    private val closeOnSuccessfulSave: Boolean = false,
) : AndroidViewModel(application) {
    private val recorder = VoiceRecorder(application)
    private val audioFeedback = AudioFeedbackManager()
    private val localState = MutableStateFlow(
        CaptureUiState(
            text = initialText.orEmpty(),
            hasInitialContent = initialText?.isNotBlank() == true || initialUris.isNotEmpty(),
        ),
    )
    private val closeEvents = Channel<Unit>(Channel.BUFFERED)
    private var closeAfterSave = closeOnSuccessfulSave
    private var lastSavedId: String? = null
    private var amplitudeJob: Job? = null
    private val recordingSafetyTimer = RecordingSafetyTimer(
        scope = applicationScope,
        timeoutMillis = RECORDING_TIMEOUT_MILLIS,
        onTimeout = { finishRecording(RecordingStopReason.TIMEOUT) },
    )

    val close = closeEvents.receiveAsFlow()

    val state: StateFlow<CaptureUiState> = combine(
        localState,
        settingsRepository.settings,
        repository.items,
    ) { local, settings, items ->
        closeAfterSave = closeOnSuccessfulSave || settings.closeAfterSave
        val saved = lastSavedId?.let { id -> items.firstOrNull { it.item.id == id } }
        val syncMessage = when (saved?.item?.status) {
            InboxStatus.SYNCING -> "Отправляется"
            InboxStatus.SYNCED -> "Отправлено"
            InboxStatus.ERROR -> saved.item.lastError ?: "Ошибка отправки"
            InboxStatus.QUEUED -> "В очереди"
            else -> local.statusMessage
        }
        local.copy(
            statusMessage = syncMessage,
            isError = saved?.item?.status == InboxStatus.ERROR || local.isError,
            defaultOpenMode = settings.defaultOpenMode,
            autoStartAssistantRecording = settings.autoStartAssistantRecording,
            recentItems = items.take(4),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CaptureUiState(),
    )

    init {
        if (initialUris.isNotEmpty()) addAttachments(initialUris)
    }

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
            RecordingForegroundService.start(getApplication())
            recorder.start(repository.newVoiceFile()) { message ->
                applicationScope.launch { handleRecorderError(message) }
            }
            audioFeedback.playStartSound()
            localState.value = localState.value.copy(
                isRecording = true,
                recordingAmplitude = 0f,
                statusMessage = "Идет запись…",
                isError = false,
            )
            Log.i(TAG, "recording active; recorder actually active=${recorder.isActuallyActive()}")
            startAmplitudeUpdates()
            recordingSafetyTimer.recordingStarted()
        }.onFailure {
            RecordingForegroundService.stop(getApplication())
            audioFeedback.playErrorSound()
            showError("Не удалось начать запись")
        }
    }

    fun stopRecordingAndSave() {
        finishRecording(RecordingStopReason.SEND)
    }

    fun stopRecordingAndAdd() {
        finishRecording(RecordingStopReason.ADD_TO_DRAFT)
    }

    fun cancelRecording() {
        finishRecording(RecordingStopReason.CANCEL)
    }

    fun onHostActiveChanged(active: Boolean) {
        Log.i(TAG, "overlay ${if (active) "active" else "inactive"}")
        recordingSafetyTimer.setOverlayActive(active)
    }

    private fun finishRecording(reason: RecordingStopReason) {
        if (!localState.value.isRecording) return
        recordingSafetyTimer.recordingStopped()
        stopAmplitudeUpdates()
        if (!reason.shouldSave) {
            recorder.cancel()
            RecordingForegroundService.stop(getApplication())
            Log.i(TAG, "recording stopped without save: $reason")
            localState.value = localState.value.copy(
                isRecording = false,
                recordingAmplitude = 0f,
                isBusy = false,
                statusMessage = if (reason == RecordingStopReason.CANCEL) "Запись отменена" else "Ошибка записи",
                isError = reason == RecordingStopReason.ERROR,
            )
            return
        }
        val text = localState.value.text
        val drafts = localState.value.attachments
        localState.value = localState.value.copy(
            isRecording = false,
            recordingAmplitude = 0f,
            isBusy = true,
            statusMessage = if (reason == RecordingStopReason.TIMEOUT) {
                "Запись остановлена автоматически"
            } else {
                "Сохраняем…"
            },
        )
        applicationScope.launch {
            try {
                val recorded = withContext(Dispatchers.IO) { recorder.stop() }
                RecordingForegroundService.stop(getApplication())
                audioFeedback.playStopSound()
                val voice = repository.voiceDraft(recorded.file)
                if (reason == RecordingStopReason.ADD_TO_DRAFT) {
                    val previousDuration = localState.value.voiceDurationMillis ?: 0L
                    localState.value = localState.value.copy(
                        attachments = drafts + voice,
                        voiceDurationMillis = previousDuration + recorded.durationMillis,
                        isBusy = false,
                        statusMessage = "Голосовое пояснение добавлено",
                        isError = false,
                    )
                } else {
                    saveInternal(
                        text = text,
                        drafts = drafts + voice,
                        durationMillis = recorded.durationMillis,
                        successMessage = if (reason == RecordingStopReason.TIMEOUT) {
                            "Запись остановлена автоматически"
                        } else {
                            "В очереди"
                        },
                        trackSavedItem = reason != RecordingStopReason.TIMEOUT,
                    )
                }
            } catch (error: Exception) {
                RecordingForegroundService.stop(getApplication())
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
        val text = localState.value.text
        val drafts = localState.value.attachments
        viewModelScope.launch { saveInternal(text, drafts) }
    }

    private suspend fun saveInternal(
        text: String,
        drafts: List<AttachmentDraft>,
        durationMillis: Long? = null,
        successMessage: String = "В очереди",
        trackSavedItem: Boolean = true,
    ) {
        if (text.isBlank() && drafts.isEmpty()) {
            showError("Введите текст или добавьте файл")
            return
        }
        localState.value = localState.value.copy(
            isBusy = true,
            statusMessage = if (successMessage == "В очереди") "Сохраняем…" else successMessage,
        )
        val effectiveDurationMillis = durationMillis ?: localState.value.voiceDurationMillis
        runCatching { repository.save(text, drafts, source, effectiveDurationMillis) }
            .onSuccess { item ->
                lastSavedId = item.id.takeIf { trackSavedItem }
                localState.value = CaptureUiState(
                    statusMessage = successMessage,
                    defaultOpenMode = localState.value.defaultOpenMode,
                    autoStartAssistantRecording = localState.value.autoStartAssistantRecording,
                )
                if (closeAfterSave) closeEvents.trySend(Unit)
            }
            .onFailure { showError(it.message ?: "Не удалось сохранить запись") }
    }

    private fun showError(message: String) {
        recordingSafetyTimer.cancel()
        stopAmplitudeUpdates()
        RecordingForegroundService.stop(getApplication())
        localState.value = localState.value.copy(
            isBusy = false,
            isRecording = false,
            recordingAmplitude = 0f,
            statusMessage = message,
            isError = true,
        )
    }

    private fun handleRecorderError(message: String) {
        if (!localState.value.isRecording) return
        Log.e(TAG, "microphone lost/error: $message")
        finishRecording(RecordingStopReason.ERROR)
        showError(message)
    }

    private fun startAmplitudeUpdates() {
        amplitudeJob?.cancel()
        amplitudeJob = viewModelScope.launch {
            var smoothed = 0f
            while (localState.value.isRecording) {
                val raw = recorder.currentAmplitude().coerceIn(0, 32_767) / 32_767f
                val visibleLevel = kotlin.math.sqrt(raw)
                smoothed = smoothed * 0.78f + visibleLevel * 0.22f
                localState.value = localState.value.copy(recordingAmplitude = smoothed)
                delay(100)
            }
        }
    }

    private fun stopAmplitudeUpdates() {
        amplitudeJob?.cancel()
        amplitudeJob = null
    }

    override fun onCleared() {
        stopAmplitudeUpdates()
        if (!localState.value.isRecording) recordingSafetyTimer.cancel()
        super.onCleared()
    }

    class Factory(
        private val application: Application,
        private val repository: InboxRepository,
        private val settingsRepository: SettingsRepository,
        private val source: String = InboxSource.MANUAL,
        private val applicationScope: CoroutineScope,
        private val initialText: String? = null,
        private val initialUris: List<Uri> = emptyList(),
        private val closeOnSuccessfulSave: Boolean = false,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CaptureViewModel(
                application,
                repository,
                settingsRepository,
                source,
                applicationScope,
                initialText,
                initialUris,
                closeOnSuccessfulSave,
            ) as T
    }

    companion object {
        const val RECORDING_TIMEOUT_MILLIS = 60_000L
        private const val TAG = "BruceRecording"
    }
}
