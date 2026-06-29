package ru.bruce.dispatcherinbox.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

class AttachmentStore(private val context: Context) {
    private val stagingDir = File(context.filesDir, "inbox-staging")
    private val itemsDir = File(context.filesDir, "inbox-items")

    suspend fun stage(uri: Uri): AttachmentDraft = withContext(Dispatchers.IO) {
        stagingDir.mkdirs()
        val metadata = readMetadata(context.contentResolver, uri)
        val safeName = sanitizeFileName(metadata.name ?: "attachment")
        val id = UUID.randomUUID().toString()
        val target = File(stagingDir, "$id-$safeName")
        val digest = MessageDigest.getInstance("SHA-256")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                    }
                }
            } ?: error("Не удалось открыть выбранный файл")
        } catch (error: Exception) {
            target.delete()
            throw error
        }

        AttachmentDraft(
            id = id,
            localPath = target.absolutePath,
            mimeType = context.contentResolver.getType(uri),
            originalName = metadata.name,
            sizeBytes = target.length(),
            checksum = digest.digest().joinToString("") { "%02x".format(it) },
        )
    }

    suspend fun createVoiceDraft(file: File): AttachmentDraft = withContext(Dispatchers.IO) {
        require(file.exists() && file.length() > 0) { "Аудиозапись пуста" }
        AttachmentDraft(
            localPath = file.absolutePath,
            mimeType = "audio/mp4",
            originalName = file.name,
            sizeBytes = file.length(),
            checksum = sha256(file),
        )
    }

    suspend fun commit(itemId: String, draft: AttachmentDraft): InboxAttachmentEntity =
        withContext(Dispatchers.IO) {
            val source = File(draft.localPath)
            require(source.exists()) { "Локальный файл больше недоступен" }
            val targetDir = File(itemsDir, itemId).apply { mkdirs() }
            val fileName = "${draft.id}-${sanitizeFileName(draft.originalName ?: source.name)}"
            val target = File(targetDir, fileName)
            if (!source.renameTo(target)) {
                FileInputStream(source).use { input ->
                    FileOutputStream(target).use(input::copyTo)
                }
                source.delete()
            }
            InboxAttachmentEntity(
                id = draft.id,
                itemId = itemId,
                localPath = target.absolutePath,
                mimeType = draft.mimeType,
                originalName = draft.originalName,
                sizeBytes = target.length(),
                checksum = draft.checksum,
            )
        }

    suspend fun deleteDraft(draft: AttachmentDraft) = withContext(Dispatchers.IO) {
        File(draft.localPath).delete()
    }

    suspend fun deleteAttachments(attachments: List<InboxAttachmentEntity>) =
        withContext(Dispatchers.IO) {
            attachments.forEach { File(it.localPath).delete() }
            attachments.map { File(it.localPath).parentFile }.distinct().forEach { directory ->
                directory?.takeIf { it.isDirectory && it.listFiles().isNullOrEmpty() }?.delete()
            }
        }

    fun newVoiceFile(): File {
        stagingDir.mkdirs()
        return File(stagingDir, "voice-${System.currentTimeMillis()}.m4a")
    }

    private fun readMetadata(resolver: ContentResolver, uri: Uri): FileMetadata {
        var name: String? = null
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        return FileMetadata(name, size)
    }

    private fun sanitizeFileName(value: String): String =
        value.replace(Regex("[^A-Za-zА-Яа-я0-9._ -]"), "_").take(120).ifBlank { "attachment" }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class FileMetadata(val name: String?, val size: Long?)
}
