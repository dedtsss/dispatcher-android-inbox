package ru.bruce.dispatcherinbox.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface InboxDao {
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

    @Query("DELETE FROM inbox_items WHERE id = :id")
    suspend fun deleteById(id: String)
}
