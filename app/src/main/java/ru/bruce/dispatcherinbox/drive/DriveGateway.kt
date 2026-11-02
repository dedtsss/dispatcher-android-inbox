package ru.bruce.dispatcherinbox.drive

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import ru.bruce.dispatcherinbox.data.AppSettings
import ru.bruce.dispatcherinbox.data.InboxItemWithAttachments
import ru.bruce.dispatcherinbox.network.DispatcherGateway
import ru.bruce.dispatcherinbox.network.UploadResult
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class DriveGateway(
    private val ids: DriveIdStore,
    private val tokenProvider: suspend (AppSettings) -> String,
    private val clientFactory: (String) -> DriveRestClient = { DriveRestClient(it) },
    private val invalidateToken: suspend (String) -> Unit = {},
    private val reportStatus: suspend (String) -> Unit = {},
) : DispatcherGateway {
    override suspend fun upload(item: InboxItemWithAttachments, settings: AppSettings): UploadResult = withContext(Dispatchers.IO) {
        var token: String? = null
        try {
            token = tokenProvider(settings)
            val drive = clientFactory(token)
            val account = drive.json("drive/v3/about?fields=user(emailAddress)")["user"].asJsonObject["emailAddress"].asString
            check(account == settings.driveAccount) { "Аккаунт Drive изменился. Подключите его повторно" }
            suspend fun reserved(key: String): String = ids.get("$account/$key") ?: drive.generateId().also {
                ids.put("$account/$key", it)
            }
            suspend fun folder(key: String, name: String, parent: String, discover: Boolean): String {
                val existing = ids.get("$account/$key")
                if (existing != null) {
                    val metadata = drive.metadata(existing)
                    if (metadata != null) {
                        check(metadata["trashed"]?.asBoolean != true) { "Каталог Drive находится в корзине. Восстановите его" }
                        return existing
                    }
                }
                val id = if (existing == null && discover) drive.findFolder(name, parent) else null
                if (id != null) { ids.put("$account/$key", id); return id }
                val allocated = reserved(key)
                try { drive.createFolder(allocated, name, parent) }
                catch (e: DriveHttpException) { if (e.code != 409 || drive.metadata(allocated) == null) throw e }
                return allocated
            }
            val root = folder("root", "Catching", "root", true)
            val inbox = folder("inbox", "Inbox", root, true)
            val prefix = FORMAT.format(Instant.ofEpochMilli(item.item.createdAt)) + "_" + item.item.id
            val parts = mutableListOf<RawPart>()
            item.item.text?.let { text ->
                val bytes = text.toByteArray(Charsets.UTF_8)
                parts += RawPart("text", "note.txt", "text/plain", bytes.toRequestBody("text/plain; charset=utf-8".toMediaType()), md5(bytes))
            }
            item.attachments.sortedBy { it.id }.forEach { attachment ->
                val file = File(attachment.localPath)
                check(file.isFile) { "Локальный оригинал не найден: ${attachment.originalName ?: attachment.id}" }
                val mime = attachment.mimeType ?: "application/octet-stream"
                val digest = file.inputStream().use { input ->
                    val md = MessageDigest.getInstance("MD5")
                    val buffer = ByteArray(65536)
                    while (true) { val n = input.read(buffer); if (n < 0) break; md.update(buffer, 0, n) }
                    md.digest().hex()
                }
                val name = (attachment.originalName ?: "file").replace(Regex("[/\\\\\\p{Cntrl}]"), "_")
                parts += RawPart(attachment.id, name, mime, file.asRequestBody(mime.toMediaType()), digest)
            }
            check(parts.isNotEmpty()) { "Пустая запись" }
            val parent = if (parts.size > 1) folder("event/${item.item.id}", prefix, inbox, false) else inbox
            val uploaded = parts.mapIndexed { index, part ->
                val id = reserved("item/${item.item.id}/${part.key}")
                suspend fun confirmed(): Boolean {
                    val metadata = drive.metadata(id) ?: return false
                    check(metadata["trashed"]?.asBoolean != true) { "Оригинал Drive находится в корзине. Восстановите его" }
                    check(metadata["size"]?.asLong == part.body.contentLength() && metadata["md5Checksum"]?.asString == part.md5) {
                        "Содержимое Drive не совпадает с локальным оригиналом; оригинал сохранён"
                    }
                    return true
                }
                if (!confirmed()) {
                    val name = if (parts.size == 1) "${prefix}_${part.name}" else "%03d_%s".format(index + 1, part.name)
                    try { drive.upload(id, name, parent, part.mime, part.body) }
                    catch (e: DriveHttpException) { if (e.code != 409) throw e }
                    check(confirmed()) { "Drive не подтвердил сохранение оригинала" }
                }
                id
            }
            reportStatus("Подключён · Catching/Inbox")
            UploadResult.Success(if (parts.size > 1) parent else uploaded.single())
        } catch (e: CancellationException) { throw e }
        catch (e: DriveHttpException) {
            if (e.code == 401) token?.let { runCatching { invalidateToken(it) } }
            val message = when (e.code) {
                401 -> "Сессия Google истекла. Подключите Drive повторно"
                403 -> if (e.reason.contains("quota", true) || e.reason == "storageQuotaExceeded") "Недостаточно места или квоты Google Drive. Освободите место и повторите" else "Доступ к Google Drive отклонён. Проверьте разрешения и подключите повторно"
                429 -> "Лимит запросов Google Drive. Повторим автоматически"
                else -> "Ошибка Google Drive HTTP ${e.code}. Оригинал сохранён"
            }
            reportStatus(message)
            UploadResult.Failure(message, e.code == 401 || e.code == 403 || e.code == 408 || e.code == 429 || e.code >= 500)
        } catch (e: IOException) {
            UploadResult.Failure("Нет связи с Google Drive. Повторим автоматически", true)
        } catch (e: Exception) {
            val message = e.message ?: "Подключите Google Drive повторно"
            reportStatus(message)
            UploadResult.Failure(message, false)
        }
    }

    private data class RawPart(val key: String, val name: String, val mime: String, val body: RequestBody, val md5: String)
    companion object {
        private val FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss_SSS").withZone(ZoneOffset.UTC)
        private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
        private fun md5(bytes: ByteArray) = MessageDigest.getInstance("MD5").digest(bytes).hex()
    }
}
