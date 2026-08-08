package ru.bruce.dispatcherinbox.assistant

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.bruce.dispatcherinbox.DispatcherApplication
import ru.bruce.dispatcherinbox.MainActivity
import ru.bruce.dispatcherinbox.data.InboxSource
import ru.bruce.dispatcherinbox.ui.capture.CatChingCaptureOverlay
import ru.bruce.dispatcherinbox.ui.capture.CaptureViewModel
import ru.bruce.dispatcherinbox.ui.theme.DispatcherInboxTheme

class AssistantEntryActivity : ComponentActivity() {
    private var captureRequestId by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
        )
        onBackPressedDispatcher.addCallback(this) { hideAssistant() }
        val isAssistantMode = true
        val container = (application as DispatcherApplication).container
        setContent {
            DispatcherInboxTheme {
                key(captureRequestId) {
                    CatChingCaptureOverlay(
                        viewModel = viewModel(
                            factory = CaptureViewModel.Factory(
                                application,
                                container.inboxRepository,
                                container.settingsRepository,
                                InboxSource.ASSISTANT,
                                container.applicationScope,
                            ),
                        ),
                        allowAutoStartVoice = isAssistantMode,
                        voiceAsDraft = false,
                        onDismiss = ::hideAssistant,
                        onSaved = ::hideAssistant,
                        onOpenSettings = { openMainApp("settings") },
                        onOpenQueue = { openMainApp("queue") },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureRequestId += 1
    }

    private fun hideAssistant() {
        moveTaskToBack(true)
    }

    private fun openMainApp(destination: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_START_DESTINATION, destination)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
        )
        moveTaskToBack(true)
    }
}
