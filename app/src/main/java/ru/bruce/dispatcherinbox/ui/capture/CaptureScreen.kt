package ru.bruce.dispatcherinbox.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import ru.bruce.dispatcherinbox.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.bruce.dispatcherinbox.data.AttachmentDraft
import ru.bruce.dispatcherinbox.data.InboxItemWithAttachments
import ru.bruce.dispatcherinbox.data.InboxStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    forceVoiceStart: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var textExpanded by rememberSaveable { mutableStateOf(false) }
    var requestedAutoStart by rememberSaveable { mutableStateOf(false) }

    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.startRecording() else viewModel.onAudioPermissionDenied()
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        viewModel.addAttachments(uris)
    }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20),
    ) { uris ->
        viewModel.addAttachments(uris)
    }

    fun startWithPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.startRecording()
        } else {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(state.defaultOpenMode, forceVoiceStart) {
        if (!requestedAutoStart && state.defaultOpenMode != null &&
            (forceVoiceStart || state.defaultOpenMode == "voice")
        ) {
            requestedAutoStart = true
            startWithPermission()
        }
    }
    LaunchedEffect(viewModel) { viewModel.close.collect { onClose() } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) 18.dp else 20.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(if (compact) 76.dp else 120.dp)
                .background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.catching_logo_wide), contentDescription = "Catching", modifier = Modifier.fillMaxWidth(0.7f))
        }
        Text("Сначала на телефоне · затем в Google Drive", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { if (state.isRecording) viewModel.stopRecordingAndSave() else startWithPermission() },
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth().height(if (compact) 72.dp else 96.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Icon(if (state.isRecording) Icons.Default.Stop else Icons.Default.Mic, null, Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Text(if (state.isRecording) "Остановить и сохранить" else "Голос", style = MaterialTheme.typography.titleLarge)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { textExpanded = !textExpanded }, enabled = !state.isBusy && !state.isRecording,
                modifier = Modifier.weight(1f)) { Text("Текст") }
            OutlinedButton(onClick = { filePicker.launch("*/*") }, enabled = !state.isBusy && !state.isRecording,
                modifier = Modifier.weight(1f)) { Text("Файл") }
        }
        if (textExpanded || state.text.isNotEmpty()) {
        OutlinedTextField(
            value = state.text,
            onValueChange = viewModel::setText,
            enabled = !state.isBusy,
            label = { Text("Что записать?") },
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 112.dp else 180.dp),
            shape = RoundedCornerShape(20.dp),
        )

        }

        if (state.attachments.isNotEmpty()) {
            Text(
                "Готово к сохранению",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 7.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.attachments, key = { it.id }) { draft ->
                    AttachmentCard(draft = draft, onRemove = { viewModel.removeAttachment(draft) })
                }
            }
        }

        if (state.isRecording) Text("Идёт запись оригинала M4A…", Modifier.padding(vertical = 12.dp))
        state.statusMessage?.let { message ->
            Text(message, modifier = Modifier.padding(vertical = 8.dp),
                color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = viewModel::save,
            enabled = !state.isBusy && !state.isRecording &&
                (state.text.isNotBlank() || state.attachments.isNotEmpty()),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            Text(if (state.isBusy) "Сохраняем…" else "Сохранить")
        }

        if (!compact) RecentJournal(
            items = state.recentItems.take(if (compact) 2 else 4),
            modifier = Modifier.padding(top = 16.dp),
        )
        Spacer(Modifier.height(if (compact) 18.dp else 8.dp))
    }
}

@Composable
private fun AttachmentCard(draft: AttachmentDraft, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.width(292.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 13.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                attachmentIcon(draft.mimeType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    draft.originalName ?: "Вложение",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${attachmentTypeLabel(draft.mimeType)} · ${formatSize(draft.sizeBytes)}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Убрать вложение")
            }
        }
    }
}

@Composable
private fun RecentJournal(
    items: List<InboxItemWithAttachments>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Недавние записи", style = MaterialTheme.typography.titleSmall)
            if (items.isNotEmpty()) {
                Text(
                    "${items.size} посл.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (items.isEmpty()) {
            Text(
                "После сохранения здесь появится краткий журнал",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            items.forEach { entry -> RecentItemCard(entry) }
        }
    }
}

@Composable
private fun RecentItemCard(entry: InboxItemWithAttachments) {
    val item = entry.item
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                recentItemIcon(entry),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    recentSummary(entry),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${recentTypeLabel(entry)} · ${formatTime(item.createdAt)}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(item.status)
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val color: Color
    val label: String
    when (status) {
        InboxStatus.SYNCED -> {
            color = MaterialTheme.colorScheme.primaryContainer
            label = "Отправлено"
        }
        InboxStatus.ERROR -> {
            color = MaterialTheme.colorScheme.errorContainer
            label = "Ошибка"
        }
        InboxStatus.SYNCING -> {
            color = MaterialTheme.colorScheme.tertiaryContainer
            label = "Отправка"
        }
        else -> {
            color = MaterialTheme.colorScheme.secondaryContainer
            label = "В очереди"
        }
    }
    Surface(color = color, shape = RoundedCornerShape(9.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun attachmentIcon(mimeType: String?): ImageVector = when {
    mimeType?.startsWith("image/") == true -> Icons.Default.Image
    mimeType?.startsWith("video/") == true -> Icons.Default.Movie
    mimeType?.startsWith("audio/") == true -> Icons.Default.Audiotrack
    mimeType == "application/pdf" || mimeType?.startsWith("text/") == true -> Icons.Default.Description
    else -> Icons.Default.AttachFile
}

private fun recentItemIcon(entry: InboxItemWithAttachments): ImageVector =
    if (entry.attachments.isEmpty()) Icons.Default.Description
    else attachmentIcon(entry.attachments.first().mimeType)

private fun attachmentTypeLabel(mimeType: String?): String {
    val category = when {
        mimeType?.startsWith("image/") == true -> "Изображение"
        mimeType?.startsWith("video/") == true -> "Видео"
        mimeType?.startsWith("audio/") == true -> "Аудио"
        mimeType == "application/pdf" -> "Документ"
        mimeType?.startsWith("text/") == true -> "Документ"
        else -> "Файл"
    }
    val subtype = mimeType?.substringAfter('/')?.uppercase(Locale.ROOT)?.take(12)
    return if (subtype.isNullOrBlank()) category else "$category · $subtype"
}

private fun recentSummary(entry: InboxItemWithAttachments): String {
    entry.item.text?.takeIf { it.isNotBlank() }?.let { return it }
    val first = entry.attachments.firstOrNull()?.originalName ?: "Запись без названия"
    val remaining = entry.attachments.size - 1
    return if (remaining > 0) "$first и еще $remaining" else first
}

private fun recentTypeLabel(entry: InboxItemWithAttachments): String {
    if (!entry.item.text.isNullOrBlank() && entry.attachments.isNotEmpty()) return "Текст и файлы"
    if (entry.attachments.isNotEmpty()) return attachmentTypeLabel(entry.attachments.first().mimeType)
    return "Текст"
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f МБ", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.getDefault(), "%.0f КБ", bytes / 1024.0)
    else -> "$bytes Б"
}

private val journalTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")
    .withZone(ZoneId.systemDefault())

private fun formatTime(timestamp: Long): String = journalTimeFormatter.format(Instant.ofEpochMilli(timestamp))
