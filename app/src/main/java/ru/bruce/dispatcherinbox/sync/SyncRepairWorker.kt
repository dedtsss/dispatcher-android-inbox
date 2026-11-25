package ru.bruce.dispatcherinbox.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import ru.bruce.dispatcherinbox.DispatcherApplication

class SyncRepairWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as DispatcherApplication).container
        return try {
            val settings = container.settingsRepository.settings.first()
            val pending = container.dao.getPending()
            if (settings.autoSync && pending.isNotEmpty()) {
                SyncScheduler(applicationContext, container.settingsRepository).scheduleAutomatic(coalesce = true)
            }
            OriginalCleanup(container.dao, container.attachmentStore).run(settings)
            if (settings.autoSync) SyncNotifications(applicationContext).update(pending)
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            container.settingsRepository.setSyncResult("Восстановление очереди: ${error.javaClass.simpleName}. Оригиналы сохранены")
            Result.retry()
        }
    }
}
