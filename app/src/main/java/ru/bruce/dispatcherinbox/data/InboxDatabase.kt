package ru.bruce.dispatcherinbox.data

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [InboxItemEntity::class, InboxAttachmentEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class InboxDatabase : RoomDatabase() {
    abstract fun inboxDao(): InboxDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE inbox_items ADD COLUMN durationMillis INTEGER")
            }
        }
    }
}
