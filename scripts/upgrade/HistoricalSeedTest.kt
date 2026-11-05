package ru.bruce.dispatcherinbox

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import kotlinx.coroutines.runBlocking
import ru.bruce.dispatcherinbox.data.SettingsRepository
import ru.bruce.dispatcherinbox.data.InboxDatabase
import java.io.File
import java.security.MessageDigest

class HistoricalSeedTest {
    @Test fun seedWithHistoricalRoom() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SettingsRepository(context).setAutoSync(false)
        val room = Room.databaseBuilder(context, InboxDatabase::class.java, "dispatcher-inbox.db").build()
        val db = room.openHelper.writableDatabase
        val original = File(context.filesDir, "upgrade-original.m4a")
        original.writeBytes(byteArrayOf(0, 1, -1, 10, 42))
        val hash = MessageDigest.getInstance("SHA-256").digest(original.readBytes()).joinToString("") { "%02x".format(it) }
        for (status in listOf("queued", "error", "synced")) {
            db.execSQL("INSERT INTO inbox_items (id,type,text,createdAt,updatedAt,status,source,retryCount,lastError,remoteId) VALUES (?, 'voice', '  исходник\nЮникод  ', 123, 456, ?, 'assistant', 2, 'ошибка', 'remote-old')", arrayOf("upgrade-$status", status))
            db.execSQL("INSERT INTO inbox_attachments VALUES (?, ?, ?, 'audio/mp4', 'исходник.m4a', 5, ?)", arrayOf("file-$status", "upgrade-$status", original.absolutePath, hash))
        }
        val hasDuration = db.query("PRAGMA table_info(inbox_items)").use { c ->
            var found = false
            while (c.moveToNext()) if (c.getString(1) == "durationMillis") found = true
            found
        }
        if (hasDuration) db.execSQL("UPDATE inbox_items SET durationMillis = 12345")
        val hasDrive = db.query("SELECT name FROM sqlite_master WHERE name='drive_checkpoints'").use { it.moveToFirst() }
        if (hasDrive) db.execSQL("INSERT INTO drive_checkpoints VALUES ('upgrade/account', 'reserved-drive-id')")
        room.close()
    }
}
