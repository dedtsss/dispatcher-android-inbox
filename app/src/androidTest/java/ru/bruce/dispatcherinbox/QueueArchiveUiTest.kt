package ru.bruce.dispatcherinbox

import android.content.Intent
import java.io.File
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import ru.bruce.dispatcherinbox.data.InboxItemEntity
import ru.bruce.dispatcherinbox.data.InboxSource
import ru.bruce.dispatcherinbox.data.InboxStatus
import ru.bruce.dispatcherinbox.data.InboxType

class QueueArchiveUiTest {
    @Test fun archiveOnlyConfirmedSentEntriesAndRestoreThemFromTheQueue() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val container = (context.applicationContext as DispatcherApplication).container
        val device = UiDevice.getInstance(instrumentation)
        val previousSettings = container.settingsRepository.settings.first()
        container.settingsRepository.setAutoSync(false)
        container.settingsRepository.setDriveAccount("test@example.invalid")
        val prefix = "queue-archive-${System.nanoTime()}"
        val now = System.currentTimeMillis()
        val existing = container.dao.observeAll().first()
        val existingConfirmed = existing.filter { it.item.archivedAt == null && it.item.status == InboxStatus.SYNCED && it.item.syncedAt != null }
        val existingArchivedCount = existing.count { it.item.archivedAt != null }
        val sentCount = existingConfirmed.size + 1
        val archiveCountAfterCleanup = existingArchivedCount + sentCount
        val entries = listOf(
            Triple("sent", InboxStatus.SYNCED, now + 4),
            Triple("waiting", InboxStatus.QUEUED, null),
            Triple("uploading", InboxStatus.SYNCING, null),
            Triple("failed", InboxStatus.ERROR, null),
        ).mapIndexed { index, (label, status, syncedAt) ->
            InboxItemEntity(
                id = "$prefix-$label", type = InboxType.TEXT, text = "$prefix-$label",
                createdAt = now + index, updatedAt = now + index, status = status,
                source = InboxSource.MANUAL, retryCount = 0, lastError = null,
                remoteId = if (syncedAt != null) "confirmed-$prefix" else null, syncedAt = syncedAt,
            )
        }
        entries.forEach { container.dao.insertItem(it) }
        val imageId = "$prefix-image"
        val documentId = "$prefix-document"
        container.dao.insertItemWithAttachments(
            InboxItemEntity(imageId, InboxType.IMAGE, null, now + 10, now + 10, InboxStatus.QUEUED,
                InboxSource.MANUAL, 0, null, null),
            listOf(ru.bruce.dispatcherinbox.data.InboxAttachmentEntity("$imageId-a", imageId, "/synthetic/not-a-file.pdf", "image/png", "not-a-file.pdf", 10, null)),
        )
        container.dao.insertItemWithAttachments(
            InboxItemEntity(documentId, InboxType.DOCUMENT, null, now + 11, now + 11, InboxStatus.QUEUED,
                InboxSource.MANUAL, 0, null, null),
            listOf(ru.bruce.dispatcherinbox.data.InboxAttachmentEntity("$documentId-a", documentId, "/synthetic/not-an-image.jpg", "application/pdf", "not-an-image.jpg", 10, null)),
        )
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                assertTrue(device.wait(Until.hasObject(By.text("Очередь")), 10000))
                device.findObjects(By.text("Очередь")).last().click()
                assertTrue(device.wait(Until.hasObject(By.text("Очистить отправленные ($sentCount)")), 10000))
                val queueList = UiScrollable(UiSelector().scrollable(true))
                fun assertVisibleStatus(marker: String, status: String) {
                    assertTrue("Не найдена синтетическая запись $marker",
                        queueList.scrollIntoView(UiSelector().descriptionContains(marker)))
                    assertTrue("Для $marker не показан статус $status",
                        device.wait(Until.hasObject(By.descContains("$marker. $status")), 3000))
                }
                assertVisibleStatus("$prefix-sent", "Отправлено")
                assertVisibleStatus("$prefix-waiting", "На телефоне · В очереди")
                assertVisibleStatus("$prefix-uploading", "Отправляется")
                assertVisibleStatus("$prefix-failed", "Ошибка загрузки")
                assertTrue("Изображение классифицировано по расширению, а не MIME", device.hasObject(By.descContains("Тип: фотография")))
                assertTrue("Документ классифицирован как изображение по расширению", device.hasObject(By.descContains("Тип: документ")))
                val evidence = File(context.getExternalFilesDir(null), "catching-evidence").apply { mkdirs() }
                assertTrue(device.takeScreenshot(File(evidence, "queue-types-and-delivery-status.png")))

                device.findObject(By.text("Очистить отправленные ($sentCount)")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Скрыть отправленные из очереди?")), 3000))
                device.findObject(By.text("В архив")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Архив ($archiveCountAfterCleanup)")), 5000))
                scenario.recreate()
                assertTrue(device.wait(Until.hasObject(By.text("Архив ($archiveCountAfterCleanup)")), 5000))
                device.findObject(By.text("Архив ($archiveCountAfterCleanup)")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Вернуть в очередь")), 5000))
                assertTrue(device.hasObject(By.descContains("$prefix-sent. В архиве · Отправлено")))
                device.findObjects(By.text("Вернуть в очередь")).first().click()
                assertTrue(device.wait(Until.hasObject(By.text("К очереди")), 5000))
                entries.forEach { before ->
                    val after = container.dao.getById(before.id)!!.item
                    assertEquals(before.status, after.status)
                    assertNull("Очистка затронула неподтверждённую запись: ${before.id}", after.archivedAt)
                }
            }
        } finally {
            existingConfirmed.forEach { container.inboxRepository.restoreArchived(it.item.id) }
            container.inboxRepository.restoreArchived(entries.first().id)
            entries.forEach { container.dao.deleteById(it.id) }
            container.dao.deleteById(imageId)
            container.dao.deleteById(documentId)
            container.settingsRepository.setAutoSync(previousSettings.autoSync)
            container.settingsRepository.setDriveAccount(previousSettings.driveAccount)
        }
    }
}
