package ru.bruce.dispatcherinbox

import android.content.Intent

enum class AppDestination(val route: String) {
    CAPTURE("capture"),
    QUEUE("queue"),
    SETTINGS("settings"),
}

object AppEntryPolicy {
    private val assistantActions = setOf(
        Intent.ACTION_ASSIST,
        Intent.ACTION_VOICE_COMMAND,
        "android.intent.action.VOICE_ASSIST",
    )

    fun isAssistantAction(action: String?): Boolean = action in assistantActions

    fun destination(requestedRoute: String?, assistantEntry: Boolean = false): AppDestination {
        if (assistantEntry) return AppDestination.CAPTURE
        return AppDestination.entries.firstOrNull { it.route == requestedRoute } ?: AppDestination.CAPTURE
    }
}
