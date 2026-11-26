package ru.bruce.dispatcherinbox.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import ru.bruce.dispatcherinbox.DispatcherApplication
import ru.bruce.dispatcherinbox.data.AttachmentStore
import ru.bruce.dispatcherinbox.data.InboxDao
import ru.bruce.dispatcherinbox.data.SettingsRepository

// Явная DI для интеграционных тестов; штатный WorkManager всегда использует
// двухаргументный конструктор и неизменённый production-контейнер приложения.
data class SyncDependencies(
    val dao: InboxDao,
    val processor: SyncItemProcessor,
    val settings: SettingsRepository,
    val store: AttachmentStore,
)

class SyncWorker @JvmOverloads constructor(
    appContext: Context,
    params: WorkerParameters,
    private val dependencies: SyncDependencies? = null,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as DispatcherApplication).container
        val dao = dependencies?.dao ?: container.dao
        val settingsRepository = dependencies?.settings ?: container.settingsRepository
        val settings = settingsRepository.settings.first()
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        if (!manual && !settings.autoSync) return Result.success()
        if (!manual && settings.wifiOnly &&
            applicationContext.getSystemService(android.net.ConnectivityManager::class.java).isActiveNetworkMetered
        ) return Result.retry()

        val shouldRetry = QueueDrainer(dao, dependencies?.processor ?: container.syncItemProcessor)
            .drain(settings, settingsRepository::setSyncDiagnostics)
        OriginalCleanup(dao, dependencies?.store ?: container.attachmentStore).run(settings)
        SyncNotifications(applicationContext).update(dao.getPending())
        return if (shouldRetry) Result.retry() else Result.success()
    }

    companion object { const val KEY_MANUAL = "manual" }
}
