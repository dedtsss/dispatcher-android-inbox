package ru.bruce.dispatcherinbox.sync

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import ru.bruce.dispatcherinbox.data.SettingsRepository
import java.util.concurrent.TimeUnit

class SyncScheduler(private val context: Context, private val settingsRepository: SettingsRepository) {
    suspend fun ensureRepairScheduled() = withContext(Dispatchers.IO) {
        // Независимый от сети сторож закрывает разрыв между Room commit и enqueue,
        // в том числе после убийства процесса. Android вправе отложить его в Doze.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            REPAIR_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncRepairWorker>(15, TimeUnit.MINUTES).build(),
        ).result.get()
    }

    suspend fun recordSchedulingFailure(error: Exception) {
        try { settingsRepository.setSyncResult("Не удалось поставить отправку: ${error.javaClass.simpleName}. Оригиналы в очереди") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { android.util.Log.e("CatchingSync", "Планировщик недоступен; локальный commit сохранён") }
    }

    suspend fun repair() {
        try {
            ensureRepairScheduled()
            scheduleAutomatic()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            recordSchedulingFailure(error)
        }
    }

    suspend fun scheduleAutomatic(replace: Boolean = false, coalesce: Boolean = false) {
        ensureRepairScheduled()
        val settings = settingsRepository.settings.first()
        if (!settings.autoSync) return
        enqueue(manual = false, wifiOnly = settings.wifiOnly, replace = replace, coalesce = coalesce)
    }

    suspend fun scheduleManual() = enqueue(manual = true, wifiOnly = false, replace = true, coalesce = false)

    private suspend fun enqueue(manual: Boolean, wifiOnly: Boolean, replace: Boolean, coalesce: Boolean) = withContext(Dispatchers.IO) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .setInputData(workDataOf(SyncWorker.KEY_MANUAL to manual))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            when {
                replace -> ExistingWorkPolicy.REPLACE
                coalesce -> ExistingWorkPolicy.KEEP
                else -> ExistingWorkPolicy.APPEND_OR_REPLACE
            },
            request,
        ).result.get()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "dispatcher-inbox-sync"
        const val REPAIR_WORK_NAME = "catching-sync-repair"
    }
}
