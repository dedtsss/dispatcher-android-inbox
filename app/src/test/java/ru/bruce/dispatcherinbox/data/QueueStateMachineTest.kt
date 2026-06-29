package ru.bruce.dispatcherinbox.data

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueStateMachineTest {
    @Test
    fun `successful sync follows queue states`() {
        val syncing = QueueStateMachine.onSyncStarted(InboxStatus.QUEUED)
        val synced = QueueStateMachine.onSyncSucceeded(syncing)

        assertEquals(InboxStatus.SYNCING, syncing)
        assertEquals(InboxStatus.SYNCED, synced)
    }

    @Test
    fun `failed item can return to queue`() {
        val error = QueueStateMachine.onSyncFailed(InboxStatus.SYNCING)
        val queued = QueueStateMachine.onRetry(error)

        assertEquals(InboxStatus.ERROR, error)
        assertEquals(InboxStatus.QUEUED, queued)
    }
}
