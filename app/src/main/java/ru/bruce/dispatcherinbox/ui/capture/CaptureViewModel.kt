package ru.bruce.dispatcherinbox.ui.capture

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val recordingLevels: List<Float> = RecordingLevelMeter.emptyHistory(),
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val localCommitted: Boolean = false,
    val savedOriginalBytes: Long = 0,
    val defaultOpenMode: String? = null,
    val recentItems: List<InboxItemWithAttachments> = emptyList(),
)

internal class CaptureSession(
    private val application: Application,
    private val repository: InboxRepository,
    settingsRepository: SettingsRepository,
    private val source: String,
    amplitudeSource: (() -> Int)? = null,
) {
    private val sessionScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main.immediate)
    private val recorder = VoiceRecorder(application)
    // Подмена доступна только конструктору теста; штатная Factory всегда читает MediaRecorder.
    private val readAmplitude = amplitudeSource ?: recorder::currentAmplitude
    private val levelMeter = RecordingLevelMeter()
    private val audioFeedback = AudioFeedbackManager()
    private val localState = MutableStateFlow(CaptureUiState())
    private val committedId = MutableStateFlow<String?>(null)
    private var amplitudeJob: kotlinx.coroutines.Job? = null
    private var captureId = java.util.UUID.randomUUID().toString()
    private var attachmentImportFailed = false
    private var stopping = false
    private var attachmentImportJob: kotlinx.coroutines.Job? = null
    private var rawVoicePath: String? = null
    private val journal = android.util.AtomicFile(java.io.File(application.filesDir, "capture-$source.json"))
    private var lastSavedId: String? = null
    val isCommitted get() = committedId.value != null && source == InboxSource.ASSISTANT

    init { restoreDraft() }

    private fun persistDraft(): Boolean = runCatching {
        val json = org.json.JSONObject().put("captureId", captureId).put("text", localState.value.text).put("rawVoicePath", rawVoicePath)
        json.put("attachments", org.json.JSONArray().apply { localState.value.attachments.forEach { d ->
            put(org.json.JSONObject().put("id", d.id).put("path", d.localPath).put("name", d.originalName)
                .put("mime", d.mimeType).put("size", d.sizeBytes).put("checksum", d.checksum))
        } })
        val stream = journal.startWrite()
        try { stream.write(json.toString().toByteArray()); journal.finishWrite(stream) }
        catch (error: Exception) { journal.failWrite(stream); throw error }
    }.onFailure {
        localState.value = localState.value.copy(isError = true, statusMessage = "Не удалось записать журнал черновика: ${it.message}")
    }.isSuccess
    private fun restoreDraft() {
        if (!journal.baseFile.exists()) return
        runCatching {
            val json = journal.openRead().use { org.json.JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
            captureId = json.optString("captureId").ifBlank { captureId }
            val a = json.getJSONArray("attachments")
            val drafts = (0 until a.length()).map { i -> a.getJSONObject(i).let { d ->
                AttachmentDraft(d.getString("id"), d.getString("path"), d.optString("mime").takeIf { it.isNotBlank() },
                    d.optString("name").takeIf { it.isNotBlank() }, d.getLong("size"), d.optString("checksum").takeIf { it.isNotBlank() })
            } }.toMutableList()
            val path = json.optString("rawVoicePath").takeIf { it.isNotBlank() && it != "null" }
            if (path != null && drafts.none { it.localPath == path }) {
                val file = java.io.File(path)
                if (file.exists()) drafts += AttachmentDraft(localPath = path, mimeType = "audio/mp4", originalName = file.name, sizeBytes = file.length(), checksum = null)
            }
            localState.value = localState.value.copy(text = json.optString("text"), attachments = drafts,
                statusMessage = "Восстановлен локальный черновик. Проверьте состав и аудио перед сохранением", isError = path != null)
        }.onFailure { localState.value = localState.value.copy(statusMessage = "Не удалось прочитать черновик. Исходные файлы сохранены", isError = true) }
    }

    // StateFlow повторит commit новой Activity после конфигурационной смены/возврата в STARTED.
    val close = committedId.filterNotNull().filter { source == InboxSource.ASSISTANT }.map { Unit }

    val state: StateFlow<CaptureUiState> = combine(
        localState,
        settingsRepository.settings,
        repository.items,
    ) { local, settings, items ->
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
            statusMessage = if (local.localCommitted) "Сохранено на телефоне" else syncMessage,
            isError = !local.localCommitted && ((!waitingForDrive && saved?.item?.status == InboxStatus.ERROR) || local.isError),
            driveConnected = settings.driveAccount.isNotBlank(),
            assistantOpenMode = settings.assistantOpenMode,
            autoStartAssistantRecording = settings.autoStartAssistantRecording,
            defaultOpenMode = settings.defaultOpenMode,
            recentItems = items.take(4),
        )
    }.stateIn(
        sessionScope,
        SharingStarted.WhileSubscribed(5_000),
        CaptureUiState(),
    )

    fun setText(value: String) {
        if (value != localState.value.text) lastSavedId = null
        localState.value = localState.value.copy(text = value, statusMessage = null, isError = false, localCommitted = false)
        persistDraft()
    }

    fun addAttachment(uri: Uri) {
        addAttachments(listOf(uri))
    }

    fun addAttachments(uris: List<Uri>) {
        val selectedUris = uris.distinct()
        if (selectedUris.isEmpty()) return
        lastSavedId = null
        localState.value = localState.value.copy(isBusy = true, localCommitted = false)
        attachmentImportJob = sessionScope.launch {
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
                isBusy = stopping,
                statusMessage = when {
                    failedCount == 0 && added.size == 1 -> "Файл добавлен"
                    failedCount == 0 -> "Добавлено файлов: ${added.size}"
                    added.isNotEmpty() -> "Добавлено: ${added.size}, ошибок: $failedCount"
                    else -> "Не удалось добавить выбранные файлы"
                },
                isError = failedCount > 0,
            )
            attachmentImportFailed = failedCount > 0
            persistDraft()

        }
    }

    fun removeAttachment(draft: AttachmentDraft) {
        sessionScope.launch {
            repository.discardDraft(draft)
            localState.value = localState.value.copy(
                attachments = localState.value.attachments - draft,
                statusMessage = null,
            )
            persistDraft()
        }
    }

    fun startRecording() {
        if (localState.value.isRecording || localState.value.isBusy || committedId.value != null && source == InboxSource.ASSISTANT) return
        lastSavedId = null
        runCatching {
            ru.bruce.dispatcherinbox.capture.RecordingService.begin(application, this, ::stopRecordingAndSave)
            val voiceFile = repository.newVoiceFile()
            rawVoicePath = voiceFile.absolutePath
            check(persistDraft()) { "Не удалось защитить черновик. Освободите место и повторите" }
            recorder.start(voiceFile)
            audioFeedback.playStartSound()
            levelMeter.reset()
            localState.value = localState.value.copy(
                localCommitted = false,
                isRecording = true,
                recordingAmplitude = 0f,
                recordingLevels = RecordingLevelMeter.emptyHistory(),
                recordingSeconds = 0,
                statusMessage = "Идёт запись",
                isError = false,
            )

            val started = android.os.SystemClock.elapsedRealtime()
            amplitudeJob?.cancel()
            amplitudeJob = sessionScope.launch {
                while (localState.value.isRecording) {
                    val measured = levelMeter.sample(readAmplitude())
                    localState.value = localState.value.copy(
                        recordingAmplitude = measured.level,
                        recordingLevels = measured.history,
                        recordingSeconds = (android.os.SystemClock.elapsedRealtime() - started) / 1000,
                    )
                    kotlinx.coroutines.delay(RecordingLevelMeter.SAMPLE_INTERVAL_MS)
                }
            }
        }.onFailure {
            ru.bruce.dispatcherinbox.capture.RecordingService.end(application, this)
            audioFeedback.playErrorSound()
            showError(it.message ?: "Не удалось начать запись")
        }
    }

    fun stopRecordingAndSave() {
        if (!localState.value.isRecording) return
        stopping = true
        amplitudeJob?.cancel()
        levelMeter.reset()
        localState.value = localState.value.copy(
            isRecording = false,
            recordingAmplitude = 0f,
            recordingLevels = RecordingLevelMeter.emptyHistory(),
            isBusy = true,
        )
        sessionScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { recorder.stop() }
                audioFeedback.playStopSound()
                val voice = repository.voiceDraft(file)
                rawVoicePath = null
                localState.value = localState.value.copy(attachments = localState.value.attachments + voice)
                persistDraft()
                attachmentImportJob?.join()
                check(!attachmentImportFailed) { "Аудио сохранено в черновике. Не все выбранные файлы добавлены: повторите выбор файла или сохраните доступный состав" }
                saveInternal(localState.value.attachments)
            } catch (error: Exception) {
                rawVoicePath?.let { path ->
                    val file = java.io.File(path)
                    if (file.exists() && localState.value.attachments.none { it.localPath == path }) {
                        localState.value = localState.value.copy(attachments = localState.value.attachments + AttachmentDraft(
                            localPath = path, mimeType = "audio/mp4", originalName = "Незавершённая-${file.name}", sizeBytes = file.length(), checksum = null))
                        persistDraft()
                    }
                }
                ru.bruce.dispatcherinbox.capture.RecordingService.end(application, this@CaptureSession)
                audioFeedback.playErrorSound()
                showError(error.message ?: "Не удалось сохранить запись")
            } finally { stopping = false }
        }
    }

    fun onAudioPermissionDenied() {
        audioFeedback.playErrorSound()
        showError("Без доступа к микрофону запись голоса невозможна")
    }

    fun save() {
        if (localState.value.isBusy || localState.value.isRecording || committedId.value != null && source == InboxSource.ASSISTANT) return
        val drafts = localState.value.attachments
        // Синхронно закрываем окно повторного нажатия до запуска coroutine.
        localState.value = localState.value.copy(isBusy = true)
        sessionScope.launch { saveInternal(drafts) }
    }

    private suspend fun saveInternal(drafts: List<AttachmentDraft>) {
        val text = localState.value.text
        if (text.isBlank() && drafts.isEmpty()) {
            showError("Введите текст или добавьте файл")
            return
        }
        localState.value = localState.value.copy(attachments = drafts, isBusy = true, statusMessage = "Сохраняем…")
        try {
            // Завершение Activity после commit не должно отменить очистку/постановку WorkManager.
            withContext(NonCancellable) {
                repository.save(text, drafts, source, onLocalCommit = { item ->
                    lastSavedId = item.id
                    localState.value = CaptureUiState(
                        statusMessage = "Сохранено на телефоне",
                        localCommitted = true,
                        savedOriginalBytes = drafts.sumOf { it.sizeBytes },
                        defaultOpenMode = localState.value.defaultOpenMode,
                    )
                    journal.delete()
                    rawVoicePath = null
                    committedId.value = item.id
                    ru.bruce.dispatcherinbox.capture.RecordingService.end(application, this@CaptureSession)
                    captureId = java.util.UUID.randomUUID().toString()
                }, captureId = captureId)
            }
        } catch (error: Exception) {
            ru.bruce.dispatcherinbox.capture.RecordingService.end(application, this@CaptureSession)
            showError(error.message ?: "Не удалось сохранить запись")
        }
    }

    private fun showError(message: String) {
        amplitudeJob?.cancel()
        levelMeter.reset()
        localState.value = localState.value.copy(
            isBusy = false,
            isRecording = false,
            recordingAmplitude = 0f,
            recordingLevels = RecordingLevelMeter.emptyHistory(),
            statusMessage = message,
            isError = true,
        )
    }

}

class CaptureViewModel(
    application: Application,
    repository: InboxRepository,
    settingsRepository: SettingsRepository,
    source: String,
    amplitudeSource: (() -> Int)? = null,
) : AndroidViewModel(application) {
    // Сервис удерживает сессию записи независимо от уничтожения Activity/ViewModel.
    private val session = if (amplitudeSource != null) CaptureSession(application, repository, settingsRepository, source, amplitudeSource)
        else synchronized(sessions) {
            sessions[source]?.takeUnless { it.isCommitted } ?: CaptureSession(application, repository, settingsRepository, source).also { sessions[source] = it }
        }
    val state get() = session.state
    val close get() = session.close
    fun setText(value: String) = session.setText(value)
    fun addAttachment(uri: Uri) = session.addAttachment(uri)
    fun addAttachments(uris: List<Uri>) = session.addAttachments(uris)
    fun removeAttachment(draft: AttachmentDraft) = session.removeAttachment(draft)
    fun startRecording() = session.startRecording()
    fun stopRecordingAndSave() = session.stopRecordingAndSave()
    fun onAudioPermissionDenied() = session.onAudioPermissionDenied()
    fun save() = session.save()
    companion object { private val sessions = mutableMapOf<String, CaptureSession>() }
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
