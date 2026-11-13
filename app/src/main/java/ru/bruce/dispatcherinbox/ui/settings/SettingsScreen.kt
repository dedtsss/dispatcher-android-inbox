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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

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
        ru.bruce.dispatcherinbox.update.UpdateSection()
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        val context = LocalContext.current
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Google Drive", style = MaterialTheme.typography.titleLarge)
                Text(settings.driveAccount.ifBlank { "Аккаунт не выбран" })
                Text(settings.driveStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
                var diagnosticsExpanded by remember { mutableStateOf(false) }
                androidx.compose.material3.TextButton(colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant), onClick = { diagnosticsExpanded = !diagnosticsExpanded }) { Text("Диагностика подключения") }
                if (diagnosticsExpanded) androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(settings.driveDiagnostics.ifBlank { "Диагностика появится после попытки подключения" }, style = MaterialTheme.typography.bodySmall)
                }
                Text("Оригиналы → Мой диск / Catching / Inbox. Без сети всё сохраняется на телефоне.")
                Button(onClick = {
                    context.startActivity(android.content.Intent(context, ru.bruce.dispatcherinbox.drive.DriveConnectActivity::class.java))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (settings.driveAccount.isBlank()) "Подключить Google Drive" else "Подключить повторно")
                }
            }
        }
        SettingSwitch("Автосинхронизация", settings.autoSync, viewModel::setAutoSync)
        SettingSwitch("Только по Wi-Fi", settings.wifiOnly, viewModel::setWifiOnly)
        Text("Панель ассистента закрывается сразу после сохранения на телефоне", style = MaterialTheme.typography.bodySmall)
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
        Text("При вызове ассистента", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((mode, label) in listOf("text" to "Текст", "voice" to "Голос")) {
                FilterChip(selected = settings.assistantOpenMode == mode, onClick = { viewModel.setAssistantOpenMode(mode) }, label = { Text(label) })
            }
        }
        SettingSwitch("Сразу начинать запись", settings.autoStartAssistantRecording, viewModel::setAutoStartAssistantRecording,
            enabled = settings.assistantOpenMode == "voice")
        AssistantStatus()
        Text(
            "На HyperOS экран обычно называется «Помощник и голосовой ввод». Выберите «Catching» как цифрового помощника. Голосовой ввод менять не нужно: приложение не распознает речь.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = onRequestAssistantRole, modifier = Modifier.fillMaxWidth()) {
            Text("Запросить роль ассистента")
        }
        OutlinedButton(colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = onOpenAssistantSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Открыть выбор ассистента")
        }
        OutlinedButton(colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = onOpenDefaultAppsSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Открыть приложения по умолчанию")
        }
        HorizontalDivider()
        Text("Быстрый доступ", style = MaterialTheme.typography.titleMedium)
        Text(
            "Добавьте плитку «Catching» в шторку. На защищенном экране телефон сначала попросит разблокировку, затем откроет Catching.",
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
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
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
            if (active) "Системный статус: Catching активен"
            else "Системный статус: Catching пока не активен",
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
