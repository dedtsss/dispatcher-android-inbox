package ru.bruce.dispatcherinbox.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalPresentationTest {
    @Test
    fun `text and photo render mixed summary`() {
        val presentation = entry("Текст заметки", attachment("photo.jpg", "image/jpeg")).toJournalPresentation()

        assertEquals("Текст + Фото", presentation.typeSummary)
        assertEquals(listOf(JournalKind.TEXT, JournalKind.PHOTO), presentation.kinds)
    }

    @Test
    fun `text and file render mixed summary`() {
        val presentation = entry("Договор", attachment("contract.pdf", "application/pdf")).toJournalPresentation()

        assertEquals("Текст + Файл", presentation.typeSummary)
    }

    @Test
    fun `multiple attachments include counts`() {
        val presentation = entry(
            "Материалы",
            attachment("one.pdf", "application/pdf"),
            attachment("two.zip", "application/zip"),
        ).toJournalPresentation()

        assertEquals("Текст + 2 файла", presentation.typeSummary)
    }

    @Test
    fun `voice item includes duration`() {
        val presentation = entry(
            text = null,
            attachment("voice.m4a", "audio/mp4"),
            durationMillis = 63_800L,
        ).toJournalPresentation()

        assertEquals("Голос", presentation.typeSummary)
        assertEquals("01:03", presentation.duration)
        assertTrue(JournalKind.VOICE in presentation.kinds)
    }

    private fun entry(
        text: String?,
        vararg attachments: InboxAttachmentEntity,
        durationMillis: Long? = null,
    ) = InboxItemWithAttachments(
        item = InboxItemEntity(
            id = "item",
            type = if (text != null && attachments.isNotEmpty()) InboxType.MIXED else InboxType.VOICE,
            text = text,
            createdAt = 1L,
            updatedAt = 1L,
            status = InboxStatus.QUEUED,
            source = InboxSource.MANUAL,
            retryCount = 0,
            lastError = null,
            remoteId = null,
            durationMillis = durationMillis,
        ),
        attachments = attachments.toList(),
    )

    private fun attachment(name: String, mime: String) = InboxAttachmentEntity(
        id = name,
        itemId = "item",
        localPath = "/tmp/$name",
        mimeType = mime,
        originalName = name,
        sizeBytes = 10L,
        checksum = null,
    )
}
