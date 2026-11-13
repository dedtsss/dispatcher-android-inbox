package ru.bruce.dispatcherinbox

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import ru.bruce.dispatcherinbox.data.SettingsRepository
import ru.bruce.dispatcherinbox.data.InboxDatabase
import java.io.File
import java.security.MessageDigest

class HistoricalSeedTest {
    @Test fun demonstrateHistoricalDebugSender() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SettingsRepository(context).setAutoSync(false)
        val manager = androidx.work.WorkManager.getInstance(context)
        manager.cancelAllWork().result.get(10, java.util.concurrent.TimeUnit.SECONDS)
        val room = Room.databaseBuilder(context, InboxDatabase::class.java, "dispatcher-inbox.db").build()
        try {
            room.openHelper.writableDatabase.execSQL("INSERT INTO inbox_items (id,type,text,createdAt,updatedAt,status,source,retryCount,lastError,remoteId) VALUES ('probe-race','text','fixture probe',123,456,'queued','assistant',2,'ошибка','remote-old')")
            val worker = Class.forName("ru.bruce.dispatcherinbox.sync.SyncWorker").asSubclass(androidx.work.ListenableWorker::class.java)
            // Изолированная демонстрация именно старого debug writer, без сети и без записей upgrade-*.
            val request = androidx.work.OneTimeWorkRequest.Builder(worker)
                .setInputData(androidx.work.Data.Builder().putBoolean("manual", true).build()).build()
            manager.enqueue(request).result.get(10, java.util.concurrent.TimeUnit.SECONDS)
            kotlinx.coroutines.withTimeout(15000) {
                while (!manager.getWorkInfoById(request.id).get(5, java.util.concurrent.TimeUnit.SECONDS)!!.state.isFinished) kotlinx.coroutines.delay(100)
            }
            room.openHelper.readableDatabase.query("SELECT status, remoteId, updatedAt FROM inbox_items WHERE id='probe-race'").use { c ->
                org.junit.Assert.assertTrue(c.moveToFirst())
                org.junit.Assert.assertEquals("synced", c.getString(0))
                org.junit.Assert.assertEquals("debug-probe-race", c.getString(1))
                org.junit.Assert.assertTrue(c.getLong(2) > 456)
                android.util.Log.i("CatchingFixture", "oldDebugWriterConfirmed=true; queuedToSynced=true; fakeRemoteIdConfirmed=true; autoSync=false")
            }
        } finally {
            manager.cancelAllWork().result.get(10, java.util.concurrent.TimeUnit.SECONDS)
            room.openHelper.writableDatabase.execSQL("DELETE FROM inbox_items WHERE id='probe-race'")
            room.close()
        }
    }

    @Test fun seedWithHistoricalRoom() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SettingsRepository(context).setAutoSync(false)
        val manager = androidx.work.WorkManager.getInstance(context)
        val before = manager.getWorkInfosByTag("ru.bruce.dispatcherinbox.sync.SyncWorker").get(10, java.util.concurrent.TimeUnit.SECONDS)
        android.util.Log.i("CatchingFixture", "beforeCancel=" + before.map { it.state.name })
        // Запись настройки не отменяет уже начавшийся worker со снимком старых настроек.
        manager.cancelAllWork().result.get(10, java.util.concurrent.TimeUnit.SECONDS)
        val remaining = manager.getWorkInfosByTag("ru.bruce.dispatcherinbox.sync.SyncWorker").get(10, java.util.concurrent.TimeUnit.SECONDS)
        org.junit.Assert.assertTrue(remaining.all { it.state.isFinished })
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
        if (InstrumentationRegistry.getArguments().getString("historicalDriveAccount") == "true") {
            // Синтетическая настройка для install-over; не выдаём её за настоящий OAuth grant.
            SettingsRepository(context).setDriveAccount("migration@example.test")
            SettingsRepository(context).setDriveStatus("Подключён · Catching/Inbox")
        }
        verifyWithHistoricalRoom()
    }

    @Test fun verifyWithHistoricalRoom() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (InstrumentationRegistry.getArguments().getString("historicalDriveAccount") == "true") runBlocking {
            val settings = SettingsRepository(context).settings.first()
            org.junit.Assert.assertEquals("migration@example.test", settings.driveAccount)
            org.junit.Assert.assertEquals("Подключён · Catching/Inbox", settings.driveStatus)
            org.junit.Assert.assertFalse(settings.autoSync)
        }
        val room = Room.databaseBuilder(context, InboxDatabase::class.java, "dispatcher-inbox.db").build()
        try {
            for (status in listOf("queued", "error", "synced")) {
                room.openHelper.readableDatabase.query("SELECT status, updatedAt, retryCount, remoteId FROM inbox_items WHERE id=?", arrayOf("upgrade-$status")).use { c ->
                    org.junit.Assert.assertTrue(c.moveToFirst())
                    android.util.Log.i("CatchingFixture", "historical item=$status; actual=${c.getString(0)}; updatedAt=${c.getLong(1)}; retry=${c.getInt(2)}; remoteMatches=${c.getString(3) == "remote-old"}")
                    org.junit.Assert.assertEquals(status, c.getString(0))
                    org.junit.Assert.assertEquals(456L, c.getLong(1))
                    org.junit.Assert.assertEquals(2, c.getInt(2))
                    org.junit.Assert.assertEquals("remote-old", c.getString(3))
                }
            }
        } finally { room.close() }
    }
}
