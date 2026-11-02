package ru.bruce.dispatcherinbox

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.bruce.dispatcherinbox.data.InboxDatabase
import ru.bruce.dispatcherinbox.drive.DriveCheckpoint

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @Test fun upgradeKeepsOriginalsAndQueue() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE inbox_items (id TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, text TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, status TEXT NOT NULL, source TEXT NOT NULL, retryCount INTEGER NOT NULL, lastError TEXT, remoteId TEXT)")
            db.execSQL("CREATE TABLE inbox_attachments (id TEXT NOT NULL PRIMARY KEY, itemId TEXT NOT NULL, localPath TEXT NOT NULL, mimeType TEXT, originalName TEXT, sizeBytes INTEGER NOT NULL, checksum TEXT, FOREIGN KEY(itemId) REFERENCES inbox_items(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX index_inbox_attachments_itemId ON inbox_attachments(itemId)")
            db.execSQL("INSERT INTO inbox_items VALUES ('old', 'text', '  исходник  ', 1, 1, 'queued', 'manual', 2, NULL, NULL)")
            db.execSQL("INSERT INTO inbox_attachments VALUES ('file', 'old', '/original/path', 'audio/mp4', 'voice.m4a', 42, 'checksum')")
            db.version = 1
        }
        val room = Room.databaseBuilder(context, InboxDatabase::class.java, name).addMigrations(InboxDatabase.MIGRATION_1_2).build()
        try {
            val saved = room.inboxDao().getById("old")!!
            assertEquals("  исходник  ", saved.item.text)
            assertEquals(2, saved.item.retryCount)
            assertEquals("/original/path", saved.attachments.single().localPath)
            assertEquals("checksum", saved.attachments.single().checksum)
            room.inboxDao().putDriveId(DriveCheckpoint("account/item", "drive-id"))
            assertEquals("drive-id", room.inboxDao().driveId("account/item"))
        } finally { room.close(); context.deleteDatabase(name) }
    }
}
