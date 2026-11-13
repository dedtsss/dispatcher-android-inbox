package ru.bruce.dispatcherinbox.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [InboxItemEntity::class, InboxAttachmentEntity::class, ru.bruce.dispatcherinbox.drive.DriveCheckpoint::class],
    version = 3,
    exportSchema = true,
)
abstract class InboxDatabase : RoomDatabase() {
    abstract fun inboxDao(): InboxDao

    companion object {
        // Историческая v2 и выпущенная 0.6.0 имеют разные схемы с одним номером.
        // Сохраняем длительность старых записей и дополняем обе схемы до v3.
        private fun upgrade(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            val hasDuration = db.query("PRAGMA table_info(inbox_items)").use { cursor ->
                val name = cursor.getColumnIndexOrThrow("name")
                var found = false
                while (cursor.moveToNext()) if (cursor.getString(name) == "durationMillis") found = true
                found
            }
            if (!hasDuration) db.execSQL("ALTER TABLE inbox_items ADD COLUMN durationMillis INTEGER")
            db.execSQL("CREATE TABLE IF NOT EXISTS drive_checkpoints (`key` TEXT NOT NULL, remoteId TEXT NOT NULL, PRIMARY KEY(`key`))")
        }

        val MIGRATION_1_3 = object : androidx.room.migration.Migration(1, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = upgrade(db)
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = upgrade(db)
        }
    }
}
