package ru.bruce.dispatcherinbox.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InboxItemFactoryTest {
    private val factory = InboxItemFactory(
        now = { 1_700_000_000_000L },
        newId = { "item-1" },
    )

    @Test
    fun `text only creates text item without attachments`() {
        val plan = factory.createPlan("  Купить молоко  ", emptyList(), InboxSource.MANUAL)
        val item = plan.item

        assertEquals("item-1", item.id)
        assertEquals(InboxType.TEXT, item.type)
        assertEquals("Купить молоко", item.text)
        assertEquals(InboxStatus.QUEUED, item.status)
        assertEquals(1_700_000_000_000L, item.createdAt)
        assertNull(item.lastError)
        assertEquals(emptyList<AttachmentDraft>(), plan.attachments)
    }

    @Test
    fun `photo only creates image item`() {
        val photo = attachment("photo.jpg", "image/jpeg")

        val plan = factory.createPlan(null, listOf(photo), InboxSource.MANUAL)

        assertEquals(InboxType.IMAGE, plan.item.type)
        assertNull(plan.item.text)
        assertEquals(listOf(photo), plan.attachments)
    }

    @Test
    fun `file only keeps its document type`() {
        val file = attachment("report.pdf", "application/pdf")

        val plan = factory.createPlan(null, listOf(file), InboxSource.MANUAL)

        assertEquals(InboxType.DOCUMENT, plan.item.type)
        assertNull(plan.item.text)
        assertEquals(listOf(file), plan.attachments)
    }

    @Test
    fun `text and photo create one mixed item preserving both`() {
        val photo = attachment("photo.jpg", "image/jpeg")

        val plan = factory.createPlan("Посмотри", listOf(photo), InboxSource.SHARE)

        assertEquals(InboxType.MIXED, plan.item.type)
        assertEquals("Посмотри", plan.item.text)
        assertEquals(listOf(photo), plan.attachments)
        assertEquals(InboxSource.SHARE, plan.item.source)
    }

    @Test
    fun `text and file create one mixed item preserving both`() {
        val file = attachment("report.pdf", "application/pdf")

        val plan = factory.createPlan("Отчёт", listOf(file), InboxSource.MANUAL)

        assertEquals(InboxType.MIXED, plan.item.type)
        assertEquals("Отчёт", plan.item.text)
        assertEquals(listOf(file), plan.attachments)
    }

    @Test
    fun `text and multiple files create one mixed item with every attachment`() {
        val attachments = listOf(
            attachment("photo.jpg", "image/jpeg"),
            attachment("report.pdf", "application/pdf"),
            attachment("notes.txt", "text/plain"),
        )

        val plan = factory.createPlan("Пакет документов", attachments, InboxSource.MANUAL)

        assertEquals(InboxType.MIXED, plan.item.type)
        assertEquals("Пакет документов", plan.item.text)
        assertEquals(attachments, plan.attachments)
    }

    @Test
    fun `detects voice item from MIME`() {
        val voice = AttachmentDraft(
            localPath = "/tmp/voice.m4a",
            mimeType = "audio/mp4",
            originalName = "voice.m4a",
            sizeBytes = 100,
            checksum = null,
        )

        assertEquals(InboxType.VOICE, factory.create(null, listOf(voice), InboxSource.ASSISTANT).type)
    }

    private fun attachment(name: String, mimeType: String) = AttachmentDraft(
        localPath = "/tmp/$name",
        mimeType = mimeType,
        originalName = name,
        sizeBytes = 42,
        checksum = "checksum-$name",
    )
}
