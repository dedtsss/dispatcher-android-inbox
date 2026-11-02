package ru.bruce.dispatcherinbox.sync

import ru.bruce.dispatcherinbox.data.AppSettings
import ru.bruce.dispatcherinbox.data.InboxDao
import ru.bruce.dispatcherinbox.data.InboxStatus
import ru.bruce.dispatcherinbox.data.QueueStateMachine
import ru.bruce.dispatcherinbox.network.UploadResult

class QueueDrainer(private val dao: InboxDao, private val processor: SyncItemProcessor) {
    suspend fun drain(settings: AppSettings): Boolean {
        dao.recoverInterruptedSyncs("Предыдущая синхронизация была прервана")
        var shouldRetry = false
        dao.getPending().forEach { entry ->
            dao.updateStatus(entry.item.id, QueueStateMachine.onSyncStarted(entry.item.status), System.currentTimeMillis(), null)
            when (val result = processor.sync(entry, settings)) {
                is UploadResult.Success -> dao.updateStatus(
                    entry.item.id, QueueStateMachine.onSyncSucceeded(InboxStatus.SYNCING),
                    System.currentTimeMillis(), null, remoteId = result.remoteId,
                )
                is UploadResult.Failure -> {
                    dao.updateStatus(
                        entry.item.id, QueueStateMachine.onSyncFailed(InboxStatus.SYNCING),
                        System.currentTimeMillis(), result.message, retryIncrement = 1,
                    )
                    shouldRetry = shouldRetry || result.retryable
                }
            }
        }
        return shouldRetry
    }
}
