package ru.bruce.dispatcherinbox

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.bruce.dispatcherinbox.update.UpdateController
import ru.bruce.dispatcherinbox.update.UpdateManifest
import ru.bruce.dispatcherinbox.update.UpdateStatus

@RunWith(AndroidJUnit4::class)
class UpdateRecoveryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val manifest = UpdateManifest(BuildConfig.VERSION_CODE + 1L, "следующая", "https://updates.example/catching.apk", "ab".repeat(32), context.packageName, "cd".repeat(32))

    @Before fun prepare() { prefs.edit().clear().commit() }
    @After fun clear() { prefs.edit().clear().commit() }

    @Test fun restoresAvailableManifestInNewController() = runBlocking {
        prefs.edit().putString("manifest", manifest.toJson()).commit()
        assertEquals(UpdateStatus.AVAILABLE, UpdateController(context).recover().status)
        assertEquals("следующая", UpdateController(context).recover().version)
    }

    @Test fun missingDownloadAfterRestartFailsClosed() = runBlocking {
        prefs.edit().putString("manifest", manifest.toJson()).putLong("downloadId", Long.MAX_VALUE).commit()
        assertEquals(UpdateStatus.FAILED, UpdateController(context).recover().status)
    }

    @Test fun installedVersionCannotBecomeAvailableOnRecovery() = runBlocking {
        prefs.edit().putString("manifest", manifest.copy(versionCode = BuildConfig.VERSION_CODE.toLong()).toJson()).commit()
        assertEquals(UpdateStatus.CURRENT, UpdateController(context).recover().status)
    }

    @Test fun damagedPersistentMetadataFailsClosed() = runBlocking {
        prefs.edit().putString("manifest", "{}").commit()
        assertEquals(UpdateStatus.FAILED, UpdateController(context).recover().status)
    }
}
