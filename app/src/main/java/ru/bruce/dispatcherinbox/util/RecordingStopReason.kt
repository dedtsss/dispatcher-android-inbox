package ru.bruce.dispatcherinbox.util

enum class RecordingStopReason(val shouldSave: Boolean) {
    CANCEL(false),
    SEND(true),
    TIMEOUT(true),
    ERROR(false),
}
