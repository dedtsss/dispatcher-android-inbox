package ru.bruce.dispatcherinbox.drive

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import kotlin.coroutines.resumeWithException
import java.util.concurrent.TimeUnit

class DriveHttpException(val code: Int, val reason: String) : IOException("Drive HTTP $code: $reason")

class DriveRestClient(
    private val token: String,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.MINUTES).build(),
    private val base: String = "https://www.googleapis.com/",
) {
    suspend fun json(path: String, method: String = "GET", body: JsonObject? = null): JsonObject =
        execute(Request.Builder().url(base + path).method(method, body?.toString()?.toRequestBody(JSON)))

    suspend fun execute(builder: Request.Builder): JsonObject {
        response(builder.header("Authorization", "Bearer $token").build()).use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw failure(response.code, text)
            }
            return if (text.isBlank()) JsonObject() else JsonParser.parseString(text).asJsonObject
        }
    }

    suspend fun generateId(): String = json("drive/v3/files/generateIds?count=1&space=drive")["ids"].asJsonArray[0].asString

    suspend fun metadata(id: String): JsonObject? = try {
        json("drive/v3/files/$id?fields=id,size,md5Checksum,trashed")
    } catch (e: DriveHttpException) { if (e.code == 404) null else throw e }

    suspend fun findFolder(name: String, parent: String): String? {
        val query = "name = '$name' and '$parent' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val url = (base + "drive/v3/files").toHttpUrl().newBuilder()
            .addQueryParameter("q", query).addQueryParameter("fields", "files(id)")
            .addQueryParameter("orderBy", "createdTime").build()
        return execute(Request.Builder().url(url))["files"].asJsonArray.firstOrNull()?.asJsonObject?.get("id")?.asString
    }

    suspend fun createFolder(id: String, name: String, parent: String) {
        json("drive/v3/files", "POST", metadataBody(id, name, parent).apply {
            addProperty("mimeType", "application/vnd.google-apps.folder")
        })
    }

    suspend fun upload(id: String, name: String, parent: String, mime: String, content: RequestBody) {
        val request = Request.Builder().url(base + "upload/drive/v3/files?uploadType=resumable")
            .header("Authorization", "Bearer $token")
            .header("X-Upload-Content-Type", mime)
            .header("X-Upload-Content-Length", content.contentLength().toString())
            .post(metadataBody(id, name, parent).apply { addProperty("mimeType", mime) }.toString().toRequestBody(JSON)).build()
        val location = response(request).use {
            if (!it.isSuccessful) throw failure(it.code, it.body?.string().orEmpty())
            it.header("Location") ?: throw IOException("Drive не вернул адрес загрузки")
        }
        // URL сессии выдаёт только Drive; токен не отправляется сторонним узлам.
        require(location.toHttpUrl().host == base.toHttpUrl().host)
        execute(Request.Builder().url(location).put(content))
    }

    private suspend fun response(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    private fun metadataBody(id: String, name: String, parent: String) = JsonObject().apply {
        addProperty("id", id); addProperty("name", name)
        add("parents", com.google.gson.JsonArray().apply { add(parent) })
    }

    private fun failure(code: Int, text: String): DriveHttpException {
        val reason = runCatching {
            JsonParser.parseString(text).asJsonObject.getAsJsonObject("error")
                .getAsJsonArray("errors")[0].asJsonObject["reason"].asString
        }.getOrDefault("")
        return DriveHttpException(code, reason)
    }

    companion object { private val JSON = "application/json; charset=utf-8".toMediaType() }
}
