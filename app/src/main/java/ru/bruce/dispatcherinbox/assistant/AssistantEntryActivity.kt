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
import ru.bruce.dispatcherinbox.ui.capture.CatChingCaptureSheet
import ru.bruce.dispatcherinbox.ui.capture.CaptureViewModel
import ru.bruce.dispatcherinbox.ui.theme.DispatcherInboxTheme

class AssistantEntryActivity : ComponentActivity() {
    private var captureRequestId by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this) { hideAssistant() }
        val isAssistantMode = true
        configureAssistantWindow(isAssistantMode)
        val container = (application as DispatcherApplication).container
        setContent {
            DispatcherInboxTheme {
                key(captureRequestId) {
                    CatChingCaptureSheet(
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
        configureAssistantWindow(true)
    }

    private fun hideAssistant() {
        moveTaskToBack(true)
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

private object AssistantWindowStyle {
    private const val ASSISTANT_BACKGROUND_DIM = 0.18f

    fun backgroundDim(isAssistantMode: Boolean): Float =
        if (isAssistantMode) ASSISTANT_BACKGROUND_DIM else 0f
}
