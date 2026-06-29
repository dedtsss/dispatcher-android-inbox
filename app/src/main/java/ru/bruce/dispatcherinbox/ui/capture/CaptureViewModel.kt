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
import ru.bruce.dispatcherinbox.data.AppSettings
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
        lastSavedId = null
        viewModelScope.launch {
            localState.value = localState.value.copy(isBusy = true, statusMessage = "Копируем файл…")
            runCatching { repository.stage(uri) }
                .onSuccess { draft ->
                    localState.value = localState.value.copy(
                        attachments = localState.value.attachments + draft,
                        isBusy = false,
                        statusMessage = "Файл добавлен",
                        isError = false,
                    )
                }
                .onFailure { showError("Не удалось добавить файл") }
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
                statusMessage = "Идёт запись…",
                isError = false,
            )
        }.onFailure {
            audioFeedback.playErrorSound()
            showError("Не удалось начать запись")
        }
    }

    fun stopRecordingAndSave() {
        if (!localState.value.isRecording) return
        localState.value = localState.value.copy(isRecording = false, isBusy = true)
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
        localState.value = localState.value.copy(isBusy = true, statusMessage = "Сохраняем…")
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
        localState.value = localState.value.copy(
            isBusy = false,
            isRecording = false,
            statusMessage = message,
            isError = true,
        )
    }

    override fun onCleared() {
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
