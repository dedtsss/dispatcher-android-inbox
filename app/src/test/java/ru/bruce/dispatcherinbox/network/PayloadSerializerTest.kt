package ru.bruce.dispatcherinbox.network

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.bruce.dispatcherinbox.data.InboxItemEntity
import ru.bruce.dispatcherinbox.data.InboxSource
import ru.bruce.dispatcherinbox.data.InboxStatus
import ru.bruce.dispatcherinbox.data.InboxType

class PayloadSerializerTest {
    @Test
    fun `serializes dispatcher contract`() {
        val item = InboxItemEntity(
            id = "client-42",
            type = InboxType.TEXT,
            text = "Заметка",
            createdAt = 0L,
            updatedAt = 0L,
            status = InboxStatus.QUEUED,
            source = InboxSource.MANUAL,
            retryCount = 0,
            lastError = null,
            remoteId = null,
        )
        val serializer = PayloadSerializer()

        val json = JsonParser.parseString(serializer.serialize(serializer.from(item, "0.1-test"))).asJsonObject

        assertEquals("client-42", json["clientItemId"].asString)
        assertEquals("text", json["type"].asString)
        assertEquals("1970-01-01T00:00:00Z", json["createdAt"].asString)
        assertEquals("android", json["metadata"].asJsonObject["platform"].asString)
        assertEquals("0.1-test", json["metadata"].asJsonObject["appVersion"].asString)
    }
}
