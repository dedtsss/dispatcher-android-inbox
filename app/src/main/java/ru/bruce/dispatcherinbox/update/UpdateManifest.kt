package ru.bruce.dispatcherinbox.update

import com.google.gson.JsonParser
import java.net.URI

const val UPDATE_PACKAGE = "ru.bruce.dispatcherinbox"

data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val packageName: String,
    val signerSha256: String,
) {
    fun toJson(): String = com.google.gson.Gson().toJson(this)

    companion object {
        fun parse(json: String): UpdateManifest {
            val root = JsonParser.parseString(json).asJsonObject
            fun string(key: String): String {
                val value = root.get(key)
                require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Некорректное поле $key" }
                return value.asString.also { require(it.isNotBlank()) { "Пустое поле $key" } }
            }
            val version = root.get("versionCode")
            require(version != null && version.isJsonPrimitive && version.asJsonPrimitive.isNumber && version.toString().matches(Regex("[1-9][0-9]*"))) { "Некорректный versionCode" }
            fun digest(key: String) = string(key).lowercase().also {
                require(it.matches(Regex("[0-9a-f]{64}"))) { "Некорректный $key" }
            }
            return UpdateManifest(
                version.asLong.also { require(it > 0 && it.toString() == version.toString()) },
                string("versionName"),
                string("apkUrl").also(::requireHttps),
                digest("sha256"),
                string("packageName").also { require(it == UPDATE_PACKAGE) { "Чужой пакет обновления" } },
                digest("signerSha256"),
            )
        }
    }
}

fun requireHttps(value: String) {
    val uri = URI(value)
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.fragment == null) { "Нужен HTTPS URL без учётных данных" }
}

data class ApkIdentity(val packageName: String, val versionCode: Long, val signerSha256: Set<String>)

object UpdateValidation {
    fun requireValid(manifest: UpdateManifest, hash: String, installed: ApkIdentity, candidate: ApkIdentity) {
        require(hash == manifest.sha256) { "SHA-256 APK не совпадает" }
        require(installed.packageName == UPDATE_PACKAGE && candidate.packageName == UPDATE_PACKAGE && candidate.packageName == manifest.packageName) { "Имя пакета APK не совпадает" }
        require(candidate.versionCode == manifest.versionCode && candidate.versionCode > installed.versionCode) { "APK должен быть новее установленной версии" }
        require(installed.signerSha256.isNotEmpty() && candidate.signerSha256 == installed.signerSha256 && candidate.signerSha256 == setOf(manifest.signerSha256)) { "Подпись APK не совпадает" }
    }
}

enum class UpdateStatus { CURRENT, AVAILABLE, DOWNLOADING, PAUSED, FAILED, READY }

fun downloadStatus(status: Int): UpdateStatus = when (status) {
    1, 2 -> UpdateStatus.DOWNLOADING
    4 -> UpdateStatus.PAUSED
    8 -> UpdateStatus.READY
    else -> UpdateStatus.FAILED
}
