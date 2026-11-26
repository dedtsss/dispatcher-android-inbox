package ru.bruce.dispatcherinbox.network

import ru.bruce.dispatcherinbox.data.AppSettings
import ru.bruce.dispatcherinbox.data.InboxItemWithAttachments

sealed interface UploadResult {
    data class Success(val remoteId: String) : UploadResult
    data class Failure(val message: String, val retryable: Boolean) : UploadResult
}

interface DispatcherGateway {
    suspend fun upload(item: InboxItemWithAttachments, settings: AppSettings): UploadResult
}
