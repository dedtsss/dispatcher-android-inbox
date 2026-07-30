package ru.bruce.dispatcherinbox.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.bruce.dispatcherinbox.DispatcherApplication
import ru.bruce.dispatcherinbox.data.InboxSource
import ru.bruce.dispatcherinbox.ui.capture.CatChingCaptureSheet
import ru.bruce.dispatcherinbox.ui.capture.CaptureViewModel
import ru.bruce.dispatcherinbox.ui.theme.DispatcherInboxTheme

class AssistantEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DispatcherApplication).container
        setContent {
            DispatcherInboxTheme {
                CatChingCaptureSheet(
                    viewModel = viewModel(
                        factory = CaptureViewModel.Factory(
                            application,
                            container.inboxRepository,
                            container.settingsRepository,
                            InboxSource.ASSISTANT,
                            closeOnSuccessfulSave = true,
                        ),
                    ),
                    allowAutoStartVoice = true,
                    onDismiss = ::finish,
                    onSaved = ::finish,
                )
            }
        }
    }
}
