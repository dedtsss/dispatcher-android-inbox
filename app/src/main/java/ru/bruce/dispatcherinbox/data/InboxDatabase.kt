package ru.bruce.dispatcherinbox.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [InboxItemEntity::class, InboxAttachmentEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class InboxDatabase : RoomDatabase() {
    abstract fun inboxDao(): InboxDao
}
