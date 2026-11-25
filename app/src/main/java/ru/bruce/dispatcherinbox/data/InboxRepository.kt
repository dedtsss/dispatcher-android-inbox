package ru.bruce.dispatcherinbox.data

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.bruce.dispatcherinbox.sync.SyncScheduler

class InboxRepository(
    private val dao: InboxDao,
    private val attachmentStore: AttachmentStore,
    private val factory: InboxItemFactory,
    private val syncScheduler: SyncScheduler,
) {
    private val commitLock = Mutex()
    val items: Flow<List<InboxItemWithAttachments>> = dao.observeAll()

    suspend fun stage(uri: Uri): AttachmentDraft = attachmentStore.stage(uri)

    fun newVoiceFile() = attachmentStore.newVoiceFile()

    suspend fun voiceDraft(file: java.io.File) = attachmentStore.createVoiceDraft(file)

    suspend fun save(
        text: String?,
        drafts: List<AttachmentDraft>,
        source: String = InboxSource.MANUAL,
        onLocalCommit: (InboxItemEntity) -> Unit = {},
        captureId: String? = null,
    ): InboxItemEntity = commitLock.withLock {
        require(captureId == null || captureId.matches(Regex("[A-Za-z0-9-]+")))
        captureId?.let { id ->
            dao.getById(id)?.let { existing ->
                onLocalCommit(existing.item)
                syncScheduler.repair()
                return@withLock existing.item
            }
        }
        // Сторож должен быть durable до появления первой новой строки Room.
        // Сбой Android-планировщика всё равно не запрещает локальный capture.
        try { syncScheduler.ensureRepairScheduled() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) { syncScheduler.recordSchedulingFailure(error) }
        val item = factory.create(text, drafts, source).let { if (captureId == null) it else it.copy(id = captureId) }
        val attachments = mutableListOf<InboxAttachmentEntity>()
        try {
            drafts.forEach { attachments += attachmentStore.commit(item.id, it) }
            dao.insertItemWithAttachments(item, attachments)
        } catch (error: Exception) {
            attachmentStore.deleteAttachments(attachments)
            throw error
        }
        // Локальный commit уже достоверен. UI ассистента может завершиться до очистки/планировщика.
        onLocalCommit(item)
        drafts.forEach { runCatching { attachmentStore.deleteDraft(it) } }
        // Запись уже зафиксирована: сбой планировщика не должен провоцировать повторный capture.
        syncScheduler.repair()
        item
    }

    suspend fun discardDraft(draft: AttachmentDraft) = attachmentStore.deleteDraft(draft)

    suspend fun retry(id: String) {
        val exists = ru.bruce.dispatcherinbox.sync.QueueDrainer.withQueueLock {
            val item = dao.getById(id)?.item ?: return@withQueueLock false
            if (item.status == InboxStatus.ERROR) {
                dao.updateStatus(id, QueueStateMachine.onRetry(item.status), System.currentTimeMillis(), null)
            }
            true
        }
        if (exists) syncScheduler.scheduleManual()
    }

    suspend fun delete(id: String) = ru.bruce.dispatcherinbox.sync.QueueDrainer.withQueueLock {
        val item = dao.getById(id) ?: return@withQueueLock
        check(item.item.status != InboxStatus.SYNCING) { "Отправка ещё не завершена" }
        attachmentStore.deleteAttachments(item.attachments)
        dao.deleteById(id)
    }
}
