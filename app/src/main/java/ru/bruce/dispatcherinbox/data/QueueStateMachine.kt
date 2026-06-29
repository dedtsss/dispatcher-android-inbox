package ru.bruce.dispatcherinbox.data

object QueueStateMachine {
    fun onSyncStarted(status: String): String {
        require(status == InboxStatus.QUEUED || status == InboxStatus.ERROR)
        return InboxStatus.SYNCING
    }

    fun onSyncSucceeded(status: String): String {
        require(status == InboxStatus.SYNCING)
        return InboxStatus.SYNCED
    }

    fun onSyncFailed(status: String): String {
        require(status == InboxStatus.SYNCING)
        return InboxStatus.ERROR
    }

    fun onRetry(status: String): String {
        require(status == InboxStatus.ERROR)
        return InboxStatus.QUEUED
    }
}
