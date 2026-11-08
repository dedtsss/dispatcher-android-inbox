package ru.bruce.dispatcherinbox.drive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import ru.bruce.dispatcherinbox.DispatcherApplication
import ru.bruce.dispatcherinbox.sync.SyncScheduler

class DriveConnectActivity : ComponentActivity() {
    private var awaitingResolution = false
    private var authorizationSummary = ""
    private var stage = "authorize"
    private var activityResultCode: Int? = null
    private val container get() = (application as DispatcherApplication).container
    private val auth by lazy { DriveAuthorization(this) }
    private val resolution = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        awaitingResolution = false
        lifecycleScope.launch {
            try {
                stage = "resolution"
                activityResultCode = result.resultCode
                // Google может вернуть ApiException и при RESULT_CANCELED: всегда декодируем Intent.
                connected(auth.client.getAuthorizationResultFromIntent(result.data))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed(e) }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        awaitingResolution = savedInstanceState?.getBoolean("awaiting_resolution") ?: false
        authorizationSummary = savedInstanceState?.getString("authorization_summary").orEmpty()
        stage = savedInstanceState?.getString("authorization_stage") ?: "authorize"
        if (awaitingResolution) return
        lifecycleScope.launch {
            try {
                container.settingsRepository.setDriveStatus("Ожидаем подтверждения Google…")
                val settings = container.settingsRepository.settings.first()
                val result = auth.client.authorize(auth.request(settings.driveAccount)).await()
                authorizationSummary = summary(result)
                record(authorizationSummary)
                if (result.hasResolution()) {
                    awaitingResolution = true
                    resolution.launch(IntentSenderRequest.Builder(checkNotNull(result.pendingIntent)).build())
                } else {
                    connected(result)
                    finish()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed(e); finish() }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("awaiting_resolution", awaitingResolution)
        outState.putString("authorization_summary", authorizationSummary)
        outState.putString("authorization_stage", stage)
        super.onSaveInstanceState(outState)
    }

    private suspend fun connected(result: AuthorizationResult) {
        authorizationSummary = summary(result)
        record(authorizationSummary)
        check(!result.hasResolution()) { "Google повторно запросил подтверждение" }
        check(DriveAuthorization.DRIVE_FILE in result.grantedScopes) { "Google не подтвердил scope drive.file" }
        val token = checkNotNull(result.accessToken) { "Google не выдал access token" }
        stage = "drive_about"
        val account = withContext(Dispatchers.IO) {
            DriveRestClient(token).json("drive/v3/about?fields=user(emailAddress)")
                .getAsJsonObject("user")["emailAddress"].asString
        }
        container.settingsRepository.setDriveAccount(account)
        container.settingsRepository.setDriveStatus("Подключён · Готов к отправке")
        SyncScheduler(this, container.settingsRepository).scheduleManual()
    }

    private suspend fun failed(error: Exception) {
        val api = error as? ApiException
        val http = error as? DriveHttpException
        record("apiStatus=${api?.statusCode}; statusName=${api?.statusCode?.let(com.google.android.gms.common.api.CommonStatusCodes::getStatusCodeString)}; providerMessage=${AuthorizationDiagnostics.safeMessage(api?.status?.statusMessage)}; authorizationResult=$authorizationSummary; httpStatus=${http?.code}; reason=${http?.reason.orEmpty()}; exception=${error.javaClass.simpleName}; detail=${AuthorizationDiagnostics.safeMessage(error.message)}")
        container.settingsRepository.setDriveStatus(AuthorizationDiagnostics.message(api?.statusCode, http?.code, http?.reason.orEmpty()))
    }

    private fun summary(result: AuthorizationResult) = "hasResolution=${result.hasResolution()}; hasAccessToken=${!result.accessToken.isNullOrBlank()}; driveFileGranted=${DriveAuthorization.DRIVE_FILE in result.grantedScopes}"

    private suspend fun record(details: String) {
        val signer = if (android.os.Build.VERSION.SDK_INT >= 28) {
            packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES).signatures?.firstOrNull()?.toByteArray()
        }
        val sha1 = signer?.let { java.security.MessageDigest.getInstance("SHA-1").digest(it).joinToString(":") { b -> "%02X".format(b) } }.orEmpty()
        val gms = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo("com.google.android.gms", 0).versionName
        }.getOrNull() ?: "unavailable"
        val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(this)
        val environment = "Android=${android.os.Build.VERSION.SDK_INT}; gmsVersion=$gms; gmsAvailability=$availability; authSdk=21.4.0; app=${ru.bruce.dispatcherinbox.BuildConfig.VERSION_NAME}/${ru.bruce.dispatcherinbox.BuildConfig.VERSION_CODE}"
        val diagnostic = "stage=$stage; resultCode=$activityResultCode; $details\npackage=$packageName; SHA-1=$sha1; scope=${DriveAuthorization.DRIVE_FILE}\n$environment"
        android.util.Log.i("CatchingOAuth", diagnostic)
        container.settingsRepository.setDriveDiagnostics(diagnostic)
    }
}
