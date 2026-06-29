package ru.bruce.dispatcherinbox.assistant

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class DispatcherVoiceInteractionService : VoiceInteractionService()

class DispatcherVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        DispatcherVoiceInteractionSession(this)
}

private class DispatcherVoiceInteractionSession(
    context: android.content.Context,
) : VoiceInteractionSession(context) {
    private var launched = false

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        if (launched) return
        launched = true
        context.startActivity(
            Intent(context, AssistantEntryActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
        )
        finish()
    }
}
