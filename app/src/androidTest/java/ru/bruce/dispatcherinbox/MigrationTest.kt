package ru.bruce.dispatcherinbox

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.bruce.dispatcherinbox.data.InboxDatabase
import java.io.File
import java.security.MessageDigest

class MigrationTest {
    @Test fun actualInstallOverPreservesData() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val args = InstrumentationRegistry.getArguments()
        val room = Room.databaseBuilder(context, InboxDatabase::class.java, "dispatcher-inbox.db")
            .addMigrations(InboxDatabase.MIGRATION_1_3, InboxDatabase.MIGRATION_2_3).build()
        try {
            for (status in listOf("queued", "error", "synced")) {
                val saved = room.inboxDao().getById("upgrade-$status")!!
                assertEquals("  исходник\nЮникод  ", saved.item.text)
                assertEquals(status, saved.item.status)
                assertEquals(123L, saved.item.createdAt)
                assertEquals(456L, saved.item.updatedAt)
                assertEquals("assistant", saved.item.source)
                assertEquals(2, saved.item.retryCount)
                assertEquals("ошибка", saved.item.lastError)
                assertEquals("remote-old", saved.item.remoteId)
                assertEquals(if (args.getString("historicalDuration") == "true") 12345L else null, saved.item.durationMillis)
                val attachment = saved.attachments.single()
                assertEquals("исходник.m4a", attachment.originalName)
                assertEquals("audio/mp4", attachment.mimeType)
                assertEquals(5L, attachment.sizeBytes)
                val bytes = File(attachment.localPath).readBytes()
                assertArrayEquals(byteArrayOf(0, 1, -1, 10, 42), bytes)
                assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, attachment.checksum)
            }
            if (args.getString("historicalDrive") == "true") assertEquals("reserved-drive-id", room.inboxDao().driveId("upgrade/account"))
            assertEquals(3, room.openHelper.writableDatabase.version)
        } finally { room.close() }
    }
}
