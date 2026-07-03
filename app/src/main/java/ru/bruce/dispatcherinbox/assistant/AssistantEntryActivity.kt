package ru.bruce.dispatcherinbox.assistant

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.bruce.dispatcherinbox.DispatcherApplication
import ru.bruce.dispatcherinbox.MainActivity
import ru.bruce.dispatcherinbox.data.InboxSource
import ru.bruce.dispatcherinbox.ui.capture.CaptureScreen
import ru.bruce.dispatcherinbox.ui.capture.CaptureViewModel
import ru.bruce.dispatcherinbox.ui.theme.DispatcherInboxTheme

class AssistantEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val isAssistantMode = intent.action == Intent.ACTION_ASSIST
        configureAssistantWindow(isAssistantMode)
        val container = (application as DispatcherApplication).container
        setContent {
            DispatcherInboxTheme {
                AssistantCaptureSheet(
                    viewModel = viewModel(
                        factory = CaptureViewModel.Factory(
                            application,
                            container.inboxRepository,
                            container.settingsRepository,
                            InboxSource.ASSISTANT,
                            container.applicationScope,
                        ),
                    ),
                    isAssistantMode = isAssistantMode,
                    onDismiss = ::finish,
                    onOpenSettings = { openMainApp("settings") },
                    onOpenQueue = { openMainApp("queue") },
                )
            }
        }
    }

    private fun configureAssistantWindow(isAssistantMode: Boolean) {
        if (!isAssistantMode) return
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply {
            dimAmount = AssistantWindowStyle.backgroundDim(isAssistantMode)
            alpha = 1f
        }
    }

    private fun openMainApp(destination: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_START_DESTINATION, destination),
        )
        finish()
    }
}

private object AssistantWindowStyle {
    private const val ASSISTANT_PANEL_OPACITY = 0.97f
    private const val ASSISTANT_BACKGROUND_DIM = 0.42f

    fun panelAlpha(isAssistantMode: Boolean): Float =
        if (isAssistantMode) ASSISTANT_PANEL_OPACITY else 1f

    fun backgroundDim(isAssistantMode: Boolean): Float =
        if (isAssistantMode) ASSISTANT_BACKGROUND_DIM else 0f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantCaptureSheet(
    viewModel: CaptureViewModel,
    isAssistantMode: Boolean,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sheetHeight = (maxHeight * 0.90f).coerceAtMost(760.dp)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = Color.Transparent,
            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            scrimColor = Color.Transparent,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .navigationBarsPadding(),
            ) {
                CaptureScreen(
                    viewModel = viewModel,
                    onClose = onDismiss,
                    compact = true,
                    isAssistantMode = isAssistantMode,
                    panelAlpha = AssistantWindowStyle.panelAlpha(isAssistantMode),
                    onOpenSettings = onOpenSettings,
                    onOpenQueue = onOpenQueue,
                )
            }
        }
    }
}
