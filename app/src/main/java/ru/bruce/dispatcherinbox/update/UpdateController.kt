package ru.bruce.dispatcherinbox.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.bruce.dispatcherinbox.BuildConfig
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class UpdateState(val status: UpdateStatus = UpdateStatus.CURRENT, val message: String = "", val version: String = "", val canDownload: Boolean = status == UpdateStatus.AVAILABLE || status == UpdateStatus.FAILED)

class UpdateController(context: Context) {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val downloads = this.context.getSystemService(DownloadManager::class.java)
    companion object { private val mutex = Mutex() }
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).followSslRedirects(false).build()
    private val privateApk get() = File(context.filesDir, "updates/candidate.apk")
    private fun manifest() = prefs.getString("manifest", null)?.let(UpdateManifest::parse)
    private fun saveManifest(manifest: UpdateManifest) {
        check(prefs.edit().putString("manifest", manifest.toJson()).commit()) { "Не удалось сохранить обновление" }
    }

    suspend fun check(): UpdateState = withContext(Dispatchers.IO) { mutex.withLock {
        try {
            val existing = runCatching { recoverLocked() }.getOrNull()
            if (existing != null && existing.status in setOf(UpdateStatus.DOWNLOADING, UpdateStatus.PAUSED, UpdateStatus.READY)) return@withLock existing
            val url = BuildConfig.UPDATE_MANIFEST_URL
            if (url.isBlank()) return@withLock UpdateState(message = "Канал обновлений ещё не настроен")
            requireHttps(url)
            val manifest = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful) { "Сервер обновлений: ${response.code}" }
                val body = requireNotNull(response.body)
                require(body.contentLength() <= 65536) { "Слишком большой манифест" }
                val bytes = body.byteStream().use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (output.size() <= 65536) {
                        val count = input.read(buffer, 0, minOf(buffer.size, 65537 - output.size()))
                        if (count == -1) break
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                require(bytes.size <= 65536) { "Слишком большой манифест" }
                UpdateManifest.parse(bytes.toString(Charsets.UTF_8))
            }
            saveManifest(manifest)
            prefs.edit().remove("downloadId").apply()
            if (manifest.versionCode > installed().versionCode) UpdateState(UpdateStatus.AVAILABLE, "Доступно обновление", manifest.versionName)
            else UpdateState(UpdateStatus.CURRENT, "Установлена актуальная версия")
        } catch (e: Exception) { failed(e) }
    } }

    suspend fun download(): UpdateState = withContext(Dispatchers.IO) { mutex.withLock {
        try {
            val manifest = requireNotNull(manifest()) { "Сначала проверьте обновления" }
            require(manifest.versionCode > installed().versionCode) { "Обновление уже установлено" }
            val old = prefs.getLong("downloadId", -1)
            if (old != -1L) downloads.remove(old)
            privateApk.delete()
            val target = File(context.getExternalFilesDir(null) ?: error("Хранилище недоступно"), "updates/candidate.apk")
            target.parentFile?.mkdirs()
            check(!target.exists() || target.delete()) { "Не удалось заменить APK" }
            val request = DownloadManager.Request(Uri.parse(manifest.apkUrl))
                .setTitle("Обновление Catching ${manifest.versionName}")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(target))
                .setAllowedOverMetered(true).setAllowedOverRoaming(false)
            val id = downloads.enqueue(request)
            if (!prefs.edit().putLong("downloadId", id).putString("downloadPath", target.absolutePath).commit()) {
                downloads.remove(id)
                error("Не удалось сохранить загрузку")
            }
            recoverLocked()
        } catch (e: Exception) { failed(e) }
    } }

    suspend fun recover(): UpdateState = withContext(Dispatchers.IO) { mutex.withLock {
        try { recoverLocked() } catch (e: Exception) { failed(e) }
    } }

    private fun recoverLocked(): UpdateState {
        val manifest = manifest() ?: return UpdateState(message = if (BuildConfig.UPDATE_MANIFEST_URL.isBlank()) "Канал обновлений ещё не настроен" else "")
        if (manifest.versionCode <= installed().versionCode) return UpdateState(message = "Установлена актуальная версия")
        val id = prefs.getLong("downloadId", -1)
        if (id == -1L) return UpdateState(UpdateStatus.AVAILABLE, "Доступно обновление", manifest.versionName)
        downloads.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            check(cursor != null && cursor.moveToFirst()) { "Загрузка удалена. Повторите скачивание" }
            val status = downloadStatus(cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)))
            val message = when (status) {
                UpdateStatus.DOWNLOADING -> "Обновление скачивается"
                UpdateStatus.PAUSED -> "Загрузка приостановлена; Android продолжит её автоматически"
                UpdateStatus.FAILED -> "Ошибка загрузки: ${cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))}"
                UpdateStatus.READY -> {
                    validateDownloaded(manifest)
                    "Обновление проверено и готово к установке"
                }
                else -> ""
            }
            return UpdateState(status, message, manifest.versionName)
        }
    }

    private fun validateDownloaded(manifest: UpdateManifest) {
        val source = File(requireNotNull(prefs.getString("downloadPath", null)))
        check(source.isFile) { "APK не найден" }
        privateApk.parentFile?.mkdirs()
        source.copyTo(privateApk, overwrite = true)
        validate(privateApk, manifest)
    }

    private fun validate(file: File, manifest: UpdateManifest) {
        val hash = file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(65536)
            while (true) { val count = input.read(buffer); if (count == -1) break; digest.update(buffer, 0, count) }
            digest.digest().hex()
        }
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: error("APK не распознан Android")
        UpdateValidation.requireValid(manifest, hash, installed(), identity(archive))
    }

    private fun installed(): ApkIdentity {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return identity(context.packageManager.getPackageInfo(context.packageName, flags))
    }

    @Suppress("DEPRECATION")
    private fun identity(info: PackageInfo): ApkIdentity {
        val certificates = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return ApkIdentity(info.packageName, if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
            certificates.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }.toSet())
    }

    suspend fun install(): UpdateState = withContext(Dispatchers.IO) { mutex.withLock {
        try {
            val state = recoverLocked()
            check(state.status == UpdateStatus.READY) { "Загрузка ещё не готова к установке" }
            val manifest = requireNotNull(manifest())
            validate(privateApk, manifest)
            if (!context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return@withLock UpdateState(UpdateStatus.READY, "Разрешите установку и снова нажмите «Установить»", manifest.versionName)
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", privateApk)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
            UpdateState(UpdateStatus.READY, "Открыта системная установка", manifest.versionName)
        } catch (e: Exception) { failed(e) }
    } }

    private fun failed(e: Exception) = UpdateState(UpdateStatus.FAILED, e.message ?: "Ошибка обновления",
        canDownload = runCatching { manifest()?.versionCode?.let { it > installed().versionCode } == true }.getOrDefault(false))
}

private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
