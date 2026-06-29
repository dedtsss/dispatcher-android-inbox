package ru.bruce.dispatcherinbox.sync

import ru.bruce.dispatcherinbox.BuildConfig
import ru.bruce.dispatcherinbox.data.AppSettings
import ru.bruce.dispatcherinbox.data.InboxItemWithAttachments
import ru.bruce.dispatcherinbox.network.DebugFakeDispatcherGateway
import ru.bruce.dispatcherinbox.network.DispatcherGateway
import ru.bruce.dispatcherinbox.network.UploadResult

class SyncItemProcessor(
    private val realGateway: DispatcherGateway,
    private val fakeGateway: DispatcherGateway = DebugFakeDispatcherGateway(),
    private val isDebug: Boolean = BuildConfig.DEBUG,
) {
    suspend fun sync(item: InboxItemWithAttachments, settings: AppSettings): UploadResult {
        if (settings.apiBaseUrl.isBlank()) {
            return if (isDebug) {
                fakeGateway.upload(item, settings)
            } else {
                UploadResult.Failure("Укажите Dispatcher API URL в настройках", false)
            }
        }
        return realGateway.upload(item, settings)
    }
}
