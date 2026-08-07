package ru.bruce.dispatcherinbox.ui.capture

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.bruce.dispatcherinbox.R

/** Lightweight CatChing entry surface shared by manual and external-input launches. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatChingCaptureSheet(
    viewModel: CaptureViewModel,
    allowAutoStartVoice: Boolean,
    voiceAsDraft: Boolean = true,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    LaunchedEffect(sheetState) {
        // Keep the comfortable, tall launch state while retaining the collapsed
        // and expanded drag anchors for users who want to resize the sheet.
        sheetState.expand()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.05f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f),
        ) {
            CatChingSheetHeader()
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            CaptureScreen(
                viewModel = viewModel,
                onClose = onSaved,
                isAssistantMode = allowAutoStartVoice,
                allowAutoStartVoice = allowAutoStartVoice,
                voiceAsDraft = voiceAsDraft,
                modifier = Modifier.weight(1f),
                inSheet = true,
                showRecent = false,
                onOpenSettings = onOpenSettings,
                onOpenQueue = onOpenQueue,
            )
        }
    }
}

@Composable
private fun CatChingSheetHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.catching_logo),
            contentDescription = "Логотип CatChing",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(104.dp)
                .clip(RoundedCornerShape(24.dp)),
        )
    }
}
