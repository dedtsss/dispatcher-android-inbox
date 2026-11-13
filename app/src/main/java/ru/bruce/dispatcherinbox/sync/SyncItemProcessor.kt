package ru.bruce.dispatcherinbox.sync

import ru.bruce.dispatcherinbox.data.AppSettings
import ru.bruce.dispatcherinbox.data.InboxItemWithAttachments
import ru.bruce.dispatcherinbox.network.DispatcherGateway
import ru.bruce.dispatcherinbox.network.UploadResult

class SyncItemProcessor(private val realGateway: DispatcherGateway) {
    suspend fun sync(item: InboxItemWithAttachments, settings: AppSettings): UploadResult =
        realGateway.upload(item, settings)
}
