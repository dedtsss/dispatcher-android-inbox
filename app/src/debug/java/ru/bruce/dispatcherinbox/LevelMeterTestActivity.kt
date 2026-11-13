package ru.bruce.dispatcherinbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ru.bruce.dispatcherinbox.data.InboxSource
import ru.bruce.dispatcherinbox.ui.capture.CaptureScreen
import ru.bruce.dispatcherinbox.ui.capture.CaptureViewModel
import ru.bruce.dispatcherinbox.ui.theme.DispatcherInboxTheme

/** Только debug: управляемый пик для UI-проверки. Аудио по-прежнему пишет настоящий MediaRecorder. */
class LevelMeterTestActivity : ComponentActivity() {
    var testPeak: Int = 0
    val measuredPeaks = mutableListOf<Int>()
    lateinit var model: CaptureViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as DispatcherApplication).container
        model = androidx.lifecycle.ViewModelProvider(this, object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                CaptureViewModel(application, container.inboxRepository, container.settingsRepository, InboxSource.MANUAL,
                    amplitudeSource = { testPeak.also { measuredPeaks.add(it) } }) as T
        })[CaptureViewModel::class.java]
        setContent {
            DispatcherInboxTheme {
                androidx.compose.material3.Surface {
                    CaptureScreen(model, forceVoiceStart = false, onClose = ::finish)
                }
            }
        }
    }

}
