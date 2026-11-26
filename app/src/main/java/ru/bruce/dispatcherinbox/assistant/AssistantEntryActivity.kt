package ru.bruce.dispatcherinbox.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.bruce.dispatcherinbox.DispatcherApplication
import ru.bruce.dispatcherinbox.data.InboxSource
import ru.bruce.dispatcherinbox.ui.capture.CaptureScreen
import ru.bruce.dispatcherinbox.ui.capture.CaptureViewModel
import ru.bruce.dispatcherinbox.ui.theme.DispatcherInboxTheme

class AssistantEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
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
                    onOpenApp = { destination ->
                        startActivity(android.content.Intent(this, ru.bruce.dispatcherinbox.MainActivity::class.java)
                            .putExtra("destination", destination)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP))
                        finish()
                    },
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
    onOpenApp: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val closeAfterHide: () -> Unit = {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = 400.dp,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = null,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.background(Brush.verticalGradient(listOf(Color(0xFF23242C), Color(0xFF17181E))))
            ) {
                CaptureScreen(
                    viewModel = viewModel,
                    forceVoiceStart = false,
                    onClose = closeAfterHide,
                    compact = true,
                    onOpenApp = { onOpenApp("capture") },
                    onOpenSettings = { onOpenApp("settings") },
                )
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f))
                )
            }
        }
    }
}
