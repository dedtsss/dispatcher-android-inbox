package ru.bruce.dispatcherinbox.data

import java.util.UUID

data class InboxDraftPlan(
    val item: InboxItemEntity,
    val attachments: List<AttachmentDraft>,
)

class InboxItemFactory(
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun create(
        text: String?,
        attachments: List<AttachmentDraft>,
        source: String,
        status: String = InboxStatus.QUEUED,
        durationMillis: Long? = null,
    ): InboxItemEntity {
        val normalizedText = text?.trim()?.takeIf { it.isNotEmpty() }
        require(normalizedText != null || attachments.isNotEmpty()) {
            "Нужно добавить текст, голос или файл"
        }
        val timestamp = now()
        return InboxItemEntity(
            id = newId(),
            type = determineType(normalizedText, attachments),
            text = normalizedText,
            createdAt = timestamp,
            updatedAt = timestamp,
            status = status,
            source = source,
            retryCount = 0,
            lastError = null,
            remoteId = null,
            durationMillis = durationMillis?.coerceAtLeast(0L),
        )
    }

    fun createPlan(
        text: String?,
        attachments: List<AttachmentDraft>,
        source: String,
        status: String = InboxStatus.QUEUED,
        durationMillis: Long? = null,
    ): InboxDraftPlan = InboxDraftPlan(
        item = create(text, attachments, source, status, durationMillis),
        attachments = attachments.toList(),
    )

    fun determineType(text: String?, attachments: List<AttachmentDraft>): String {
        if (!text.isNullOrBlank() && attachments.isEmpty()) return InboxType.TEXT
        if (!text.isNullOrBlank() && attachments.isNotEmpty()) return InboxType.MIXED
        if (attachments.size != 1) return InboxType.MIXED

        val mime = attachments.single().mimeType.orEmpty().lowercase()
        return when {
            mime.startsWith("audio/") -> InboxType.VOICE
            mime.startsWith("image/") -> InboxType.IMAGE
            mime.startsWith("video/") -> InboxType.VIDEO
            mime == "application/pdf" || mime.startsWith("text/") -> InboxType.DOCUMENT
            else -> InboxType.FILE
        }
    }
}
