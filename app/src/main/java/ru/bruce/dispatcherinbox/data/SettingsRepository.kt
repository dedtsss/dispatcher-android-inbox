package ru.bruce.dispatcherinbox.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "dispatcher_settings")

data class AppSettings(
    val driveAccount: String = "",
    val driveStatus: String = "Google Drive не подключён",
    val autoSync: Boolean = true,
    val wifiOnly: Boolean = false,
    val defaultOpenMode: String = "text",
    val closeAfterSave: Boolean = false,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val driveAccount = stringPreferencesKey("drive_account")
        val driveStatus = stringPreferencesKey("drive_status")
        val autoSync = booleanPreferencesKey("auto_sync")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val defaultOpenMode = stringPreferencesKey("default_open_mode")
        val closeAfterSave = booleanPreferencesKey("close_after_save")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { values ->
        AppSettings(
            driveAccount = values[Keys.driveAccount].orEmpty(),
            driveStatus = values[Keys.driveStatus] ?: "Google Drive не подключён",
            autoSync = values[Keys.autoSync] ?: true,
            wifiOnly = values[Keys.wifiOnly] ?: false,
            defaultOpenMode = values[Keys.defaultOpenMode] ?: "text",
            closeAfterSave = values[Keys.closeAfterSave] ?: false,
        )
    }

    suspend fun setDriveAccount(value: String) = update(Keys.driveAccount, value)
    suspend fun setDriveStatus(value: String) = update(Keys.driveStatus, value)

    suspend fun setAutoSync(value: Boolean) {
        update(Keys.autoSync, value)
        if (value) ru.bruce.dispatcherinbox.sync.SyncScheduler(context, this).scheduleAutomatic()
    }
    suspend fun setWifiOnly(value: Boolean) = update(Keys.wifiOnly, value)
    suspend fun setDefaultOpenMode(value: String) = update(Keys.defaultOpenMode, value)
    suspend fun setCloseAfterSave(value: Boolean) = update(Keys.closeAfterSave, value)

    private suspend fun <T> update(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        context.settingsDataStore.edit { it[key] = value }
    }
}
