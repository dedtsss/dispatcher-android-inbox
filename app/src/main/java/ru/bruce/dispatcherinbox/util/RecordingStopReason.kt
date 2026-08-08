package ru.bruce.dispatcherinbox.util

enum class RecordingStopReason(val shouldSave: Boolean) {
    CANCEL(false),
    SEND(true),
    ADD_TO_DRAFT(true),
    TIMEOUT(true),
    ERROR(false),
}
