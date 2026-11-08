package ru.bruce.dispatcherinbox.drive

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import ru.bruce.dispatcherinbox.DispatcherApplication
import ru.bruce.dispatcherinbox.sync.SyncScheduler

internal class DriveConnectionViewModel(saved: SavedStateHandle, context: Context) : ViewModel() {
    val flow = DriveConnectionFlow(saved, viewModelScope, GoogleDriveConnectionBackend(context.applicationContext))
}

internal class GoogleDriveConnectionBackend(private val context: Context) : DriveConnectionBackend {
    private val container get() = (context.applicationContext as DispatcherApplication).container
    private val auth = DriveAuthorization(context)
    private val history = mutableListOf<String>()
    private fun grant(result: AuthorizationResult) = DriveGrant(result.grantedScopes, result.accessToken, result.pendingIntent)
    override suspend fun authorize(account: String) = grant(auth.client.authorize(auth.request(account)).await())
    override fun parse(data: Intent?) = grant(auth.client.getAuthorizationResultFromIntent(data))
    override suspend fun verifyAndConnect(account: String, token: String) {
        record("drive_verify", "accessGranted=true")
        withContext(Dispatchers.IO) {
            val drive = DriveRestClient(token)
            val actual = drive.json("drive/v3/about?fields=user(emailAddress)")["user"].asJsonObject["emailAddress"].asString
            check(actual.equals(account, ignoreCase = true)) { "account_mismatch" }
            DriveFolders(actual, drive, object : DriveIdStore {
                override suspend fun get(key: String) = container.dao.driveId(key)
                override suspend fun put(key: String, id: String) = container.dao.putDriveId(DriveCheckpoint(key, id))
            }).inbox()
            // Только подтверждённый аккаунт становится destination; старые записи не меняются.
            container.settingsRepository.setDriveAccount(actual)
        }
        container.settingsRepository.setDriveStatus("Подключён · Catching/Inbox")
        SyncScheduler(context, container.settingsRepository).scheduleManual()
        record("connected", "driveFileGranted=true; inboxVerified=true")
    }
    override suspend fun status(message: String) { container.settingsRepository.setDriveStatus(message) }
    override fun errorMessage(error: Exception): String = AuthorizationDiagnostics.message((error as? ApiException)?.statusCode, (error as? DriveHttpException)?.code)
    override suspend fun record(stage: String, details: String) {
        history += "stage=$stage; $details"
        val manager = context.packageManager
        @Suppress("DEPRECATION")
        val signer = if (android.os.Build.VERSION.SDK_INT >= 28) manager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()
            else manager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        val sha1 = signer?.toByteArray()?.let { java.security.MessageDigest.getInstance("SHA-1").digest(it).joinToString(":") { b -> "%02X".format(b) } }.orEmpty()
        @Suppress("DEPRECATION")
        val gms = runCatching { manager.getPackageInfo("com.google.android.gms", 0).versionName }.getOrNull() ?: "unavailable"
        val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        val diagnostics = history.takeLast(8).joinToString("\n") + "\npackage=${context.packageName}; SHA-1=$sha1; scope=${DriveAuthorization.DRIVE_FILE}\nAndroid=${android.os.Build.VERSION.SDK_INT}; gmsVersion=$gms; gmsAvailability=$availability; authSdk=21.4.0; app=${ru.bruce.dispatcherinbox.BuildConfig.VERSION_NAME}/${ru.bruce.dispatcherinbox.BuildConfig.VERSION_CODE}"
        container.settingsRepository.setDriveDiagnostics(diagnostics)
    }
}
