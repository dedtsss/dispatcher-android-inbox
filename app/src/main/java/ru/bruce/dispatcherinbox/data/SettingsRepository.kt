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
    val apiBaseUrl: String = "",
    val bearerToken: String = "",
    val autoSync: Boolean = true,
    val wifiOnly: Boolean = false,
    val deleteAfterSync: Boolean = false,
    val defaultOpenMode: String = "text",
    val autoStartAssistantRecording: Boolean = false,
    val closeAfterSave: Boolean = false,
)

object DefaultOpenMode {
    const val TEXT = "text"
    const val VOICE = "voice"

    fun normalize(value: String?): String = if (value == VOICE) VOICE else TEXT
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val apiBaseUrl = stringPreferencesKey("api_base_url")
        val bearerToken = stringPreferencesKey("bearer_token")
        val autoSync = booleanPreferencesKey("auto_sync")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val deleteAfterSync = booleanPreferencesKey("delete_after_sync")
        val defaultOpenMode = stringPreferencesKey("default_open_mode")
        val autoStartAssistantRecording = booleanPreferencesKey("auto_start_assistant_recording")
        val closeAfterSave = booleanPreferencesKey("close_after_save")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { values ->
        AppSettings(
            apiBaseUrl = values[Keys.apiBaseUrl].orEmpty(),
            bearerToken = values[Keys.bearerToken].orEmpty(),
            autoSync = values[Keys.autoSync] ?: true,
            wifiOnly = values[Keys.wifiOnly] ?: false,
            deleteAfterSync = values[Keys.deleteAfterSync] ?: false,
            defaultOpenMode = DefaultOpenMode.normalize(values[Keys.defaultOpenMode]),
            autoStartAssistantRecording = values[Keys.autoStartAssistantRecording] ?: false,
            closeAfterSave = values[Keys.closeAfterSave] ?: false,
        )
    }

    suspend fun setApiBaseUrl(value: String) = update(Keys.apiBaseUrl, value.trim())
    suspend fun setBearerToken(value: String) = update(Keys.bearerToken, value.trim())
    suspend fun setAutoSync(value: Boolean) = update(Keys.autoSync, value)
    suspend fun setWifiOnly(value: Boolean) = update(Keys.wifiOnly, value)
    suspend fun setDeleteAfterSync(value: Boolean) = update(Keys.deleteAfterSync, value)
    suspend fun setDefaultOpenMode(value: String) = update(
        Keys.defaultOpenMode,
        DefaultOpenMode.normalize(value),
    )
    suspend fun setAutoStartAssistantRecording(value: Boolean) =
        update(Keys.autoStartAssistantRecording, value)
    suspend fun setCloseAfterSave(value: Boolean) = update(Keys.closeAfterSave, value)

    private suspend fun <T> update(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        context.settingsDataStore.edit { it[key] = value }
    }
}
