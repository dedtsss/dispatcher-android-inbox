package ru.bruce.dispatcherinbox.drive

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await
import ru.bruce.dispatcherinbox.data.AppSettings

class DriveAuthorization(context: Context) {
    val client = Identity.getAuthorizationClient(context)

    fun request(account: String = ""): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_FILE)))
        .apply { if (account.isNotBlank()) setAccount(Account(account, "com.google")) }
        .build()

    suspend fun token(settings: AppSettings): String {
        check(settings.driveAccount.isNotBlank()) { "Подключите Google Drive в настройках. Оригиналы сохранены на телефоне" }
        val result = client.authorize(request(settings.driveAccount)).await()
        check(!result.hasResolution()) { "Google Drive требует подтверждения. Подключите его повторно в настройках" }
        return checkNotNull(result.accessToken) { "Google не выдал доступ к Drive. Подключите повторно" }
    }

    suspend fun clear(token: String) {
        client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
    }

    companion object { const val DRIVE_FILE = "https://www.googleapis.com/auth/drive.file" }
}
