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

    fun shouldAutoFocusText(
        isAssistantMode: Boolean,
        inSheet: Boolean,
        defaultOpenMode: String?,
    ): Boolean = !isAssistantMode && !inSheet && defaultOpenMode == DefaultOpenMode.TEXT
}
