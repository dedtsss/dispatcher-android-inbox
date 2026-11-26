package ru.bruce.dispatcherinbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.bruce.dispatcherinbox.data.InboxStatus
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class ProcessRestartTest {
    @Test fun originalsAndQueueSurviveForceStop() = runBlocking<Unit> {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DispatcherApplication
        val container = app.container
        val all = container.dao.observeAll().first()
        val marker = all.single { it.item.text == "process-restart-marker" }
        assertEquals(InboxStatus.QUEUED, marker.item.status)
        assertArrayEquals(byteArrayOf(0, 1, -1, 10, 42), File(marker.attachments.single().localPath).readBytes())
        assertEquals("reserved-id", container.dao.driveId("test/account/item"))
        assertTrue(all.size >= 7)
        all.flatMap { it.attachments }.forEach { attachment ->
            val file = File(attachment.localPath)
            assertTrue(file.isFile)
            val checksum = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals(attachment.checksum, checksum)
        }
    }
}
