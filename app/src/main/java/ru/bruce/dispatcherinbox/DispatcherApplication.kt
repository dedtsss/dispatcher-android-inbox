package ru.bruce.dispatcherinbox

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.bruce.dispatcherinbox.data.AttachmentStore
import ru.bruce.dispatcherinbox.data.InboxDao
import ru.bruce.dispatcherinbox.data.InboxDatabase
import ru.bruce.dispatcherinbox.data.InboxItemFactory
import ru.bruce.dispatcherinbox.data.InboxRepository
import ru.bruce.dispatcherinbox.data.SettingsRepository
import ru.bruce.dispatcherinbox.network.RetrofitDispatcherGateway
import ru.bruce.dispatcherinbox.sync.SyncItemProcessor
import ru.bruce.dispatcherinbox.sync.SyncScheduler

class DispatcherApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.applicationScope.launch {
            container.syncScheduler.ensurePeriodic()
        }
    }
}

class AppContainer(application: Application) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val database = Room.databaseBuilder(
        application,
        InboxDatabase::class.java,
        "dispatcher-inbox.db",
    ).addMigrations(InboxDatabase.MIGRATION_1_2).build()

    val dao: InboxDao = database.inboxDao()
    val settingsRepository = SettingsRepository(application)
    val attachmentStore = AttachmentStore(application)
    val syncScheduler = SyncScheduler(application, settingsRepository)
    val inboxRepository = InboxRepository(
        dao = dao,
        attachmentStore = attachmentStore,
        factory = InboxItemFactory(),
        syncScheduler = syncScheduler,
    )
    val syncItemProcessor = SyncItemProcessor(RetrofitDispatcherGateway())
}
