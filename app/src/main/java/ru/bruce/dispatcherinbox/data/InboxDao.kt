package ru.bruce.dispatcherinbox.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface InboxDao {
    @Query("SELECT remoteId FROM drive_checkpoints WHERE `key` = :key")
    suspend fun driveId(key: String): String?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun putDriveId(checkpoint: ru.bruce.dispatcherinbox.drive.DriveCheckpoint)

    @Insert
    suspend fun insertItem(item: InboxItemEntity)

    @Insert
    suspend fun insertAttachments(attachments: List<InboxAttachmentEntity>)

    @Transaction
    suspend fun insertItemWithAttachments(
        item: InboxItemEntity,
        attachments: List<InboxAttachmentEntity>,
    ) {
        insertItem(item)
        if (attachments.isNotEmpty()) insertAttachments(attachments)
    }

    @Transaction
    @Query("SELECT * FROM inbox_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<InboxItemWithAttachments>>

    @Transaction
    @Query("SELECT * FROM inbox_items WHERE status IN ('queued', 'error') ORDER BY createdAt ASC")
    suspend fun getPending(): List<InboxItemWithAttachments>

    @Transaction
    @Query("SELECT * FROM inbox_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): InboxItemWithAttachments?

    @Query(
        """
        UPDATE inbox_items
        SET status = :status,
            updatedAt = :updatedAt,
            lastError = :lastError,
            remoteId = COALESCE(:remoteId, remoteId),
            retryCount = retryCount + :retryIncrement
        WHERE id = :id
        """,
    )
    suspend fun updateStatus(
        id: String,
        status: String,
        updatedAt: Long,
        lastError: String?,
        remoteId: String? = null,
        retryIncrement: Int = 0,
    )

    @Query("UPDATE inbox_items SET status = 'error', lastError = :message WHERE status = 'syncing'")
    suspend fun recoverInterruptedSyncs(message: String)

    @Query("UPDATE inbox_items SET lastAttemptAt = :at, status = 'syncing', updatedAt = :at, lastError = NULL WHERE id = :id AND status IN ('queued', 'error')")
    suspend fun startAttempt(id: String, at: Long)

    @Query("UPDATE inbox_items SET status = 'synced', updatedAt = :at, syncedAt = :at, remoteId = :remoteId, lastError = NULL WHERE id = :id")
    suspend fun confirmFullUpload(id: String, at: Long, remoteId: String)

    @Transaction
    @Query("SELECT * FROM inbox_items WHERE status = 'synced' AND syncedAt IS NOT NULL AND originalsDeletedAt IS NULL")
    suspend fun getConfirmedForCleanup(): List<InboxItemWithAttachments>

    @Query("UPDATE inbox_items SET originalsDeletedAt = :at WHERE id = :id AND status = 'synced' AND syncedAt IS NOT NULL")
    suspend fun markOriginalsDeleted(id: String, at: Long)

    @Query("DELETE FROM inbox_items WHERE id = :id")
    suspend fun deleteById(id: String)
}
