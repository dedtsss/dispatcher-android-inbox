package ru.bruce.dispatcherinbox.ui.settings

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ru.bruce.dispatcherinbox.data.DefaultOpenMode

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onRequestAssistantRole: () -> Unit,
    onOpenAssistantSettings: () -> Unit,
    onOpenDefaultAppsSettings: () -> Unit,
    onRequestQuickSettingsTile: () -> Unit,
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
            label = "Удалять внутренние копии после отправки",
            checked = settings.deleteAfterSync,
            onCheckedChange = viewModel::setDeleteAfterSync,
        )
        SettingSwitch("Закрывать после сохранения", settings.closeAfterSave, viewModel::setCloseAfterSave)
        HorizontalDivider()
        Text("Открывать по умолчанию", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.defaultOpenMode == DefaultOpenMode.TEXT,
                onClick = { viewModel.setDefaultOpenMode(DefaultOpenMode.TEXT) },
                label = { Text("Текст") },
            )
            FilterChip(
                selected = settings.defaultOpenMode == DefaultOpenMode.VOICE,
                onClick = { viewModel.setDefaultOpenMode(DefaultOpenMode.VOICE) },
                label = { Text("Голос") },
            )
        }
        SettingSwitch(
            label = "Автозапись при вызове ассистента",
            checked = settings.autoStartAssistantRecording,
            onCheckedChange = viewModel::setAutoStartAssistantRecording,
        )
        Text(
            "Микрофон запускается автоматически только в assistant overlay и только для режима «Голос».",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider()
        Text("Системный ассистент", style = MaterialTheme.typography.titleMedium)
        AssistantStatus()
        Text(
            "На HyperOS экран обычно называется «Помощник и голосовой ввод». Выберите «CatChing» как цифрового помощника. Голосовой ввод менять не нужно: приложение не распознает речь.",
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
        HorizontalDivider()
        Text("Быстрый доступ", style = MaterialTheme.typography.titleMedium)
        Text(
            "Добавьте плитку «CatChing» в шторку. На защищенном экране телефон сначала попросит разблокировку, затем сразу откроет запись.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = onRequestQuickSettingsTile, modifier = Modifier.fillMaxWidth()) {
            Text("Добавить в шторку")
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AssistantStatus() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    fun isActive(): Boolean = isAssistantRoleHeld(context)
    var active by remember { mutableStateOf(isActive()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) active = isActive()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        color = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (active) "Системный статус: CatChing активен"
            else "Системный статус: CatChing пока не активен",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun isAssistantRoleHeld(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return context.getSystemService(RoleManager::class.java)
            ?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
    }
    val selectedComponent = Settings.Secure.getString(context.contentResolver, "assistant")
        ?.let(ComponentName::unflattenFromString)
    return selectedComponent?.packageName == context.packageName
}
