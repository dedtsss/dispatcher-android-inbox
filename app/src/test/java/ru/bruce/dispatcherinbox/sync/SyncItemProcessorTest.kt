package ru.bruce.dispatcherinbox.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.bruce.dispatcherinbox.data.AppSettings
import ru.bruce.dispatcherinbox.data.InboxItemEntity
import ru.bruce.dispatcherinbox.data.InboxItemWithAttachments
import ru.bruce.dispatcherinbox.data.InboxSource
import ru.bruce.dispatcherinbox.data.InboxStatus
import ru.bruce.dispatcherinbox.data.InboxType
import ru.bruce.dispatcherinbox.network.DispatcherGateway
import ru.bruce.dispatcherinbox.network.UploadResult

class SyncItemProcessorTest {
    private val item = InboxItemWithAttachments(
        item = InboxItemEntity(
            id = "item-1",
            type = InboxType.TEXT,
            text = "test",
            createdAt = 1L,
            updatedAt = 1L,
            status = InboxStatus.QUEUED,
            source = InboxSource.MANUAL,
            retryCount = 0,
            lastError = null,
            remoteId = null,
        ),
        attachments = emptyList(),
    )

    @Test
    fun `uses fake gateway when debug URL is empty`() = runTest {
        val fake = FakeGateway(UploadResult.Success("fake-id"))
        val processor = SyncItemProcessor(realGateway = fake, fakeGateway = fake, isDebug = true)

        val result = processor.sync(item, AppSettings(apiBaseUrl = ""))

        assertEquals(UploadResult.Success("fake-id"), result)
        assertTrue(fake.called)
    }

    @Test
    fun `uses real gateway for configured URL`() = runTest {
        val real = FakeGateway(UploadResult.Success("remote-id"))
        val processor = SyncItemProcessor(realGateway = real, isDebug = false)

        val result = processor.sync(
            item,
            AppSettings(apiBaseUrl = "https://dispatcher.example/", bearerToken = "secret"),
        )

        assertEquals(UploadResult.Success("remote-id"), result)
        assertTrue(real.called)
    }

    private class FakeGateway(private val result: UploadResult) : DispatcherGateway {
        var called = false
        override suspend fun upload(
            item: InboxItemWithAttachments,
            settings: AppSettings,
        ): UploadResult {
            called = true
            return result
        }
    }
}
