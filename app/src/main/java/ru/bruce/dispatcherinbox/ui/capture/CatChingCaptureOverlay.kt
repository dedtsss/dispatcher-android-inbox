package ru.bruce.dispatcherinbox.ui.capture

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.bruce.dispatcherinbox.R

private const val OVERLAY_ANIMATION_MILLIS = 140

/**
 * Fixed CatChing surface used by assistant, process-text and the launcher entry.
 *
 * This is deliberately a regular Compose layout rather than a Material bottom
 * sheet: the panel has one stable bottom position and lets only its scrollable
 * content/footer respond to IME insets.
 */
@Composable
fun CatChingCaptureOverlay(
    viewModel: CaptureViewModel,
    allowAutoStartVoice: Boolean,
    voiceAsDraft: Boolean = true,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
) {
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }
    LaunchedEffect(closing) {
        if (closing) {
            delay(OVERLAY_ANIMATION_MILLIS.toLong())
            onDismiss()
        }
    }
    BackHandler(enabled = !closing) {
        closing = true
        visible = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f)),
    ) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            enter = fadeIn(tween(OVERLAY_ANIMATION_MILLIS)) +
                slideInVertically(
                    animationSpec = tween(OVERLAY_ANIMATION_MILLIS),
                    initialOffsetY = { fullHeight -> fullHeight / 18 },
                ),
            exit = fadeOut(tween(OVERLAY_ANIMATION_MILLIS)) +
                slideOutVertically(
                    animationSpec = tween(OVERLAY_ANIMATION_MILLIS),
                    targetOffsetY = { fullHeight -> fullHeight / 18 },
                ),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    OverlayHandle()
                    CatChingOverlayHeader()
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    )
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
    }
}

@Composable
private fun OverlayHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)),
        )
    }
}

@Composable
private fun CatChingOverlayHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.catching_logo_wide),
            contentDescription = "Логотип CatChing",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .fillMaxHeight(),
        )
    }
}
