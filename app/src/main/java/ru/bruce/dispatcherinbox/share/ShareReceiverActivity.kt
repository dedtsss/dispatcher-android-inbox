package ru.bruce.dispatcherinbox.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.bruce.dispatcherinbox.DispatcherApplication
import ru.bruce.dispatcherinbox.data.AttachmentDraft
import ru.bruce.dispatcherinbox.data.InboxSource
import ru.bruce.dispatcherinbox.ui.capture.CatChingCaptureOverlay
import ru.bruce.dispatcherinbox.ui.capture.CaptureViewModel
import ru.bruce.dispatcherinbox.ui.theme.DispatcherInboxTheme

class ShareReceiverActivity : ComponentActivity() {
    private var message by mutableStateOf("Сохраняем в CatChing…")
    private var running by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incomingText = textFrom(intent)
        val incomingUris = extractUris(intent)
        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            enableEdgeToEdge()
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            )
            showProcessTextSheet(incomingText, incomingUris)
        } else {
            showShareProgress()
            lifecycleScope.launch { receiveShare(incomingText, incomingUris) }
        }
    }

    private fun showProcessTextSheet(initialText: String?, initialUris: List<Uri>) {
        val container = (application as DispatcherApplication).container
        setContent {
            DispatcherInboxTheme {
                CatChingCaptureOverlay(
                    viewModel = viewModel(
                        factory = CaptureViewModel.Factory(
                            application = application,
                            repository = container.inboxRepository,
                            settingsRepository = container.settingsRepository,
                            source = InboxSource.SELECTION_TEXT,
                            applicationScope = container.applicationScope,
                            initialText = initialText,
                            initialUris = initialUris,
                            closeOnSuccessfulSave = true,
                        ),
                    ),
                    allowAutoStartVoice = false,
                    onDismiss = ::finish,
                    onSaved = ::finish,
                )
            }
        }
    }

    private fun showShareProgress() {
        setContent {
            DispatcherInboxTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (running) CircularProgressIndicator(modifier = Modifier.padding(bottom = 20.dp))
                    Text(message, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    private suspend fun receiveShare(text: String?, uris: List<Uri>) {
        val repository = (application as DispatcherApplication).container.inboxRepository
        val drafts = mutableListOf<AttachmentDraft>()
        try {
            uris.forEach { drafts += repository.stage(it) }
            repository.save(text, drafts, InboxSource.SHARE)
            message = "Сохранено в очередь CatChing"
        } catch (error: Exception) {
            drafts.forEach { runCatching { repository.discardDraft(it) } }
            message = error.message ?: "Не удалось сохранить"
        }
        running = false
        delay(1_200)
        finish()
    }

    private fun textFrom(intent: Intent): String? = when (intent.action) {
        Intent.ACTION_PROCESS_TEXT ->
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        else -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
    }

    private fun extractUris(intent: Intent): List<Uri> {
        val result = mutableListOf<Uri>()
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(result::add)
        IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?.let(result::addAll)
        intent.clipData?.let { clip ->
            repeat(clip.itemCount) { index -> clip.getItemAt(index).uri?.let(result::add) }
        }
        return result.distinct()
    }
}
