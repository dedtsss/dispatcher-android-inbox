package ru.bruce.dispatcherinbox.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.bruce.dispatcherinbox.BuildConfig

@Composable
fun UpdateSection() {
    val context = LocalContext.current
    val controller = remember { UpdateController(context) }
    var state by remember { mutableStateOf(UpdateState()) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(controller, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            state = controller.recover()
            while (true) {
                delay(2000)
                if (!busy && state.status in setOf(UpdateStatus.DOWNLOADING, UpdateStatus.PAUSED)) state = controller.recover()
            }
        }
    }
    fun action(block: suspend () -> UpdateState) {
        if (busy) return
        busy = true
        scope.launch { try { state = block() } finally { busy = false } }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Версия и обновления", style = MaterialTheme.typography.titleLarge)
        Text("Catching ${BuildConfig.VERSION_NAME} (versionCode ${BuildConfig.VERSION_CODE})")
        if (state.message.isNotEmpty()) Text(state.message + if (state.version.isNotEmpty()) " · ${state.version}" else "")
        Button(onClick = { action { controller.check() } }, enabled = !busy) { Text(if (busy) "Подождите…" else "Проверить обновление") }
        when (state.status) {
            UpdateStatus.AVAILABLE, UpdateStatus.FAILED -> if (state.canDownload) Button(onClick = { action { controller.download() } }, enabled = !busy) { Text("Скачать обновление") }
            UpdateStatus.READY -> Button(onClick = { action { controller.install() } }, enabled = !busy) { Text("Установить") }
            else -> Unit
        }
    }
}
