package ru.bruce.dispatcherinbox.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import ru.bruce.dispatcherinbox.DispatcherApplication

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as DispatcherApplication).container
        val settings = container.settingsRepository.settings.first()
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        if (!manual && !settings.autoSync) return Result.success()
        if (!manual && settings.wifiOnly &&
            applicationContext.getSystemService(android.net.ConnectivityManager::class.java).isActiveNetworkMetered
        ) return Result.retry()

        val shouldRetry = QueueDrainer(container.dao, container.syncItemProcessor).drain(settings)
        return if (shouldRetry) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_MANUAL = "manual"
    }
}
