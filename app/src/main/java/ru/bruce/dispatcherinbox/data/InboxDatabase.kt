package ru.bruce.dispatcherinbox.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [InboxItemEntity::class, InboxAttachmentEntity::class, ru.bruce.dispatcherinbox.drive.DriveCheckpoint::class],
    version = 2,
    exportSchema = false,
)
abstract class InboxDatabase : RoomDatabase() {
    abstract fun inboxDao(): InboxDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS drive_checkpoints (`key` TEXT NOT NULL, remoteId TEXT NOT NULL, PRIMARY KEY(`key`))")
            }
        }
    }
}
