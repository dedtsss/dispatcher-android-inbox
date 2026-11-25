package ru.bruce.dispatcherinbox.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "dispatcher_settings")

data class AppSettings(
    val driveAccount: String = "",
    val driveDiagnostics: String = "",
    val assistantOpenMode: String = "text",
    val autoStartAssistantRecording: Boolean = false,
    val driveStatus: String = "Google Drive не подключён",
    val autoSync: Boolean = true,
    val wifiOnly: Boolean = false,
    val defaultOpenMode: String = "text",
    val closeAfterSave: Boolean = false,
    val localCopyPolicy: String = "keep",
    val cleanupEnabledAt: Long = 0,
    val syncLastAttemptAt: Long = 0,
    val syncLastResult: String = "Попыток отправки ещё не было",

)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val localCopyPolicy = stringPreferencesKey("local_copy_policy")
        val cleanupEnabledAt = longPreferencesKey("cleanup_enabled_at")
        val syncLastAttemptAt = longPreferencesKey("sync_last_attempt_at")
        val syncLastResult = stringPreferencesKey("sync_last_result")
        val driveAccount = stringPreferencesKey("drive_account")
        val driveDiagnostics = stringPreferencesKey("drive_diagnostics")
        val assistantOpenMode = stringPreferencesKey("assistant_open_mode")
        val autoStartAssistantRecording = booleanPreferencesKey("auto_start_assistant_recording")
        val driveStatus = stringPreferencesKey("drive_status")
        val autoSync = booleanPreferencesKey("auto_sync")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val defaultOpenMode = stringPreferencesKey("default_open_mode")
        val closeAfterSave = booleanPreferencesKey("close_after_save")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { values ->
        AppSettings(
            localCopyPolicy = values[Keys.localCopyPolicy] ?: "keep",
            cleanupEnabledAt = values[Keys.cleanupEnabledAt] ?: 0,
            syncLastAttemptAt = values[Keys.syncLastAttemptAt] ?: 0,
            syncLastResult = values[Keys.syncLastResult] ?: "Попыток отправки ещё не было",
            driveDiagnostics = values[Keys.driveDiagnostics].orEmpty(),
            assistantOpenMode = values[Keys.assistantOpenMode] ?: "text",
            autoStartAssistantRecording = values[Keys.autoStartAssistantRecording] ?: false,
            driveAccount = values[Keys.driveAccount].orEmpty(),
            driveStatus = values[Keys.driveStatus] ?: "Google Drive не подключён",
            autoSync = values[Keys.autoSync] ?: true,
            wifiOnly = values[Keys.wifiOnly] ?: false,
            defaultOpenMode = values[Keys.defaultOpenMode] ?: "text",
            closeAfterSave = values[Keys.closeAfterSave] ?: false,
        )
    }

    suspend fun setLocalCopyPolicy(value: String) {
        require(value in setOf("keep", "after_upload", "after_7_days"))
        context.settingsDataStore.edit {
            if (it[Keys.localCopyPolicy] != value) it[Keys.cleanupEnabledAt] = System.currentTimeMillis()
            it[Keys.localCopyPolicy] = value
        }
        ru.bruce.dispatcherinbox.sync.SyncScheduler(context, this).ensureRepairScheduled()
    }

    suspend fun setSyncResult(value: String) = update(Keys.syncLastResult, value)

    suspend fun setSyncDiagnostics(at: Long, result: String) {
        context.settingsDataStore.edit {
            it[Keys.syncLastAttemptAt] = at
            it[Keys.syncLastResult] = result
        }
    }

    suspend fun setDriveDiagnostics(value: String) = update(Keys.driveDiagnostics, value)
    suspend fun setAssistantOpenMode(value: String) = update(Keys.assistantOpenMode, if (value == "voice") "voice" else "text")
    suspend fun setAutoStartAssistantRecording(value: Boolean) = update(Keys.autoStartAssistantRecording, value)

    suspend fun setDriveAccount(value: String) = update(Keys.driveAccount, value)
    suspend fun setDriveStatus(value: String) = update(Keys.driveStatus, value)

    suspend fun setAutoSync(value: Boolean) {
        update(Keys.autoSync, value)
        if (value) ru.bruce.dispatcherinbox.sync.SyncScheduler(context, this).scheduleAutomatic(replace = true)
    }
    suspend fun setWifiOnly(value: Boolean) {
        update(Keys.wifiOnly, value)
        ru.bruce.dispatcherinbox.sync.SyncScheduler(context, this).scheduleAutomatic(replace = true)
    }
    suspend fun setDefaultOpenMode(value: String) = update(Keys.defaultOpenMode, value)
    suspend fun setCloseAfterSave(value: Boolean) = update(Keys.closeAfterSave, value)

    private suspend fun <T> update(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        context.settingsDataStore.edit { it[key] = value }
    }
}
