package ru.bruce.dispatcherinbox.assistant

import ru.bruce.dispatcherinbox.MainActivity
import ru.bruce.dispatcherinbox.data.InboxSource

class AssistantEntryActivity : MainActivity() {
    override val forceVoiceStart: Boolean = true
    override val captureSource: String = InboxSource.ASSISTANT
}
