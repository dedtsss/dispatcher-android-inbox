package ru.bruce.dispatcherinbox.drive

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import ru.bruce.dispatcherinbox.data.*
import ru.bruce.dispatcherinbox.network.UploadResult
import java.io.File
import java.security.MessageDigest

class DriveGatewayTest {
    private val account = "capture@example.test"
    private val settings = AppSettings(driveAccount = account)
    private fun entry(text: String? = "  исходный текст\n", files: List<InboxAttachmentEntity> = emptyList()) = InboxItemWithAttachments(
        InboxItemEntity("event-1", "text", text, 1700000000000, 1700000000000, "queued", "manual", 0, null, null), files,
    )
    private class Ids : DriveIdStore {
        val saved = mutableMapOf<String, String>()
        override suspend fun get(key: String) = saved[key]
        override suspend fun put(key: String, id: String) { saved[key] = id }
    }
    private inner class Server : AutoCloseable {
        val server = MockWebServer()
        val files = mutableMapOf<String, JsonObject>()
        val bytes = mutableMapOf<String, ByteArray>()
        var nextId = 0
        var failUpload = false
        var disconnectUpload = false
        var rejectCode = 0
        var stallUpload = false
        @Volatile var uploads = 0
        val ids = Ids()
        val gateway: DriveGateway
        init {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (rejectCode != 0) return MockResponse().setResponseCode(rejectCode)
                    val path = request.requestUrl!!.encodedPath
                    if (path.endsWith("/about")) return ok("""{"user":{"emailAddress":"$account"}}""")
                    if (path.endsWith("/generateIds")) return ok("""{"ids":["id-${++nextId}"]}""")
                    if (path == "/drive/v3/files" && request.method == "GET") return ok("""{"files":[]}""")
                    if (path == "/drive/v3/files" && request.method == "POST") {
                        val metadata = JsonParser.parseString(request.body.readUtf8()).asJsonObject
                        files[metadata["id"].asString] = metadata
                        return ok(metadata.toString())
                    }
                    if (path.startsWith("/drive/v3/files/")) {
                        return files[path.substringAfterLast('/')]?.let { ok(it.toString()) }
                            ?: MockResponse().setResponseCode(404)
                    }
                    if (path == "/upload/drive/v3/files") {
                        val metadata = JsonParser.parseString(request.body.readUtf8()).asJsonObject
                        val id = metadata["id"].asString
                        // ID должен быть сохранён до первого запроса, который создаёт оригинал.
                        assertTrue(ids.saved.containsValue(id))
                        files["session-$id"] = metadata
                        return ok("{}").addHeader("Location", server.url("/session/$id"))
                    }
                    if (path.startsWith("/session/")) {
                        val id = path.substringAfterLast('/')
                        val content = request.body.readByteArray()
                        val metadata = files.remove("session-$id")!!
                        metadata.addProperty("size", content.size)
                        metadata.addProperty("md5Checksum", md5(content))
                        files[id] = metadata
                        bytes[id] = content
                        uploads++
                        // Моделируем подтверждённую сервером запись, ответ о которой клиент потерял.
                        if (stallUpload) { stallUpload = false; return MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE) }
                        if (disconnectUpload) { disconnectUpload = false; return MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST) }
                        if (failUpload) { failUpload = false; return MockResponse().setResponseCode(503) }
                        return ok(metadata.toString())
                    }
                    return MockResponse().setResponseCode(400)
                }
            }
            server.start()
            gateway = DriveGateway(ids, { "test-token" }, { DriveRestClient(it, client = okhttp3.OkHttpClient.Builder().retryOnConnectionFailure(false).build(), base = server.url("/").toString()) })
        }
        override fun close() = server.shutdown()
        private fun ok(body: String) = MockResponse().setBody(body)
    }

    @Test fun `текст сохраняется без изменений в видимый Inbox`() = runTest {
        Server().use { s ->
            assertTrue(s.gateway.upload(entry(), settings) is UploadResult.Success)
            assertArrayEquals("  исходный текст\n".toByteArray(), s.bytes.values.single())
            assertEquals(listOf("Catching", "Inbox"), s.files.values.filter { it["mimeType"]?.asString == "application/vnd.google-apps.folder" }.map { it["name"].asString })
            assertTrue(s.files.values.any { it["name"]?.asString?.startsWith("2023-11-14_221320_000_event-1_") == true })
        }
    }

    @Test fun `повтор после потерянного ответа не создаёт дубль`() = runTest {
        Server().use { s ->
            s.failUpload = true
            val first = s.gateway.upload(entry(), settings)
            assertTrue(first is UploadResult.Failure && first.retryable)
            val reserved = s.ids.saved.toMap()
            assertTrue(s.gateway.upload(entry(), settings) is UploadResult.Success)
            assertEquals(reserved, s.ids.saved)
            assertEquals(1, s.uploads)
        }
    }

    @Test fun `разрыв TCP после записи восстанавливается новым экземпляром destination`() = runTest {
        Server().use { s ->
            s.disconnectUpload = true
            val result = s.gateway.upload(entry(), settings)
            assertTrue(result is UploadResult.Failure && result.retryable)
            val restarted = DriveGateway(s.ids, { "test-token" }, { DriveRestClient(it, base = s.server.url("/").toString()) })
            assertTrue(restarted.upload(entry(), settings) is UploadResult.Success)
            assertEquals(1, s.uploads)
        }
    }

    @Test fun `отмена worker прерывает HTTP и оставляет ID для безопасного повтора`() = runTest {
        Server().use { s ->
            s.stallUpload = true
            val task = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                s.gateway.upload(entry(), settings)
                fail("Отменённая загрузка не должна возвращать успех")
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                repeat(100) { if (s.uploads == 0) Thread.sleep(50) }
            }
            assertEquals(1, s.uploads)
            task.cancel()
            task.join()
            assertTrue(s.gateway.upload(entry(), settings) is UploadResult.Success)
            assertEquals(1, s.uploads)
        }
    }

    @Test fun `связанные текст и файл сохраняются в одну папку с исходными байтами`() = runTest {
        val file = File.createTempFile("capture", ".pdf").apply { writeBytes(byteArrayOf(0, 1, 2, -1, 10)) }
        try {
            Server().use { s ->
                val item = entry(files = listOf(InboxAttachmentEntity("a", "event-1", file.path, "application/pdf", "doc.pdf", file.length(), null)))
                assertTrue(s.gateway.upload(item, settings) is UploadResult.Success)
                assertEquals(2, s.uploads)
                assertTrue(s.bytes.values.any { it.contentEquals(file.readBytes()) })
                assertTrue(file.exists())
                val parentIds = s.files.values.filter { it.has("md5Checksum") }.map { it["parents"].asJsonArray[0].asString }.toSet()
                assertEquals(1, parentIds.size)
                assertEquals("2023-11-14_221320_000_event-1", s.files[parentIds.single()]!!["name"].asString)
            }
        } finally { file.delete() }
    }

    @Test fun `ошибки авторизации квоты и сети не подтверждают загрузку`() = runTest {
        for (code in listOf(401, 403, 429, 503)) Server().use { s ->
            s.rejectCode = code
            val result = s.gateway.upload(entry(), settings)
            assertTrue(result is UploadResult.Failure && result.retryable)
            assertEquals(0, s.uploads)
        }
    }

    @Test fun `чужой аккаунт и отсутствие OAuth не дают ложного успеха`() = runTest {
        Server().use { s ->
            assertTrue(s.gateway.upload(entry(), AppSettings(driveAccount = "other@example.test")) is UploadResult.Failure)
            assertEquals(0, s.uploads)
        }
        val gateway = DriveGateway(Ids(), { error("Подключите Google Drive") })
        assertTrue(gateway.upload(entry(), AppSettings()) is UploadResult.Failure)
    }

    @Test fun `изменённый удалённый оригинал не перезаписывается`() = runTest {
        Server().use { s ->
            s.gateway.upload(entry(), settings)
            s.files.values.single { it.has("md5Checksum") }.addProperty("md5Checksum", "changed")
            assertTrue(s.gateway.upload(entry(), settings) is UploadResult.Failure)
            assertEquals(1, s.uploads)
        }
    }

    private fun md5(bytes: ByteArray) = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
}
