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
    private val container get() = (application as DispatcherApplication).container
    private val auth by lazy { DriveAuthorization(this) }
    private val resolution = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        lifecycleScope.launch {
            try {
                if (result.resultCode != RESULT_OK) {
                    container.settingsRepository.setDriveStatus("Подключение отменено. Оригиналы остаются на телефоне")
                } else {
                    connected(auth.client.getAuthorizationResultFromIntent(result.data))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed(e) }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        lifecycleScope.launch {
            try {
                val settings = container.settingsRepository.settings.first()
                val result = auth.client.authorize(auth.request(settings.driveAccount)).await()
                if (result.hasResolution()) {
                    resolution.launch(IntentSenderRequest.Builder(checkNotNull(result.pendingIntent)).build())
                } else {
                    connected(result)
                    finish()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed(e); finish() }
        }
    }

    private suspend fun connected(result: AuthorizationResult) {
        val token = checkNotNull(result.accessToken)
        val account = withContext(Dispatchers.IO) {
            DriveRestClient(token).json("drive/v3/about?fields=user(emailAddress)")
                .getAsJsonObject("user")["emailAddress"].asString
        }
        container.settingsRepository.setDriveAccount(account)
        container.settingsRepository.setDriveStatus("Подключён · Catching/Inbox")
        SyncScheduler(this, container.settingsRepository).scheduleManual()
    }

    private suspend fun failed(error: Exception) {
        val code = (error as? ApiException)?.statusCode
        container.settingsRepository.setDriveStatus(
            if (code == 10) "Google OAuth не настроен для этого APK (код 10). Нужна регистрация package + SHA-1 сертификата"
            else "Не удалось подключить Google Drive${code?.let { " (код $it)" }.orEmpty()}. Проверьте сеть и повторите",
        )
    }
}
