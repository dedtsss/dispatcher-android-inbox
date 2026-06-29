package ru.bruce.dispatcherinbox.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import ru.bruce.dispatcherinbox.DispatcherApplication
import ru.bruce.dispatcherinbox.data.InboxStatus
import ru.bruce.dispatcherinbox.data.QueueStateMachine
import ru.bruce.dispatcherinbox.network.UploadResult

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as DispatcherApplication).container
        val settings = container.settingsRepository.settings.first()
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        if (!manual && !settings.autoSync) return Result.success()

        container.dao.recoverInterruptedSyncs("Предыдущая синхронизация была прервана")
        val pending = container.dao.getPending()
        var shouldRetry = false

        pending.forEach { entry ->
            container.dao.updateStatus(
                id = entry.item.id,
                status = QueueStateMachine.onSyncStarted(entry.item.status),
                updatedAt = System.currentTimeMillis(),
                lastError = null,
            )
            when (val result = container.syncItemProcessor.sync(entry, settings)) {
                is UploadResult.Success -> {
                    container.dao.updateStatus(
                        id = entry.item.id,
                        status = QueueStateMachine.onSyncSucceeded(InboxStatus.SYNCING),
                        updatedAt = System.currentTimeMillis(),
                        lastError = null,
                        remoteId = result.remoteId,
                    )
                    if (settings.deleteAfterSync) {
                        container.attachmentStore.deleteAttachments(entry.attachments)
                    }
                }
                is UploadResult.Failure -> {
                    container.dao.updateStatus(
                        id = entry.item.id,
                        status = QueueStateMachine.onSyncFailed(InboxStatus.SYNCING),
                        updatedAt = System.currentTimeMillis(),
                        lastError = result.message,
                        retryIncrement = 1,
                    )
                    shouldRetry = shouldRetry || result.retryable
                }
            }
        }

        return if (shouldRetry && runAttemptCount < 4) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_MANUAL = "manual"
    }
}
