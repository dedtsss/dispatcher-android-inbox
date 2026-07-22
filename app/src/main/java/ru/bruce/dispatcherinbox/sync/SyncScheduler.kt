package ru.bruce.dispatcherinbox.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.first
import ru.bruce.dispatcherinbox.data.SettingsRepository
import java.util.concurrent.TimeUnit

class SyncScheduler(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun scheduleAutomatic() {
        val settings = settingsRepository.settings.first()
        if (!settings.autoSync) return
        ensurePeriodic(wifiOnly = settings.wifiOnly)
        enqueueImmediate(manual = false, wifiOnly = settings.wifiOnly)
    }

    suspend fun ensurePeriodic() {
        val settings = settingsRepository.settings.first()
        val workManager = WorkManager.getInstance(context)
        if (!settings.autoSync) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }
        ensurePeriodic(wifiOnly = settings.wifiOnly)
    }

    fun scheduleManual() = enqueueImmediate(manual = true, wifiOnly = false)

    private fun enqueueImmediate(manual: Boolean, wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints(wifiOnly))
            .setInputData(
                workDataOf(
                    SyncWorker.KEY_MANUAL to manual,
                    SyncWorker.KEY_WATCHDOG to false,
                ),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun ensurePeriodic(wifiOnly: Boolean) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints(wifiOnly))
            .setInputData(
                workDataOf(
                    SyncWorker.KEY_MANUAL to false,
                    SyncWorker.KEY_WATCHDOG to true,
                ),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun networkConstraints(wifiOnly: Boolean) = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    private companion object {
        const val IMMEDIATE_WORK_NAME = "dispatcher-inbox-sync"
        const val PERIODIC_WORK_NAME = "dispatcher-inbox-sync-watchdog"
    }
}
