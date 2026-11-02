package ru.bruce.dispatcherinbox.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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
        enqueue(manual = false, wifiOnly = settings.wifiOnly)
    }

    fun scheduleManual() = enqueue(manual = true, wifiOnly = false)

    private fun enqueue(manual: Boolean, wifiOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(SyncWorker.KEY_MANUAL to manual))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            if (manual) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "dispatcher-inbox-sync"
    }
}
