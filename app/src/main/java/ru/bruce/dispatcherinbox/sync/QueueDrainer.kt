package ru.bruce.dispatcherinbox.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.bruce.dispatcherinbox.data.*
import ru.bruce.dispatcherinbox.network.UploadResult

class QueueDrainer(private val dao: InboxDao, private val processor: SyncItemProcessor) {
    suspend fun drain(settings: AppSettings, report: suspend (Long, String) -> Unit = { _, _ -> }): Boolean = lock.withLock {
        if (settings.driveAccount.isBlank()) return@withLock false
        dao.recoverInterruptedSyncs("Предыдущая синхронизация была прервана; оригиналы сохранены")
        var shouldRetry = false
        dao.getPending().forEach { entry ->
            val started = System.currentTimeMillis()
            dao.startAttempt(entry.item.id, started)
            report(started, "Отправляется")
            val result = try {
                processor.sync(entry, settings).also { currentCoroutineContext().ensureActive() }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    dao.updateStatus(entry.item.id, InboxStatus.ERROR, System.currentTimeMillis(), "Отправка прервана системой; повтор безопасен")
                    report(started, "Прервано системой; оригиналы сохранены")
                }
                throw cancelled
            } catch (error: Exception) {
                UploadResult.Failure("Сбой отправки: ${error.javaClass.simpleName}. Оригиналы сохранены", true)
            }
            when (result) {
                is UploadResult.Success -> {
                    // Gateway возвращает Success только после size+MD5 всех частей события.
                    dao.confirmFullUpload(entry.item.id, System.currentTimeMillis(), result.remoteId)
                    report(started, "Полный состав подтверждён Google Drive")
                }
                is UploadResult.Failure -> {
                    dao.updateStatus(entry.item.id, InboxStatus.ERROR, System.currentTimeMillis(), result.message, retryIncrement = 1)
                    report(started, result.message)
                    shouldRetry = shouldRetry || result.retryable
                }
            }
        }
        shouldRetry
    }

    companion object {
        private val lock = Mutex()
        suspend fun <T> withQueueLock(action: suspend () -> T): T = lock.withLock { action() }
    }
}
