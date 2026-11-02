package ru.bruce.dispatcherinbox.ui.capture

object CaptureEntryPolicy {
    fun mode(assistant: Boolean, mainMode: String, assistantMode: String, forceVoice: Boolean): String =
        if (assistant) assistantMode else if (forceVoice) "voice" else mainMode

    fun autoRecord(assistant: Boolean, mode: String, assistantAuto: Boolean, forceVoice: Boolean): Boolean =
        mode == "voice" && if (assistant) assistantAuto else forceVoice || mode == "voice"
}
