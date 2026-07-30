package ru.bruce.dispatcherinbox.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.bruce.dispatcherinbox.DispatcherApplication
import ru.bruce.dispatcherinbox.data.InboxSource
import ru.bruce.dispatcherinbox.ui.capture.CatChingCaptureSheet
import ru.bruce.dispatcherinbox.ui.capture.CaptureViewModel
import ru.bruce.dispatcherinbox.ui.theme.DispatcherInboxTheme

/**
 * Confirmation UI for Android Sharesheet and selected-text actions. Nothing is saved until the
 * person explicitly chooses the primary action in the CatChing sheet.
 */
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DispatcherApplication).container
        val initialText = textFrom(intent)
        val initialUris = extractUris(intent)

        setContent {
            DispatcherInboxTheme {
                CatChingCaptureSheet(
                    viewModel = viewModel(
                        factory = CaptureViewModel.Factory(
                            application = application,
                            repository = container.inboxRepository,
                            settingsRepository = container.settingsRepository,
                            source = InboxSource.SHARE,
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
