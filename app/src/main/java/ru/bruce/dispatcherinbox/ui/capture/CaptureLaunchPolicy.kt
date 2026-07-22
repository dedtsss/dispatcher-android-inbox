package ru.bruce.dispatcherinbox.ui.capture

import ru.bruce.dispatcherinbox.data.DefaultOpenMode

object CaptureLaunchPolicy {
    fun shouldAutoStartRecording(
        isAssistantMode: Boolean,
        defaultOpenMode: String?,
        autoStartAssistantRecording: Boolean,
    ): Boolean = isAssistantMode &&
        defaultOpenMode == DefaultOpenMode.VOICE &&
        autoStartAssistantRecording
}
