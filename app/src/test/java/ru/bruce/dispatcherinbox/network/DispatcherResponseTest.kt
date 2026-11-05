package ru.bruce.dispatcherinbox.network

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DispatcherResponseTest {
    private val gson = Gson()

    @Test
    fun `parses production remote id response`() {
        val response = gson.fromJson(
            """{"ok":true,"remote_id":"rec123","status":"stored"}""",
            DispatcherResponse::class.java,
        )

        assertTrue(response.ok)
        assertEquals("rec123", response.remoteId)
        assertEquals("stored", response.status)
    }

    @Test
    fun `keeps compatibility with legacy dispatcher item id`() {
        val response = gson.fromJson(
            """{"ok":true,"dispatcherItemId":"legacy-123"}""",
            DispatcherResponse::class.java,
        )

        assertTrue(response.ok)
        assertEquals("legacy-123", response.remoteId)
    }
}
