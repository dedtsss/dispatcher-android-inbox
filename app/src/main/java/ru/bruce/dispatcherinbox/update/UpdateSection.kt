package ru.bruce.dispatcherinbox.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.bruce.dispatcherinbox.BuildConfig
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun UpdateSection() {
    val context = LocalContext.current
    val controller = remember { UpdateController(context) }
    var state by remember { mutableStateOf(UpdateState()) }
    var busy by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(controller, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (!busy) state = controller.recover()
            while (true) {
                delay(2000)
                if (!busy && state.status in setOf(UpdateStatus.DOWNLOADING, UpdateStatus.PAUSED)) state = controller.recover()
            }
        }
    }
    fun action(label: String, block: suspend () -> UpdateState) {
        if (busy) return
        busy = true
        operation = label
        scope.launch { try { state = block() } finally { busy = false; operation = "" } }
    }
    val transferring = state.status in setOf(UpdateStatus.DOWNLOADING, UpdateStatus.PAUSED)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Версия и обновления", style = MaterialTheme.typography.titleLarge)
        Text("Catching ${BuildConfig.VERSION_NAME} (versionCode ${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
        Text(if (busy) operation else state.message + if (state.version.isNotEmpty()) " · ${state.version}" else "",
            color = if (!busy && state.status == UpdateStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (transferring) {
            val received = state.downloadedBytes
            val total = state.totalBytes
            if (received != null) Text(if (total != null) "Получено ${formatUpdateSize(received)} из ${formatUpdateSize(total)}"
                else "Получено ${formatUpdateSize(received)} · полный размер пока неизвестен", style = MaterialTheme.typography.bodySmall)
            if (state.status == UpdateStatus.DOWNLOADING) {
                if (received != null && total != null) LinearProgressIndicator(progress = { (received.toDouble() / total).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        state.checkedAtMillis?.let { timestamp ->
            val date = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp))
            Text("Последняя проверка: $date · ${state.checkResult}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = { action("Проверяем канал обновлений…") { controller.check() } }, enabled = !busy && !transferring,
            modifier = Modifier.heightIn(min = 48.dp)) { Text("Проверить обновление") }
        when (state.status) {
            UpdateStatus.AVAILABLE, UpdateStatus.FAILED -> if (state.canDownload) Button(
                onClick = { action("Передаём скачивание APK системе Android…") { controller.download() } }, enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(if (state.status == UpdateStatus.FAILED) "Повторить скачивание" else "Скачать обновление") }
            UpdateStatus.READY -> Button(onClick = { action("Проверяем APK и открываем системную установку…") { controller.install() } }, enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Установить") }
            else -> Unit
        }
    }
}

private fun formatUpdateSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes.coerceAtLeast(0)} Б"
    bytes < 1024 * 1024 -> String.format(Locale.forLanguageTag("ru"), "%.1f КиБ", bytes / 1024.0)
    else -> String.format(Locale.forLanguageTag("ru"), "%.1f МиБ", bytes / (1024.0 * 1024))
}
