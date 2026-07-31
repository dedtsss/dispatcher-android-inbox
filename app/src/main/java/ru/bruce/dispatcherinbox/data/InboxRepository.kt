package ru.bruce.dispatcherinbox.data

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import ru.bruce.dispatcherinbox.sync.SyncScheduler

class InboxRepository(
    private val dao: InboxDao,
    private val attachmentStore: AttachmentStore,
    private val factory: InboxItemFactory,
    private val syncScheduler: SyncScheduler,
) {
    val items: Flow<List<InboxItemWithAttachments>> = dao.observeAll()

    suspend fun stage(uri: Uri): AttachmentDraft = attachmentStore.stage(uri)

    fun newVoiceFile() = attachmentStore.newVoiceFile()

    suspend fun voiceDraft(file: java.io.File) = attachmentStore.createVoiceDraft(file)

    suspend fun save(
        text: String?,
        drafts: List<AttachmentDraft>,
        source: String = InboxSource.MANUAL,
        durationMillis: Long? = null,
    ): InboxItemEntity {
        val item = factory.create(text, drafts, source, durationMillis = durationMillis)
        val attachments = mutableListOf<InboxAttachmentEntity>()
        try {
            drafts.forEach { attachments += attachmentStore.commit(item.id, it) }
            dao.insertItemWithAttachments(item, attachments)
        } catch (error: Exception) {
            attachmentStore.deleteAttachments(attachments)
            throw error
        }
        syncScheduler.scheduleAutomatic()
        return item
    }

    suspend fun discardDraft(draft: AttachmentDraft) = attachmentStore.deleteDraft(draft)

    suspend fun retry(id: String) {
        val item = dao.getById(id)?.item ?: return
        if (item.status == InboxStatus.ERROR) {
            dao.updateStatus(
                id = id,
                status = QueueStateMachine.onRetry(item.status),
                updatedAt = System.currentTimeMillis(),
                lastError = null,
            )
        }
        syncScheduler.scheduleManual()
    }

    suspend fun delete(id: String) {
        val item = dao.getById(id) ?: return
        attachmentStore.deleteAttachments(item.attachments)
        dao.deleteById(id)
    }
}
