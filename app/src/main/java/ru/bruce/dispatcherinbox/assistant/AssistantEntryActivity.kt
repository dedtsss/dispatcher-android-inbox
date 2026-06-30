package ru.bruce.dispatcherinbox.assistant

import android.os.Bundle
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.bruce.dispatcherinbox.DispatcherApplication
import ru.bruce.dispatcherinbox.data.InboxSource
import ru.bruce.dispatcherinbox.ui.capture.CaptureScreen
import ru.bruce.dispatcherinbox.ui.capture.CaptureViewModel
import ru.bruce.dispatcherinbox.ui.theme.DispatcherInboxTheme

class AssistantEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                        ),
                    ),
                    onDismiss = ::finish,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantCaptureSheet(
    viewModel: CaptureViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sheetHeight = (maxHeight * 0.74f).coerceIn(440.dp, 680.dp)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
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
                    forceVoiceStart = true,
                    onClose = onDismiss,
                    compact = true,
                )
            }
        }
    }
}
