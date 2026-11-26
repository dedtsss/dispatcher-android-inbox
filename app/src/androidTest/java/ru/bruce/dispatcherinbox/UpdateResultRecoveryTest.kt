package ru.bruce.dispatcherinbox

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.bruce.dispatcherinbox.update.UpdateController
import ru.bruce.dispatcherinbox.update.UpdateManifest
import ru.bruce.dispatcherinbox.update.UpdateStatus

/** Восстановление результата проверки не должно превращать ошибку или запуск installer в успех. */
@RunWith(AndroidJUnit4::class)
class UpdateResultRecoveryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val manifest = UpdateManifest(BuildConfig.VERSION_CODE + 1L, "следующая", "https://updates.example/catching.apk",
        "ab".repeat(32), context.packageName, "cd".repeat(32))

    @Before fun prepare() { prefs.edit().clear().commit() }
    @After fun clear() { prefs.edit().clear().commit() }

    @Test fun noNetworkCheckDoesNotInventCheckedTime() = runBlocking {
        val state = UpdateController(context).recover()
        assertNull(state.checkedAtMillis)
        assertTrue(state.checkResult.isEmpty())
        assertFalse(state.canDownload)
    }

    @Test fun failedCheckSurvivesNewControllerWithoutInventingAnUpdate() = runBlocking {
        prefs.edit().putLong("checkedAtMillis", 123456L).putString("checkResult", "Сервер обновлений: 503")
            .putBoolean("checkFailed", true).commit()
        val state = UpdateController(context).recover()
        assertEquals(UpdateStatus.FAILED, state.status)
        assertEquals(123456L, state.checkedAtMillis)
        assertEquals("Сервер обновлений: 503", state.checkResult)
        assertFalse(state.canDownload)
        assertNull(state.downloadedBytes)
        assertNull(state.totalBytes)
    }

    @Test fun knownNewerManifestRetainsRetryAfterFailedRecheck() = runBlocking {
        prefs.edit().putString("manifest", manifest.toJson()).putLong("checkedAtMillis", 123456L)
            .putString("checkResult", "Нет сети").putBoolean("checkFailed", true).commit()
        val state = UpdateController(context).recover()
        assertEquals(UpdateStatus.FAILED, state.status)
        assertEquals("Нет сети", state.message)
        assertTrue(state.canDownload)
        assertEquals("следующая", state.version)
    }

    @Test fun oldCurrentManifestDoesNotHideLatestCheckFailure() = runBlocking {
        prefs.edit().putString("manifest", manifest.copy(versionCode = BuildConfig.VERSION_CODE.toLong()).toJson())
            .putLong("checkedAtMillis", 123456L).putString("checkResult", "Нет сети").putBoolean("checkFailed", true).commit()
        val state = UpdateController(context).recover()
        assertEquals(UpdateStatus.FAILED, state.status)
        assertEquals("Нет сети", state.message)
        assertFalse(state.canDownload)
    }

    @Test fun installationIsConfirmedOnlyByInstalledVersion() = runBlocking {
        prefs.edit().putString("manifest", manifest.toJson()).putBoolean("installRequested", true).commit()
        assertEquals(UpdateStatus.AVAILABLE, UpdateController(context).recover().status)
        prefs.edit().putString("manifest", manifest.copy(versionCode = BuildConfig.VERSION_CODE.toLong()).toJson()).commit()
        val state = UpdateController(context).recover()
        assertEquals(UpdateStatus.CURRENT, state.status)
        assertEquals("Обновление установлено", state.message)
        assertFalse(state.canDownload)
    }
}
