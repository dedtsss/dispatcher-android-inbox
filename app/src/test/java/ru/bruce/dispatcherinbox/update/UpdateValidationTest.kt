package ru.bruce.dispatcherinbox.update

import org.junit.Assert.*
import org.junit.Test

class UpdateValidationTest {
    private val hash = "ab".repeat(32)
    private val signer = "cd".repeat(32)
    private val manifest = UpdateManifest(9, "0.6.3", "https://updates.example/catching.apk", hash, UPDATE_PACKAGE, signer)
    private val installed = ApkIdentity(UPDATE_PACKAGE, 8, setOf(signer))
    private val candidate = ApkIdentity(UPDATE_PACKAGE, 9, setOf(signer))
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Ожидался отказ проверки") } catch (_: IllegalArgumentException) { }
    }

    @Test fun parsesCompleteManifestAndNormalizesDigest() {
        assertEquals(manifest, UpdateManifest.parse(manifest.toJson()))
        assertEquals(hash, UpdateManifest.parse(manifest.toJson().replace(hash, hash.uppercase())).sha256)
    }
    @Test fun rejectsInvalidManifest() {
        rejects { UpdateManifest.parse("{}") }
        rejects { UpdateManifest.parse(manifest.toJson().replace("https:", "http:")) }
        rejects { UpdateManifest.parse(manifest.toJson().replace(hash, "bad")) }
        rejects { UpdateManifest.parse(manifest.toJson().replace("\"versionCode\":9", "\"versionCode\":9.1")) }
        rejects { UpdateManifest.parse(manifest.toJson().replace("\"versionCode\":9", "\"versionCode\":\"9\"")) }
        rejects { UpdateManifest.parse(manifest.toJson().replace(UPDATE_PACKAGE, "wrong.package")) }
        rejects { UpdateManifest.parse(manifest.toJson().replace("\"versionCode\":9", "\"versionCode\":99999999999999999999999999")) }
        rejects { requireHttps("https://token@updates.example/manifest") }
    }
    @Test fun allowsNewMatchingApk() = UpdateValidation.requireValid(manifest, hash, installed, candidate)
    @Test fun rejectsBadHash() = rejects { UpdateValidation.requireValid(manifest, "ef".repeat(32), installed, candidate) }
    @Test fun rejectsWrongPackage() = rejects { UpdateValidation.requireValid(manifest, hash, installed, candidate.copy(packageName = "other")) }
    @Test fun rejectsWrongSigner() = rejects { UpdateValidation.requireValid(manifest, hash, installed, candidate.copy(signerSha256 = setOf(hash))) }
    @Test fun rejectsManifestSignerMismatch() = rejects { UpdateValidation.requireValid(manifest.copy(signerSha256 = hash), hash, installed, candidate) }
    @Test fun rejectsInstalledSignerMismatch() = rejects { UpdateValidation.requireValid(manifest, hash, installed.copy(signerSha256 = setOf(hash)), candidate) }
    @Test fun rejectsEmptySigner() = rejects { UpdateValidation.requireValid(manifest, hash, installed.copy(signerSha256 = emptySet()), candidate.copy(signerSha256 = emptySet())) }
    @Test fun rejectsSameAndOlderVersion() {
        rejects { UpdateValidation.requireValid(manifest.copy(versionCode = 8), hash, installed, candidate.copy(versionCode = 8)) }
        rejects { UpdateValidation.requireValid(manifest.copy(versionCode = 7), hash, installed, candidate.copy(versionCode = 7)) }
        rejects { UpdateValidation.requireValid(manifest, hash, installed, candidate.copy(versionCode = 10)) }
    }
    @Test fun mapsAllDownloadStatusesIncludingMissingAndUnknown() {
        assertEquals(UpdateStatus.DOWNLOADING, downloadStatus(1))
        assertEquals(UpdateStatus.DOWNLOADING, downloadStatus(2))
        assertEquals(UpdateStatus.PAUSED, downloadStatus(4))
        assertEquals(UpdateStatus.READY, downloadStatus(8))
        assertEquals(UpdateStatus.FAILED, downloadStatus(16))
        assertEquals(UpdateStatus.FAILED, downloadStatus(0))
        assertEquals(UpdateStatus.FAILED, downloadStatus(99))
    }
}
