package ru.bruce.dispatcherinbox.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InboxDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        context.deleteDatabase(MIGRATION_DB)
    }

    @Test
    fun migrationOneToTwoPreservesQueueAndAddsNullableDuration() {
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
        val database = helper.writableDatabase

        InboxDatabase.MIGRATION_1_2.migrate(database)

        database.query("SELECT id, text, durationMillis FROM inbox_items WHERE id = 'queued-item'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("queued-item", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals("Не потерять", cursor.getString(cursor.getColumnIndexOrThrow("text")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("durationMillis")))
        }
        database.query("PRAGMA table_info(inbox_items)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            val columns = buildList {
                while (cursor.moveToNext()) add(cursor.getString(nameColumn))
            }
            assertTrue("durationMillis" in columns)
        }
        helper.close()
    }

    private companion object {
        const val MIGRATION_DB = "migration-1-to-2-test.db"
    }
}
