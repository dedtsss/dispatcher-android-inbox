package ru.bruce.dispatcherinbox.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ru.bruce.dispatcherinbox.BuildConfig
import ru.bruce.dispatcherinbox.data.AttachmentDraft
import ru.bruce.dispatcherinbox.data.DefaultOpenMode
import ru.bruce.dispatcherinbox.data.InboxItemWithAttachments
import ru.bruce.dispatcherinbox.data.InboxStatus
import ru.bruce.dispatcherinbox.data.JournalKind
import ru.bruce.dispatcherinbox.data.toJournalPresentation
import ru.bruce.dispatcherinbox.ui.theme.DispatcherBackground
import ru.bruce.dispatcherinbox.ui.theme.DispatcherError
import ru.bruce.dispatcherinbox.ui.theme.DispatcherSuccess
import ru.bruce.dispatcherinbox.ui.theme.DispatcherWarning
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

private const val NOTE_MAX_LENGTH = 2_000

@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    isAssistantMode: Boolean = false,
    panelAlpha: Float = 1f,
    allowAutoStartVoice: Boolean = true,
    voiceAsDraft: Boolean = false,
    inSheet: Boolean = false,
    showRecent: Boolean = true,
    onOpenSettings: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var requestedAutoStart by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val noteFocusRequester = remember { FocusRequester() }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onHostActiveChanged(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.onHostActiveChanged(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.onHostActiveChanged(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onHostActiveChanged(false)
        }
    }

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

    LaunchedEffect(
        state.defaultOpenMode,
        state.autoStartAssistantRecording,
        isAssistantMode,
    ) {
        if (!requestedAutoStart && state.defaultOpenMode != null) {
            requestedAutoStart = true
            if (allowAutoStartVoice && !state.hasInitialContent &&
                CaptureLaunchPolicy.shouldAutoStartRecording(
                    isAssistantMode = isAssistantMode,
                    defaultOpenMode = state.defaultOpenMode,
                    autoStartAssistantRecording = state.autoStartAssistantRecording,
                )
            ) {
                startWithPermission()
            }
        }
    }
    LaunchedEffect(state.defaultOpenMode) {
        if (CaptureLaunchPolicy.shouldAutoFocusText(
                isAssistantMode = isAssistantMode,
                inSheet = inSheet,
                defaultOpenMode = state.defaultOpenMode,
            )
        ) {
            noteFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(viewModel) { viewModel.close.collect { onClose() } }

    val rootModifier = modifier
        .fillMaxSize()
        .background(
            when {
                isAssistantMode || inSheet -> Color.Transparent
                else -> DispatcherBackground
            },
        )
        .let { base -> if (inSheet) base else base.imePadding() }

    BoxWithConstraints(
        modifier = rootModifier,
    ) {
        val shortScreen = maxHeight < 700.dp
        val journalLimit = if (compact || shortScreen) 3 else 5
        val noteHeight = when {
            inSheet -> 156.dp
            compact -> 148.dp
            shortScreen -> 156.dp
            else -> 188.dp
        }

        Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = when {
                            inSheet -> 0.dp
                            compact -> 8.dp
                            else -> 12.dp
                        },
                        vertical = when {
                            inSheet -> 0.dp
                            compact -> 0.dp
                            else -> 10.dp
                        },
                    ),
            shape = RoundedCornerShape(if (inSheet) 0.dp else if (compact) 28.dp else 32.dp),
            color = if (inSheet) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surface.copy(alpha = panelAlpha),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = if (inSheet) BorderStroke(0.dp, Color.Transparent)
            else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)),
            tonalElevation = if (inSheet) 0.dp else 2.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = when {
                                inSheet -> 18.dp
                                compact -> 16.dp
                                else -> 20.dp
                            },
                            vertical = 14.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(if (compact || inSheet) 10.dp else 12.dp),
                ) {
                    if (!inSheet) CaptureHeader(onOpenSettings = onOpenSettings)

                    NoteField(
                        value = state.text,
                        onValueChange = viewModel::setText,
                        enabled = !state.isBusy,
                        height = noteHeight,
                        focusRequester = noteFocusRequester,
                    )

                    if (state.attachments.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.attachments, key = { it.id }) { draft ->
                                AttachmentCard(
                                    draft = draft,
                                    onRemove = { viewModel.removeAttachment(draft) },
                                )
                            }
                        }
                    }

                    if (voiceAsDraft) {
                        DraftActionButtons(
                            isRecording = state.isRecording,
                            isBusy = state.isBusy,
                            onVoiceClick = {
                                if (state.isRecording) viewModel.stopRecordingAndAdd() else startWithPermission()
                            },
                            onCancelRecording = viewModel::cancelRecording,
                            onFileClick = { filePicker.launch("*/*") },
                            onPhotoClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                                )
                            },
                        )
                    } else {
                        ActionButtons(
                            isRecording = state.isRecording,
                            voiceModeSelected = state.defaultOpenMode == DefaultOpenMode.VOICE,
                            isBusy = state.isBusy,
                            onVoiceClick = {
                                if (state.isRecording) viewModel.cancelRecording() else startWithPermission()
                            },
                            onFileClick = { filePicker.launch("*/*") },
                            onPhotoClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        )
                    }

                    state.statusMessage?.let { message ->
                        StatusMessage(
                            message = message,
                            isError = state.isError,
                            isRecording = state.isRecording,
                        )
                    }

                    if (!inSheet) {
                        DynamicSendButton(
                            isRecording = state.isRecording,
                            isSending = state.isBusy,
                            audioLevel = state.recordingAmplitude,
                            enabled = !state.isBusy && if (voiceAsDraft) {
                                !state.isRecording && (state.text.isNotBlank() || state.attachments.isNotEmpty())
                            } else {
                                state.isRecording || state.text.isNotBlank() || state.attachments.isNotEmpty()
                            },
                            onClick = {
                                if (!voiceAsDraft && state.isRecording) viewModel.stopRecordingAndSave()
                                else viewModel.save()
                            },
                        )
                    }

                    if (showRecent) {
                        RecentJournal(
                            items = state.recentItems.take(journalLimit),
                            onOpenQueue = onOpenQueue,
                        )
                    }

                    if (!inSheet) {
                        CaptureFooter(
                            onOpenSettings = onOpenSettings,
                            onOpenAbout = { showAbout = true },
                        )
                    }
                }

                if (inSheet) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .navigationBarsPadding(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                        tonalElevation = 2.dp,
                    ) {
                        DynamicSendButton(
                            isRecording = state.isRecording,
                            isSending = state.isBusy,
                            audioLevel = state.recordingAmplitude,
                            enabled = !state.isBusy && if (voiceAsDraft) {
                                !state.isRecording && (state.text.isNotBlank() || state.attachments.isNotEmpty())
                            } else {
                                state.isRecording || state.text.isNotBlank() || state.attachments.isNotEmpty()
                            },
                            onClick = {
                                if (!voiceAsDraft && state.isRecording) viewModel.stopRecordingAndSave()
                                else viewModel.save()
                            },
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text("О приложении") },
            text = {
                Text(
                    "CatChing I ${BuildConfig.VERSION_NAME}\n\n" +
                        "Быстрый приём текста, голоса и файлов с надёжной локальной очередью.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("Готово") }
            },
        )
    }
}

@Composable
private fun CaptureHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "CatChing I",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Настройки",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoteField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    height: androidx.compose.ui.unit.Dp,
    focusRequester: FocusRequester,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { updated ->
            if (updated.length <= NOTE_MAX_LENGTH) onValueChange(updated)
        },
        enabled = enabled,
        placeholder = { Text("Введите заметку...") },
        supportingText = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("${value.length}/$NOTE_MAX_LENGTH")
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .height(height),
        shape = RoundedCornerShape(22.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
private fun ActionButtons(
    isRecording: Boolean,
    voiceModeSelected: Boolean,
    isBusy: Boolean,
    onVoiceClick: () -> Unit,
    onFileClick: () -> Unit,
    onPhotoClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CaptureActionButton(
            text = if (isRecording) "Отмена" else "Голос",
            icon = if (isRecording) Icons.Default.Cancel else Icons.Default.Mic,
            enabled = !isBusy,
            active = isRecording || voiceModeSelected,
            onClick = onVoiceClick,
            modifier = Modifier.weight(1f),
        )
        CaptureActionButton(
            text = "Файл",
            icon = Icons.Default.AttachFile,
            enabled = !isBusy && !isRecording,
            onClick = onFileClick,
            modifier = Modifier.weight(1f),
        )
        CaptureActionButton(
            text = "Фото",
            icon = Icons.Default.CameraAlt,
            enabled = !isBusy && !isRecording,
            onClick = onPhotoClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DraftActionButtons(
    isRecording: Boolean,
    isBusy: Boolean,
    onVoiceClick: () -> Unit,
    onCancelRecording: () -> Unit,
    onFileClick: () -> Unit,
    onPhotoClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CaptureActionButton(
            text = "Файл",
            icon = Icons.Default.AttachFile,
            enabled = !isBusy && !isRecording,
            onClick = onFileClick,
            modifier = Modifier.weight(1f),
        )
        CaptureActionButton(
            text = "Фото/видео",
            icon = Icons.Default.CameraAlt,
            enabled = !isBusy && !isRecording,
            onClick = onPhotoClick,
            modifier = Modifier.weight(1f),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CaptureActionButton(
            text = if (isRecording) "Завершить и добавить" else "Добавить голосовое пояснение",
            icon = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
            enabled = !isBusy,
            active = isRecording,
            onClick = onVoiceClick,
            modifier = Modifier.weight(1f),
        )
        if (isRecording) {
            IconButton(onClick = onCancelRecording, enabled = !isBusy) {
                Icon(Icons.Default.Cancel, contentDescription = "Отменить запись")
            }
        }
    }
}

@Composable
private fun CaptureActionButton(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 50.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            contentColor = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusMessage(message: String, isError: Boolean, isRecording: Boolean) {
    val color = when {
        isError -> MaterialTheme.colorScheme.error
        isRecording -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
fun DynamicSendButton(
    isRecording: Boolean,
    isSending: Boolean,
    audioLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isRecording) {
                AudioWaveform(
                    isRecording = true,
                    audioLevel = audioLevel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(
                    text = when {
                        isSending -> "Сохраняем…"
                        isRecording -> "Отправить"
                        else -> "Отправить"
                    },
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun AudioWaveform(
    isRecording: Boolean,
    audioLevel: Float,
    modifier: Modifier = Modifier,
) {
    if (!isRecording) return

    val level by animateFloatAsState(
        targetValue = if (isRecording) audioLevel.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(220),
        label = "waveformLevel",
    )
    val waveColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier.semantics {
            contentDescription = "Индикатор уровня записи"
        },
    ) {
        val count = 44
        val gap = size.width / (count * 2.05f)
        val barWidth = gap * 0.62f
        val centerY = size.height / 2f
        val baselineHeight = (size.height * 0.018f).coerceAtLeast(1f)
        drawRoundRect(
            color = waveColor.copy(alpha = if (level > 0.01f) 0.30f else 0.24f),
            topLeft = Offset(0f, centerY - baselineHeight / 2f),
            size = Size(size.width, baselineHeight),
            cornerRadius = CornerRadius(baselineHeight, baselineHeight),
        )
        repeat(count) { index ->
            val centerX = (index + 0.5f) * size.width / count
            val normalizedX = centerX / size.width
            val distanceFromCenter = abs(normalizedX - 0.5f)
            if (distanceFromCenter < 0.18f) return@repeat

            val edgeFade = (distanceFromCenter / 0.5f).coerceIn(0f, 1f)
            val centerFade = ((distanceFromCenter - 0.18f) / 0.14f).coerceIn(0f, 1f)
            val pattern = (0.38f + 0.62f * abs(sin(index * 0.62f)))
            val envelope = (1f - abs(normalizedX - 0.5f) * 0.72f).coerceAtLeast(0.38f)
            val barHeight = (baselineHeight + size.height * level * 0.92f * pattern * envelope)
                .coerceAtMost(size.height * 0.84f)
            drawRoundRect(
                color = waveColor.copy(
                    alpha = (0.16f + level * 0.76f) * edgeFade * centerFade,
                ),
                topLeft = Offset(centerX - barWidth / 2f, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth, barWidth),
            )
        }
    }
}

@Composable
private fun AttachmentCard(draft: AttachmentDraft, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.width(276.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 13.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                attachmentIcon(draft.mimeType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
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
    onOpenQueue: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Журнал",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onOpenQueue) { Text("Все") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (items.isEmpty()) {
            Text(
                "Последние записи появятся здесь",
                modifier = Modifier.padding(vertical = 14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            items.forEachIndexed { index, entry ->
                RecentJournalRow(entry)
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 34.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentJournalRow(entry: InboxItemWithAttachments) {
    val item = entry.item
    val status = journalStatus(item.status)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JournalTypeIcons(entry.toJournalPresentation().kinds)
        Spacer(Modifier.width(5.dp))
        Icon(
            imageVector = status.icon,
            contentDescription = status.description,
            tint = status.color,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = recentSummary(entry),
            modifier = Modifier.weight(1f).padding(horizontal = 11.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = formatTime(item.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class JournalStatus(
    val icon: ImageVector,
    val color: Color,
    val description: String,
)

private fun journalStatus(status: String): JournalStatus = when (status) {
    InboxStatus.SYNCED -> JournalStatus(Icons.Default.CheckCircle, DispatcherSuccess, "Отправлено")
    InboxStatus.ERROR -> JournalStatus(Icons.Default.Cancel, DispatcherError, "Ошибка")
    InboxStatus.SYNCING -> JournalStatus(Icons.Default.HourglassTop, DispatcherWarning, "Отправляется")
    InboxStatus.DRAFT -> JournalStatus(Icons.Default.HourglassTop, Color.Gray, "Черновик")
    else -> JournalStatus(Icons.Default.HourglassTop, DispatcherWarning, "В очереди")
}

@Composable
private fun JournalTypeIcons(kinds: List<JournalKind>) {
    Row(horizontalArrangement = Arrangement.spacedBy((-3).dp)) {
        kinds.take(3).forEach { kind ->
            Icon(
                imageVector = when (kind) {
                    JournalKind.TEXT -> Icons.Default.Description
                    JournalKind.PHOTO -> Icons.Default.Image
                    JournalKind.FILE -> Icons.Default.AttachFile
                    JournalKind.VOICE -> Icons.Default.Mic
                    JournalKind.VIDEO -> Icons.Default.Movie
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun CaptureFooter(
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
            Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(7.dp))
            Text("Настройки", maxLines = 1)
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        TextButton(onClick = onOpenAbout, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(7.dp))
            Text("О приложении", maxLines = 1)
        }
    }
}

private fun attachmentIcon(mimeType: String?): ImageVector = when {
    mimeType?.startsWith("image/") == true -> Icons.Default.Image
    mimeType?.startsWith("video/") == true -> Icons.Default.Movie
    mimeType?.startsWith("audio/") == true -> Icons.Default.Audiotrack
    mimeType == "application/pdf" || mimeType?.startsWith("text/") == true -> Icons.Default.Description
    else -> Icons.Default.AttachFile
}

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
    val presentation = entry.toJournalPresentation()
    val status = journalStatus(entry.item.status).description.lowercase()
    return buildList {
        add(presentation.typeSummary)
        presentation.duration?.let(::add)
        add(status)
        presentation.detail?.let(::add)
    }.joinToString(" · ")
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f МБ", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.getDefault(), "%.0f КБ", bytes / 1024.0)
    else -> "$bytes Б"
}

private val journalTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    .withZone(ZoneId.systemDefault())

private fun formatTime(timestamp: Long): String = journalTimeFormatter.format(Instant.ofEpochMilli(timestamp))
