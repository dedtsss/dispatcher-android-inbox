package ru.bruce.dispatcherinbox.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.bruce.dispatcherinbox.DispatcherApplication
import ru.bruce.dispatcherinbox.data.AttachmentDraft
import ru.bruce.dispatcherinbox.data.InboxSource
import ru.bruce.dispatcherinbox.ui.theme.DispatcherInboxTheme

class ShareReceiverActivity : ComponentActivity() {
    private var message by mutableStateOf("Сохраняем в Приёмник…")
    private var running by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        lifecycleScope.launch { receiveShare(intent) }
    }

    private suspend fun receiveShare(intent: Intent) {
        val repository = (application as DispatcherApplication).container.inboxRepository
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val drafts = mutableListOf<AttachmentDraft>()
        try {
            extractUris(intent).forEach { drafts += repository.stage(it) }
            repository.save(text, drafts, InboxSource.SHARE)
            message = "Сохранено в очередь"
        } catch (error: Exception) {
            drafts.forEach { runCatching { repository.discardDraft(it) } }
            message = error.message ?: "Не удалось сохранить"
        }
        running = false
        delay(1_200)
        finish()
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
