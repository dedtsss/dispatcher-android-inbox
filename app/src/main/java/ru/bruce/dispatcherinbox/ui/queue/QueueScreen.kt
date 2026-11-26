package ru.bruce.dispatcherinbox.ui.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.bruce.dispatcherinbox.data.InboxItemWithAttachments
import ru.bruce.dispatcherinbox.data.InboxStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun QueueScreen(viewModel: QueueViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val connect = { context.startActivity(android.content.Intent(context, ru.bruce.dispatcherinbox.drive.DriveConnectActivity::class.java)) }
    val entries by viewModel.items.collectAsStateWithLifecycle()
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var confirmArchive by rememberSaveable { mutableStateOf(false) }
    val visible = entries.filter { (it.item.archivedAt != null) == showArchived }
    val sentCount = entries.count { it.item.archivedAt == null && it.item.status == InboxStatus.SYNCED && it.item.syncedAt != null }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Очередь", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Последние локальные записи",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        OutlinedButton(onClick = { showArchived = !showArchived }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(if (showArchived) Icons.Default.Undo else Icons.Default.Archive, contentDescription = null)
                Text(if (showArchived) "К очереди" else "Архив (${entries.count { it.item.archivedAt != null }})")
        }
        if (!showArchived && sentCount > 0) TextButton(onClick = { confirmArchive = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Очистить отправленные ($sentCount)")
        }
        if (visible.isEmpty()) {
            Text(
                if (showArchived) "Архив пуст" else "Здесь пока пусто",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 40.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(visible, key = { it.item.id }) { entry ->
                    QueueCard(entry, settings.driveAccount.isNotBlank(), connect, viewModel::retry, viewModel::delete,
                        showArchived, viewModel::restoreArchived)
                }
            }
        }
    }
    if (confirmArchive) AlertDialog(
        onDismissRequest = { confirmArchive = false },
        title = { Text("Скрыть отправленные из очереди?") },
        text = { Text("Будут перемещены в архив только $sentCount записей, у которых Drive подтвердил отправку. История и локальные оригиналы сохранятся; незавершённые и ошибочные записи не изменятся.") },
        confirmButton = { TextButton(onClick = { confirmArchive = false; viewModel.archiveConfirmed() }) { Text("В архив") } },
        dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Отмена") } },
    )
}

@Composable
private fun QueueCard(
    entry: InboxItemWithAttachments,
    driveConnected: Boolean,
    onConnect: () -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    archived: Boolean,
    onRestore: (String) -> Unit,
) {
    val item = entry.item
    val delivery = ru.bruce.dispatcherinbox.ui.deliveryStatus(item.status, driveConnected)
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(item.id) { mutableStateOf(false) }
    val summary = item.text?.takeIf { it.isNotBlank() }
        ?: entry.attachments.firstOrNull()?.originalName?.takeIf { it.isNotBlank() }
        ?: typeLabel(entry)
    val typeDescriptions = queueTypeCodes(entry).take(3).map { type ->
        when (type) {
            "voice" -> "Тип: голосовая запись"
            "text" -> "Тип: текстовая запись"
            "image" -> "Тип: фотография"
            "video" -> "Тип: видео"
            "document" -> "Тип: документ"
            else -> "Тип: файл"
        }
    }
    val totalBytes = entry.attachments.sumOf { it.sizeBytes.coerceAtLeast(0) } + (item.text?.toByteArray(Charsets.UTF_8)?.size ?: 0)
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            Column(
                Modifier.fillMaxWidth().heightIn(min = 80.dp)
                    .semantics {
                        contentDescription = "${typeDescriptions.joinToString()}. $summary. " +
                            (if (archived) "В архиве · " else "") + delivery.label
                    }
                    .clickable(role = Role.Button, onClickLabel = if (expanded) "Свернуть запись" else "Подробности записи") { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QueueTypeIcons(entry, Modifier.padding(end = 7.dp))
                    Text("${typeLabel(entry)} · $summary", maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null,
                        modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${formatTime(item.createdAt)} · ${formatSize(totalBytes)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    val statusIcon = when (item.status) {
                        InboxStatus.SYNCED -> Icons.Default.Done
                        InboxStatus.SYNCING -> Icons.Default.Sync
                        InboxStatus.ERROR -> Icons.Default.Warning
                        else -> Icons.Default.Schedule
                    }
                    val statusColor = when (item.status) {
                        InboxStatus.SYNCED -> androidx.compose.ui.graphics.Color(0xFF82D8A2)
                        InboxStatus.ERROR -> MaterialTheme.colorScheme.error
                        InboxStatus.SYNCING -> androidx.compose.ui.graphics.Color(0xFFC2A9F6)
                        else -> androidx.compose.ui.graphics.Color(0xFFF2C56D)
                    }
                    Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
                    Text(if (archived) "В архиве · ${delivery.label}" else delivery.label, style = MaterialTheme.typography.labelMedium, color = statusColor)
                }
                item.lastError?.takeIf { item.status == InboxStatus.ERROR }?.let {
                    Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (expanded) Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item.text?.takeIf { it.isNotBlank() }?.let { SelectionContainer { Text(it, style = MaterialTheme.typography.bodyMedium) } }
                Text("Размер оригиналов: ${formatSize(totalBytes)}", style = MaterialTheme.typography.bodySmall)
                entry.attachments.forEach { attachment ->
                    Text("${attachment.originalName ?: "Вложение"} · ${formatSize(attachment.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall)
                }
                item.durationMillis?.let { duration ->
                    Text("Длительность: %d:%02d".format(duration / 60000, duration / 1000 % 60), style = MaterialTheme.typography.bodySmall)
                }
                item.lastAttemptAt?.let { Text("Последняя попытка: ${formatTime(it)}", style = MaterialTheme.typography.bodySmall) }
                if (item.retryCount > 0) Text("Неудачных попыток: ${item.retryCount}", style = MaterialTheme.typography.bodySmall)
                item.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                item.syncedAt?.let { Text("Загрузка подтверждена: ${formatTime(it)}", style = MaterialTheme.typography.bodySmall) }
                item.originalsDeletedAt?.let { Text("Локальные копии файлов очищены: ${formatTime(it)}. Метаданные сохранены.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (archived) {
                    TextButton(onClick = { onRestore(item.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Default.Undo, contentDescription = null); Text("Вернуть в очередь")
                    }
                } else if (item.status == InboxStatus.ERROR || item.status == InboxStatus.QUEUED) {
                    OutlinedButton(colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        onClick = { if (delivery.waitingForDrive) onConnect() else onRetry(item.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text(if (delivery.waitingForDrive) "Подключить Drive" else if (item.status == InboxStatus.ERROR) "Повторить" else "Отправить")
                    }
                }
                if (item.status != InboxStatus.SYNCING) {
                    TextButton(colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null)
                        Text("Удалить запись с телефона")
                    }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Удалить запись с телефона?") },
        text = { Text(if (item.status == InboxStatus.SYNCED)
            "Запись и оставшиеся локальные оригиналы будут удалены с телефона. Файлы в Google Drive не удаляются."
            else "Отправка полного состава записи в Drive не подтверждена. Запись и её локальные оригиналы будут удалены без возможности восстановления из Catching.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; if (item.status != InboxStatus.SYNCING) onDelete(item.id) },
            enabled = item.status != InboxStatus.SYNCING) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
    )
}

private fun typeLabel(type: String) = when (type) {
    "text" -> "Текст"
    "voice" -> "Голос"
    "image" -> "Изображение"
    "video" -> "Видео"
    "document" -> "Документ"
    "mixed" -> "Текст и файлы"
    else -> "Файл"
}

private fun typeLabel(entry: InboxItemWithAttachments): String =
    if (entry.item.type == "mixed") "Смешанная запись" else typeLabel(entry.item.type)

@Composable
private fun QueueTypeIcons(entry: InboxItemWithAttachments, modifier: Modifier = Modifier) {
    val types = queueTypeCodes(entry).take(3)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        types.forEach { type ->
            val icon = when (type) {
                "voice" -> Icons.Default.Mic
                "text" -> Icons.Default.TextFields
                "image" -> Icons.Default.Image
                "video" -> Icons.Default.Movie
                "document" -> Icons.Default.Description
                else -> Icons.Default.AttachFile
            }
            val description = when (type) {
                "voice" -> "Тип: голосовая запись"
                "text" -> "Тип: текстовая запись"
                "image" -> "Тип: фотография"
                "video" -> "Тип: видео"
                "document" -> "Тип: документ"
                else -> "Тип: файл"
            }
            Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun queueTypeCodes(entry: InboxItemWithAttachments): Set<String> = buildSet {
    if (!entry.item.text.isNullOrBlank()) add("text")
    entry.attachments.forEach { attachment ->
        val mime = attachment.mimeType.orEmpty().lowercase()
        add(when {
            mime.startsWith("audio/") -> "voice"
            mime.startsWith("image/") -> "image"
            mime.startsWith("video/") -> "video"
            mime.isNotBlank() -> "document"
            else -> "file"
        })
    }
    if (isEmpty()) add(entry.item.type)
}

private fun formatTime(timestamp: Long): String = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(timestamp))

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes.coerceAtLeast(0)} Б"
    bytes < 1024 * 1024 -> String.format(Locale.forLanguageTag("ru"), "%.1f КиБ", bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> String.format(Locale.forLanguageTag("ru"), "%.1f МиБ", bytes / (1024.0 * 1024))
    else -> String.format(Locale.forLanguageTag("ru"), "%.1f ГиБ", bytes / (1024.0 * 1024 * 1024))
}
