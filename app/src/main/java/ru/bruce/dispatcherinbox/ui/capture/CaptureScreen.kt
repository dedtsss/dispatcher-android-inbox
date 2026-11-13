package ru.bruce.dispatcherinbox.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import ru.bruce.dispatcherinbox.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
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
    onOpenApp: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val originalLogo = painterResource(R.drawable.catching_logo)
    val logoTextColor = MaterialTheme.colorScheme.onSurface
    val transparentLogo = remember(originalLogo, logoTextColor) { CatchingLogoPainter(originalLogo, logoTextColor) }
    val cat = remember(originalLogo) { CatchingCatPainter(originalLogo) }
    val context = LocalContext.current
    var textExpanded by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var requestedAutoStart by rememberSaveable { mutableStateOf(false) }

    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.startRecording() else viewModel.onAudioPermissionDenied()
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
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

    LaunchedEffect(state.defaultOpenMode, state.assistantOpenMode, forceVoiceStart) {
        if (!requestedAutoStart && state.defaultOpenMode != null && state.assistantOpenMode != null) {
            requestedAutoStart = true
            val mode = CaptureEntryPolicy.mode(compact, state.defaultOpenMode!!, state.assistantOpenMode!!, forceVoiceStart)
            textExpanded = mode == "text" && compact
            if (CaptureEntryPolicy.autoRecord(compact, mode, state.autoStartAssistantRecording, forceVoiceStart)) startWithPermission()
        }
    }
    LaunchedEffect(textExpanded) {
        if (textExpanded) {
            // Ждём появления поля и завершения перемещения нижней карточки.
            kotlinx.coroutines.delay(180)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(viewModel, compact, lifecycleOwner) {
        if (compact) lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            viewModel.close.collect { onClose() }
        }
    }

    val editing = textExpanded || state.text.isNotEmpty() || state.attachments.isNotEmpty()
    BoxWithConstraints(modifier.then(if (compact) Modifier.wrapContentHeight() else Modifier.fillMaxSize()).imePadding()) {
    val availableHeight = maxHeight
    val fullDiameter = ((maxWidth - 40.dp) * 0.60f).coerceIn(176.dp, 224.dp)
    val shortScreen = !compact && availableHeight < fullDiameter + 480.dp
    Column(
        modifier = Modifier
            .then(if (compact) Modifier.wrapContentHeight() else Modifier.fillMaxSize())
            .verticalScroll(rememberScrollState())
            .then(if (compact) Modifier else Modifier.heightIn(min = availableHeight))
            .padding(horizontal = if (compact) 18.dp else 20.dp, vertical = 14.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(if (compact) 56.dp else if (shortScreen) 48.dp else 64.dp), contentAlignment = if (compact) Alignment.Center else Alignment.CenterStart) {
            if (compact) {
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart).size(48.dp)) {
                    Icon(Icons.Default.Close, "Закрыть ассистент")
                }
                Image(cat, contentDescription = "Catching", contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.size(48.dp).clickable(onClick = onOpenApp).padding(2.dp))
                IconButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.CenterEnd).size(48.dp)) {
                    Icon(Icons.Default.Settings, "Настройки ассистента")
                }
            } else {
                Image(transparentLogo, contentDescription = "Catching",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.width(160.dp).height(48.dp))
            }
        }
        if (!compact && !editing) Spacer(Modifier.weight(1f))
        if (compact && !textExpanded) VoiceControl(state, true, onClick = { if (state.isRecording) viewModel.stopRecordingAndSave() else startWithPermission() })
        if (!compact && (!editing || state.isRecording)) FullscreenVoiceControl(
            state, shortScreen = shortScreen, onClick = { if (state.isRecording) viewModel.stopRecordingAndSave() else startWithPermission() },
        )
        if (!compact && !editing) Spacer(Modifier.height(if (shortScreen) 8.dp else 32.dp))
        if (compact) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { textExpanded = !textExpanded }, enabled = !state.isBusy && !state.isRecording,
                modifier = Modifier.weight(1f)) { Icon(if (compact && textExpanded) Icons.Default.Mic else Icons.Default.Keyboard, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (compact && textExpanded) "Голос" else "Текст") }
            OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { filePicker.launch("*/*") }, enabled = !state.isBusy && !state.isRecording,
                modifier = Modifier.weight(1f)) { Icon(Icons.Default.AttachFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Файл") }
        }
        } else {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                onClick = { textExpanded = !textExpanded }, enabled = !state.isBusy && !state.isRecording,
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
            ) {
                Icon(Icons.Default.Description, null, Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("Текст", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                onClick = { filePicker.launch("*/*") }, enabled = !state.isBusy && !state.isRecording,
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
            ) {
                Icon(Icons.Default.FolderOpen, null, Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("Файл", style = MaterialTheme.typography.titleMedium)
            }
        }
        }
        if (textExpanded || state.text.isNotEmpty()) {
            OutlinedTextField(
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.onSurface),
                value = state.text,
                onValueChange = viewModel::setText,
                enabled = !state.isBusy,
                label = { Text("Что записать?") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 112.dp else 180.dp)
                    .focusRequester(focusRequester),
                shape = RoundedCornerShape(20.dp),
            )

        }

        if (state.attachments.isNotEmpty()) {
            Text(
                "Готово к сохранению",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 7.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.attachments, key = { it.id }) { draft ->
                    AttachmentCard(draft = draft, onRemove = { viewModel.removeAttachment(draft) })
                }
            }
        }

        state.statusMessage?.let { message ->
            Text(message, modifier = Modifier.padding(vertical = 8.dp),
                color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (editing) {
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

        }

        if (!compact && !editing) Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(if (compact) 18.dp else 8.dp))
    }
    }
}

@Composable
private fun FullscreenVoiceControl(state: CaptureUiState, shortScreen: Boolean, onClick: () -> Unit) {
    val accent = ru.bruce.dispatcherinbox.ui.theme.CaptureAccent
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val diameter = (maxWidth * 0.60f).coerceIn(176.dp, 224.dp)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(diameter + if (shortScreen) 40.dp else 72.dp).background(
                    Brush.radialGradient(0f to accent.copy(alpha = 0.28f), 0.72f to accent.copy(alpha = 0.16f), 1f to Color.Transparent),
                ), contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick = onClick, enabled = !state.isBusy, shape = CircleShape,
                    modifier = Modifier.size(diameter), contentPadding = PaddingValues(0.dp),
                    border = BorderStroke(3.dp, accent),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color(0xFFF4EAFF)),
                ) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                        ru.bruce.dispatcherinbox.ui.theme.CaptureButtonTop,
                        ru.bruce.dispatcherinbox.ui.theme.CaptureButtonBottom,
                    ))), contentAlignment = Alignment.Center) {
                        Icon(if (state.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            if (state.isRecording) "Остановить и сохранить" else "Начать запись", Modifier.size(76.dp))
                    }
                }
            }
            RecordingLevelIndicator(state.recordingLevels, state.recordingAmplitude, state.isRecording, accent,
                Modifier.fillMaxWidth(0.82f).height(if (shortScreen) 40.dp else 64.dp), barWidth = 3.dp)
            // Место таймера постоянно: старт и остановка не сдвигают основные действия.
            Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
                if (state.isRecording) Text("%02d:%02d".format(state.recordingSeconds / 60, state.recordingSeconds % 60),
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun VoiceControl(state: CaptureUiState, compact: Boolean, onClick: () -> Unit) {
    val amplitude = if (state.isRecording) state.recordingAmplitude else 0f
    val primary = MaterialTheme.colorScheme.onPrimaryContainer
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(if (compact) 132.dp else 156.dp).background(
            Brush.radialGradient(listOf(primary.copy(alpha = 0.10f + amplitude * 0.10f), Color.Transparent))),
            contentAlignment = Alignment.Center) {
            Button(onClick = onClick, enabled = !state.isBusy, shape = CircleShape,
                modifier = Modifier.size(if (compact) 88.dp else 100.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                border = BorderStroke(1.dp, primary.copy(alpha = 0.3f)),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = primary)) {
                Icon(if (state.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    if (state.isRecording) "Остановить и сохранить" else "Начать запись", Modifier.size(34.dp))
            }
        }
        Text(if (state.isRecording) "%02d:%02d".format(state.recordingSeconds / 60, state.recordingSeconds % 60) else "Голос",
            style = MaterialTheme.typography.titleMedium)
        RecordingLevelIndicator(state.recordingLevels, amplitude, state.isRecording, primary)

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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    driveConnected: Boolean,
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
            items.forEach { entry -> RecentItemCard(entry, driveConnected) }
        }
    }
}

@Composable
private fun RecentItemCard(entry: InboxItemWithAttachments, driveConnected: Boolean) {
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
            StatusBadge(item.status, driveConnected)
        }
    }
}

@Composable
private fun StatusBadge(status: String, driveConnected: Boolean) {
    val delivery = ru.bruce.dispatcherinbox.ui.deliveryStatus(status, driveConnected)
    val label = delivery.label
    val color = if (delivery.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
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

@Composable
private fun RecordingLevelIndicator(
    history: List<Float>, level: Float, recording: Boolean, color: Color,
    modifier: Modifier = Modifier.width(120.dp).height(24.dp),
    barWidth: androidx.compose.ui.unit.Dp = 2.dp,
) {
    Canvas(modifier.semantics {
        contentDescription = "Уровень микрофона"
        stateDescription = if (recording) "Запись · ${(level * 100).toInt()}%" else "Запись остановлена"
    }) {
        val slot = size.width / RecordingLevelMeter.HISTORY_SIZE
        drawLine(color.copy(alpha = 0.2f), androidx.compose.ui.geometry.Offset(0f, size.height / 2),
            androidx.compose.ui.geometry.Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx())
        if (recording) history.forEachIndexed { index, value ->
            if (value > 0f) {
                val height = (value.coerceIn(0f, 1f) * size.height * 0.92f).coerceAtLeast(1.dp.toPx())
                drawRoundRect(color.copy(alpha = 0.85f),
                    topLeft = androidx.compose.ui.geometry.Offset(index * slot + (slot - barWidth.toPx()) / 2, (size.height - height) / 2),
                    size = androidx.compose.ui.geometry.Size(barWidth.toPx(), height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
            }
        }
    }
}
