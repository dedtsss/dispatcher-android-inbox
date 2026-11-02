package ru.bruce.dispatcherinbox

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.bruce.dispatcherinbox.data.*
import ru.bruce.dispatcherinbox.drive.DriveCheckpoint
import ru.bruce.dispatcherinbox.util.VoiceRecorder
import java.io.File

class TestOriginalProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "application/octet-stream"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): Cursor =
        MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf("original.bin", 5)) }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = File(context!!.cacheDir, "original.bin").apply { writeBytes(byteArrayOf(0, 1, -1, 10, 42)) }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}

@RunWith(AndroidJUnit4::class)
class CaptureIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as DispatcherApplication).container
    private val device = UiDevice.getInstance(instrumentation)
    private val original = Uri.parse("content://ru.bruce.dispatcherinbox.test.originals/original")

    @Test fun captureOfflineAndSystemEntries() = runBlocking {
        // CI отключает Wi-Fi и мобильные данные до запуска этого теста.
        assertNull((context.getSystemService(android.net.ConnectivityManager::class.java)).activeNetwork)
        container.settingsRepository.setAutoSync(false)
        container.settingsRepository.setDefaultOpenMode("text")
        val text = "  Offline оригинал\n"
        val savedText = container.inboxRepository.save(text, emptyList())
        assertEquals(text, container.dao.getById(savedText.id)!!.item.text)
        assertEquals(InboxStatus.QUEUED, container.dao.getById(savedText.id)!!.item.status)

        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(device.wait(Until.hasObject(By.text("Голос")), 10000))
            screenshot("01-main")
            val recorder = VoiceRecorder(context)
            val file = container.inboxRepository.newVoiceFile()
            instrumentation.runOnMainSync { recorder.start(file) }
            Thread.sleep(1200)
            recorder.stop()
            assertTrue(file.length() > 0)
            val voice = container.inboxRepository.save(null, listOf(container.inboxRepository.voiceDraft(file)), InboxSource.ASSISTANT)
            val stored = container.dao.getById(voice.id)!!
            assertEquals(InboxType.VOICE, stored.item.type)
            assertEquals(InboxStatus.QUEUED, stored.item.status)
            assertTrue(File(stored.attachments.single().localPath).length() > 0)
        }
        val attachment = container.inboxRepository.save(null, listOf(container.inboxRepository.stage(original)))
        val local = container.dao.getById(attachment.id)!!
        assertEquals(InboxStatus.QUEUED, local.item.status)
        assertArrayEquals(byteArrayOf(0, 1, -1, 10, 42), File(local.attachments.single().localPath).readBytes())

        val marker = "share-${System.nanoTime()}"
        context.startActivity(Intent(Intent.ACTION_SEND).setClassName(context, "ru.bruce.dispatcherinbox.share.ShareReceiverActivity")
            .setType("text/plain").putExtra(Intent.EXTRA_TEXT, marker).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        awaitItem(marker)
        val multiMarker = "multi-${System.nanoTime()}"
        context.startActivity(Intent(Intent.ACTION_SEND_MULTIPLE).setClassName(context, "ru.bruce.dispatcherinbox.share.ShareReceiverActivity")
            .setType("*/*").putExtra(Intent.EXTRA_TEXT, multiMarker).putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(original))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
        val shared = awaitItem(multiMarker)
        assertEquals(InboxSource.SHARE, shared.item.source)
        assertArrayEquals(byteArrayOf(0, 1, -1, 10, 42), File(shared.attachments.single().localPath).readBytes())
        Thread.sleep(1500)

        val before = container.dao.getPending().size
        context.startActivity(Intent(Intent.ACTION_ASSIST).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(device.wait(Until.hasObject(By.text("Остановить и сохранить")), 10000))
        screenshot("02-assistant-recording")
        Thread.sleep(1000)
        device.findObject(By.text("Остановить и сохранить")).click()
        repeat(100) { if (container.dao.getPending().size <= before) Thread.sleep(100) }
        assertTrue(container.dao.getPending().size > before)
        val assistant = container.dao.getPending().last()
        assertEquals(InboxSource.ASSISTANT, assistant.item.source)
        assertTrue(File(assistant.attachments.single().localPath).length() > 0)
        screenshot("03-assistant-saved")
        device.pressBack()

        // Состояние syncing и зарезервированный Drive ID переживают новое соединение Room.
        container.dao.updateStatus(savedText.id, InboxStatus.SYNCING, System.currentTimeMillis(), null)
        container.dao.putDriveId(DriveCheckpoint("test/account/item", "reserved-id"))
        val reopened = Room.databaseBuilder(context, InboxDatabase::class.java, "dispatcher-inbox.db")
            .addMigrations(InboxDatabase.MIGRATION_1_2).build()
        reopened.inboxDao().recoverInterruptedSyncs("Проверка восстановления")
        assertEquals(InboxStatus.ERROR, reopened.inboxDao().getById(savedText.id)!!.item.status)
        assertEquals("reserved-id", reopened.inboxDao().driveId("test/account/item"))
        assertTrue(reopened.inboxDao().getPending().any { it.item.id == savedText.id })
        reopened.close()

        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(device.wait(Until.hasObject(By.text("Очередь")), 10000))
            device.findObject(By.text("Очередь")).click()
            screenshot("04-offline-queue")
            device.findObject(By.text("Настройки")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Google Drive")), 5000))
            screenshot("05-settings")
        }
    }

    private suspend fun awaitItem(text: String): InboxItemWithAttachments {
        repeat(100) {
            container.dao.getPending().firstOrNull { it.item.text == text }?.let { return it }
            Thread.sleep(100)
        }
        error("Share не создал локальную запись")
    }
    private fun screenshot(name: String) {
        device.waitForIdle()
        val dir = File(context.getExternalFilesDir(null), "catching-evidence").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(dir, "$name.png")))
    }
}
