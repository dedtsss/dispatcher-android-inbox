package ru.bruce.dispatcherinbox

import android.app.Application
import androidx.room.Room
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
    }
}

class AppContainer(application: Application) {
    private val database = Room.databaseBuilder(
        application,
        InboxDatabase::class.java,
        "dispatcher-inbox.db",
    ).build()

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
    val syncItemProcessor = SyncItemProcessor(RetrofitDispatcherGateway())
}
