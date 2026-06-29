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
    fun `creates queued text item`() {
        val item = factory.create("  Купить молоко  ", emptyList(), InboxSource.MANUAL)

        assertEquals("item-1", item.id)
        assertEquals(InboxType.TEXT, item.type)
        assertEquals("Купить молоко", item.text)
        assertEquals(InboxStatus.QUEUED, item.status)
        assertEquals(1_700_000_000_000L, item.createdAt)
        assertNull(item.lastError)
    }

    @Test
    fun `creates mixed item for text with attachment`() {
        val attachment = AttachmentDraft(
            localPath = "/tmp/photo.jpg",
            mimeType = "image/jpeg",
            originalName = "photo.jpg",
            sizeBytes = 42,
            checksum = "abc",
        )

        val item = factory.create("Посмотри", listOf(attachment), InboxSource.SHARE)

        assertEquals(InboxType.MIXED, item.type)
        assertEquals(InboxSource.SHARE, item.source)
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
}
