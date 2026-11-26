package ru.bruce.dispatcherinbox.drive

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drive_checkpoints")
data class DriveCheckpoint(@PrimaryKey val key: String, val remoteId: String)

interface DriveIdStore {
    suspend fun get(key: String): String?
    suspend fun put(key: String, id: String)
}
