package ru.bruce.dispatcherinbox.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

object InboxType {
    const val VOICE = "voice"
    const val TEXT = "text"
    const val IMAGE = "image"
    const val VIDEO = "video"
    const val DOCUMENT = "document"
    const val FILE = "file"
    const val MIXED = "mixed"
}

object InboxStatus {
    const val DRAFT = "draft"
    const val QUEUED = "queued"
    const val SYNCING = "syncing"
    const val SYNCED = "synced"
    const val ERROR = "error"
}

object InboxSource {
    const val MANUAL = "manual"
    const val ASSISTANT = "assistant"
    const val SHARE = "share"
    const val SELECTION_TEXT = "selection_text"
}

@Entity(tableName = "inbox_items")
data class InboxItemEntity(
    @PrimaryKey val id: String,
    val type: String,
    val text: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val status: String,
    val source: String,
    val retryCount: Int,
    val lastError: String?,
    val remoteId: String?,
    val durationMillis: Long? = null,
)

@Entity(
    tableName = "inbox_attachments",
    foreignKeys = [
        ForeignKey(
            entity = InboxItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId")],
)
data class InboxAttachmentEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val localPath: String,
    val mimeType: String?,
    val originalName: String?,
    val sizeBytes: Long,
    val checksum: String?,
)

data class InboxItemWithAttachments(
    @Embedded val item: InboxItemEntity,
    @Relation(parentColumn = "id", entityColumn = "itemId")
    val attachments: List<InboxAttachmentEntity>,
)

data class AttachmentDraft(
    val id: String = UUID.randomUUID().toString(),
    val localPath: String,
    val mimeType: String?,
    val originalName: String?,
    val sizeBytes: Long,
    val checksum: String?,
)
