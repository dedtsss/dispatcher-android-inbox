package ru.bruce.dispatcherinbox.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onRequestAssistantRole: () -> Unit,
    onOpenAssistantSettings: () -> Unit,
    onOpenDefaultAppsSettings: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        Text(
            "В debug-сборке пустой URL включает локальную заглушку. Для реальной отправки нужны URL и token.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = settings.apiBaseUrl,
            onValueChange = viewModel::setApiBaseUrl,
            label = { Text("Dispatcher API base URL") },
            placeholder = { Text("https://dispatcher.example/") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.bearerToken,
            onValueChange = viewModel::setBearerToken,
            label = { Text("Device / Bearer token") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SettingSwitch("Автосинхронизация", settings.autoSync, viewModel::setAutoSync)
        SettingSwitch("Только по Wi-Fi", settings.wifiOnly, viewModel::setWifiOnly)
        SettingSwitch(
            "Удалять файлы после успешной отправки",
            settings.deleteAfterSync,
            viewModel::setDeleteAfterSync,
        )
        SettingSwitch("Закрывать после сохранения", settings.closeAfterSave, viewModel::setCloseAfterSave)
        HorizontalDivider()
        Text("Открывать по умолчанию", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.defaultOpenMode == "text",
                onClick = { viewModel.setDefaultOpenMode("text") },
                label = { Text("Текст") },
            )
            FilterChip(
                selected = settings.defaultOpenMode == "voice",
                onClick = { viewModel.setDefaultOpenMode("voice") },
                label = { Text("Голос") },
            )
        }
        HorizontalDivider()
        Text("Системный ассистент", style = MaterialTheme.typography.titleMedium)
        Text(
            "На HyperOS экран обычно называется «Помощник и голосовой ввод». Если системный запрос роли не сработал, откройте выбор ниже и укажите «Приёмник» вместо текущего ассистента.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = onRequestAssistantRole, modifier = Modifier.fillMaxWidth()) {
            Text("Запросить роль ассистента")
        }
        OutlinedButton(onClick = onOpenAssistantSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Открыть выбор ассистента")
        }
        OutlinedButton(onClick = onOpenDefaultAppsSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Открыть приложения по умолчанию")
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
