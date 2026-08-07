package ru.bruce.dispatcherinbox.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InboxDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var database: InboxDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(MIGRATION_DB)
    }

    @Test
    fun clearQueueKeepsSyncingItems() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, InboxDatabase::class.java).build()
        database = db
        val dao = db.inboxDao()
        dao.insertItem(item("queued", InboxStatus.QUEUED))
        dao.insertItem(item("synced", InboxStatus.SYNCED))
        dao.insertItem(item("error", InboxStatus.ERROR))
        dao.insertItem(item("syncing", InboxStatus.SYNCING))

        val deleted = dao.deleteAllExceptSyncing()

        assertEquals(3, deleted.size)
        assertEquals(listOf("syncing"), dao.observeAll().first().map { it.item.id })
    }

    @Test
    fun migrationOneToTwoAddsDurationColumn() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(MIGRATION_DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            """CREATE TABLE inbox_items (
                                id TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, text TEXT,
                                createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                                status TEXT NOT NULL, source TEXT NOT NULL, retryCount INTEGER NOT NULL,
                                lastError TEXT, remoteId TEXT
                            )""".trimIndent(),
                        )
                        db.execSQL(
                            """INSERT INTO inbox_items (
                                id, type, text, createdAt, updatedAt, status, source, retryCount,
                                lastError, remoteId
                            ) VALUES (
                                'queued-item', 'text', 'Не потерять', 1, 1, 'queued', 'share', 0,
                                NULL, NULL
                            )""".trimIndent(),
                        )
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build(),
        )
        val sqlite = helper.writableDatabase

        InboxDatabase.MIGRATION_1_2.migrate(sqlite)

        sqlite.query("SELECT text, durationMillis FROM inbox_items WHERE id = 'queued-item'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Не потерять", cursor.getString(cursor.getColumnIndexOrThrow("text")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("durationMillis")))
        }

        sqlite.query("PRAGMA table_info(inbox_items)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            val columns = buildList {
                while (cursor.moveToNext()) add(cursor.getString(nameColumn))
            }
            assertTrue("durationMillis" in columns)
        }
        helper.close()
    }

    private fun item(id: String, status: String) = InboxItemEntity(
        id = id,
        type = InboxType.TEXT,
        text = id,
        createdAt = 1L,
        updatedAt = 1L,
        status = status,
        source = InboxSource.MANUAL,
        retryCount = 0,
        lastError = null,
        remoteId = null,
    )

    private companion object {
        const val MIGRATION_DB = "migration-test.db"
    }
}
