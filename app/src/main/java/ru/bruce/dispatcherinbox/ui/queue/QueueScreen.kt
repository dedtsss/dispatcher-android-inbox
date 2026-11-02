package ru.bruce.dispatcherinbox.ui.queue

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
import androidx.compose.material3.Surface
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
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Очередь", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Последние локальные записи",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        if (entries.isEmpty()) {
            Text(
                "Здесь пока пусто",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 40.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(entries, key = { it.item.id }) { entry ->
                    QueueCard(entry, settings.driveAccount.isNotBlank(), connect, viewModel::retry, viewModel::delete)
                }
            }
        }
    }
}

@Composable
private fun QueueCard(
    entry: InboxItemWithAttachments,
    driveConnected: Boolean,
    onConnect: () -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val item = entry.item
    val delivery = ru.bruce.dispatcherinbox.ui.deliveryStatus(item.status, driveConnected)
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(typeLabel(item.type), style = MaterialTheme.typography.labelLarge)
                StatusBadge(delivery)
            }
            val summary = item.text ?: entry.attachments.joinToString { it.originalName ?: "Вложение" }
            Text(summary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                formatTime(item.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.lastError?.takeIf { delivery.isError }?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.status == InboxStatus.ERROR || item.status == InboxStatus.QUEUED) {
                    OutlinedButton(onClick = { if (delivery.waitingForDrive) onConnect() else onRetry(item.id) }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text(if (delivery.waitingForDrive) "Подключить Drive" else if (item.status == InboxStatus.ERROR) "Повторить" else "Отправить")
                    }
                }
                if (item.status != InboxStatus.SYNCING) {
                    TextButton(onClick = { onDelete(item.id) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null)
                        Text("Удалить локально")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(delivery: ru.bruce.dispatcherinbox.ui.DeliveryStatus) {
    val isError = delivery.isError
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            delivery.label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
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

private fun formatTime(timestamp: Long): String = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(timestamp))
