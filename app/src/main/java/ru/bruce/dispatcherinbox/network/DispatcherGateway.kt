package ru.bruce.dispatcherinbox.network

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.bruce.dispatcherinbox.data.AppSettings
import ru.bruce.dispatcherinbox.data.InboxItemWithAttachments
import ru.bruce.dispatcherinbox.sync.SyncRetryPolicy
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface UploadResult {
    data class Success(val remoteId: String) : UploadResult
    data class Failure(val message: String, val retryable: Boolean) : UploadResult
}

interface DispatcherGateway {
    suspend fun upload(item: InboxItemWithAttachments, settings: AppSettings): UploadResult
}

class RetrofitDispatcherGateway(
    private val payloadSerializer: PayloadSerializer = PayloadSerializer(),
) : DispatcherGateway {
    override suspend fun upload(
        item: InboxItemWithAttachments,
        settings: AppSettings,
    ): UploadResult = withContext(Dispatchers.IO) {
        if (settings.bearerToken.isBlank()) {
            return@withContext UploadResult.Failure("Укажите Device/Bearer token в настройках", false)
        }
        val baseUrl = normalizeBaseUrl(settings.apiBaseUrl)
            ?: return@withContext UploadResult.Failure("Некорректный Dispatcher API URL", false)

        val missingFile = item.attachments.firstOrNull { !File(it.localPath).isFile }
        if (missingFile != null) {
            return@withContext UploadResult.Failure(
                "Локальный файл ${missingFile.originalName ?: missingFile.id} не найден",
                false,
            )
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
            .create(DispatcherApi::class.java)

        val payloadJson = payloadSerializer.serialize(payloadSerializer.from(item.item))
        val payload = payloadJson.toRequestBody("application/json; charset=utf-8".toMediaType())
        val files = item.attachments.map { attachment ->
            val file = File(attachment.localPath)
            MultipartBody.Part.createFormData(
                "files[]",
                attachment.originalName ?: file.name,
                file.asRequestBody(attachment.mimeType?.toMediaTypeOrNull()),
            )
        }

        try {
            val response = api.uploadItem("Bearer ${settings.bearerToken}", payload, files)
            val body = response.body()
            if (response.isSuccessful && body?.ok == true && !body.remoteId.isNullOrBlank()) {
                UploadResult.Success(body.remoteId)
            } else {
                UploadResult.Failure(
                    message = "Сервер вернул HTTP ${response.code()}",
                    retryable = SyncRetryPolicy.isRetryableHttp(response.code()),
                )
            }
        } catch (error: IOException) {
            UploadResult.Failure("Нет связи с Диспетчером", true)
        } catch (error: Exception) {
            UploadResult.Failure("Не удалось отправить запись", false)
        }
    }

    private fun normalizeBaseUrl(value: String): String? {
        val trimmed = value.trim()
        if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) return null
        return if (trimmed.endsWith('/')) trimmed else "$trimmed/"
    }
}

class DebugFakeDispatcherGateway : DispatcherGateway {
    override suspend fun upload(
        item: InboxItemWithAttachments,
        settings: AppSettings,
    ): UploadResult = UploadResult.Success("debug-${item.item.id}")
}
