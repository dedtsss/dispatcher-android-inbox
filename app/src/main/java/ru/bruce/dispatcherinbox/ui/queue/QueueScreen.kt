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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.bruce.dispatcherinbox.data.InboxItemWithAttachments
import ru.bruce.dispatcherinbox.data.InboxStatus
import ru.bruce.dispatcherinbox.data.JournalKind
import ru.bruce.dispatcherinbox.data.toJournalPresentation
import ru.bruce.dispatcherinbox.ui.theme.DispatcherError
import ru.bruce.dispatcherinbox.ui.theme.DispatcherSuccess
import ru.bruce.dispatcherinbox.ui.theme.DispatcherWarning
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun QueueScreen(viewModel: QueueViewModel) {
    val entries by viewModel.items.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Очередь", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { confirmClear = true }, enabled = entries.any { it.item.status != InboxStatus.SYNCING }) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null)
                Text("Очистить очередь")
            }
        }
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
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.item.id }) { entry ->
                    QueueCard(entry, viewModel::retry, viewModel::delete)
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Удалить все записи из очереди?") },
            text = { Text("Активно отправляющиеся записи будут сохранены.") },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Отмена") } },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearQueue()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

@Composable
private fun QueueCard(
    entry: InboxItemWithAttachments,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val item = entry.item
    val presentation = entry.toJournalPresentation()
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    presentation.kinds.take(3).forEach { kind ->
                        Icon(
                            imageVector = kind.icon(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                    Text(
                        buildList {
                            add(presentation.typeSummary)
                            presentation.duration?.let(::add)
                        }.joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                StatusBadge(item.status)
            }
            presentation.detail?.let {
                Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                formatTime(item.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.lastError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.status == InboxStatus.ERROR || item.status == InboxStatus.QUEUED) {
                    OutlinedButton(onClick = { onRetry(item.id) }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text(if (item.status == InboxStatus.ERROR) "Повторить" else "Отправить")
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
private fun StatusBadge(status: String) {
    val badgeColor = when (status) {
        InboxStatus.SYNCED -> DispatcherSuccess
        InboxStatus.ERROR -> DispatcherError
        InboxStatus.QUEUED, InboxStatus.SYNCING -> DispatcherWarning
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        color = badgeColor.copy(alpha = 0.18f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            statusLabel(status),
            style = MaterialTheme.typography.labelMedium,
            color = badgeColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

private fun JournalKind.icon() = when (this) {
    JournalKind.TEXT -> Icons.Default.Description
    JournalKind.PHOTO -> Icons.Default.Image
    JournalKind.FILE -> Icons.Default.AttachFile
    JournalKind.VOICE -> Icons.Default.Mic
    JournalKind.VIDEO -> Icons.Default.Movie
}

private fun statusLabel(status: String) = when (status) {
    InboxStatus.DRAFT -> "Черновик"
    InboxStatus.QUEUED -> "В очереди"
    InboxStatus.SYNCING -> "Отправляется"
    InboxStatus.SYNCED -> "Отправлено"
    InboxStatus.ERROR -> "Ошибка"
    else -> status
}

private fun formatTime(timestamp: Long): String = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(timestamp))
