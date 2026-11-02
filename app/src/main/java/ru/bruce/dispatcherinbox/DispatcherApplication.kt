package ru.bruce.dispatcherinbox

import android.app.Application
import kotlinx.coroutines.launch
import androidx.room.Room
import ru.bruce.dispatcherinbox.data.AttachmentStore
import ru.bruce.dispatcherinbox.data.InboxDao
import ru.bruce.dispatcherinbox.data.InboxDatabase
import ru.bruce.dispatcherinbox.data.InboxItemFactory
import ru.bruce.dispatcherinbox.data.InboxRepository
import ru.bruce.dispatcherinbox.data.SettingsRepository
import ru.bruce.dispatcherinbox.drive.*
import ru.bruce.dispatcherinbox.sync.SyncItemProcessor
import ru.bruce.dispatcherinbox.sync.SyncScheduler

class DispatcherApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { SyncScheduler(this@DispatcherApplication, container.settingsRepository).scheduleAutomatic() }
        }
    }
}

class AppContainer(application: Application) {
    private val database = Room.databaseBuilder(
        application,
        InboxDatabase::class.java,
        "dispatcher-inbox.db",
    ).addMigrations(InboxDatabase.MIGRATION_1_2).build()

    val dao: InboxDao = database.inboxDao()
    val settingsRepository = SettingsRepository(application)
    val attachmentStore = AttachmentStore(application)
    private val syncScheduler = SyncScheduler(application, settingsRepository)
    val inboxRepository = InboxRepository(
        dao = dao,
        attachmentStore = attachmentStore,
        factory = InboxItemFactory(),
        syncScheduler = syncScheduler,
    )
    private val driveAuth = DriveAuthorization(application)
    val syncItemProcessor = SyncItemProcessor(DriveGateway(
        ids = object : DriveIdStore {
            override suspend fun get(key: String) = dao.driveId(key)
            override suspend fun put(key: String, id: String) = dao.putDriveId(DriveCheckpoint(key, id))
        },
        tokenProvider = driveAuth::token,
        invalidateToken = driveAuth::clear,
        reportStatus = settingsRepository::setDriveStatus,
    ))
}
