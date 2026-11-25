package ru.bruce.dispatcherinbox.ui.settings

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
        SyncNotificationPermission()
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        val context = LocalContext.current
        var driveExpanded by rememberSaveable { mutableStateOf(false) }
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button,
                    onClickLabel = if (driveExpanded) "Свернуть Google Drive" else "Подробности Google Drive") { driveExpanded = !driveExpanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Google Drive", style = MaterialTheme.typography.titleMedium)
                        Text(if (settings.driveAccount.isBlank()) "Не подключён" else settings.driveStatus.substringBefore('·').trim().ifBlank { "Аккаунт выбран" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(if (driveExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(24.dp))
                }
                if (driveExpanded) Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(settings.driveAccount.ifBlank { "Аккаунт не выбран" }, style = MaterialTheme.typography.bodyMedium)
                    Text(settings.driveStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text("Оригиналы → Мой диск / Catching / Inbox. Без сети всё сохраняется на телефоне.", style = MaterialTheme.typography.bodySmall)
                    var diagnosticsExpanded by rememberSaveable { mutableStateOf(false) }
                    TextButton(onClick = { diagnosticsExpanded = !diagnosticsExpanded }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (diagnosticsExpanded) "Скрыть диагностику подключения" else "Диагностика подключения")
                    }
                    if (diagnosticsExpanded) androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(settings.driveDiagnostics.ifBlank { "Диагностика появится после попытки подключения" }, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (driveExpanded || settings.driveAccount.isBlank()) TextButton(onClick = {
                    context.startActivity(android.content.Intent(context, ru.bruce.dispatcherinbox.drive.DriveConnectActivity::class.java))
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (settings.driveAccount.isBlank()) "Подключить Google Drive" else "Подключить повторно")
                }
            }
        }
        SettingSwitch("Автосинхронизация", settings.autoSync, viewModel::setAutoSync)
        SettingSwitch("Только по Wi-Fi", settings.wifiOnly, viewModel::setWifiOnly)
        if (settings.syncLastAttemptAt > 0L) {
            Text("Последняя попытка отправки: ${formatSettingsTime(settings.syncLastAttemptAt)}", style = MaterialTheme.typography.bodySmall)
            Text(settings.syncLastResult.ifBlank { "Попытка началась; подтверждения результата пока нет" },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        var cleanupExpanded by rememberSaveable { mutableStateOf(false) }
        val policies = listOf("keep" to "Не удалять", "after_upload" to "После подтверждённой загрузки", "after_7_days" to "Через 7 дней после загрузки")
        TextButton(onClick = { cleanupExpanded = !cleanupExpanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Локальные копии: ${policies.firstOrNull { it.first == settings.localCopyPolicy }?.second ?: "Не удалять"}")
        }
        if (cleanupExpanded) {
            Text("Очистка только файлов после подтверждённой загрузки полного состава записи в Drive. Метаданные остаются; неотправленные и ошибочные оригиналы сохраняются. Политика применяется только к новым записям, созданным после её включения; старые записи автоматически не очищаются.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            policies.forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = settings.localCopyPolicy == value,
                    role = Role.RadioButton, onClick = { viewModel.setLocalCopyPolicy(value) }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = settings.localCopyPolicy == value, onClick = null)
                    Text(label, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text("После локального сохранения панель показывает подтверждение и закрывается. Отправка в Drive выполняется отдельно.", style = MaterialTheme.typography.bodySmall)
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

private fun formatSettingsTime(timestamp: Long): String = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp))

@androidx.compose.runtime.Composable
private fun SyncNotificationPermission() {
    if (android.os.Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    var allowed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    ) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { allowed = it }
    if (!allowed) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Уведомления о задержке отправки отключены в Android", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Разрешить уведомления")
        }
    }
}
