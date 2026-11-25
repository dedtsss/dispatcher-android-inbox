package ru.bruce.dispatcherinbox.sync

import org.junit.Assert.*
import org.junit.Test
import ru.bruce.dispatcherinbox.data.*
import java.util.concurrent.TimeUnit

class OriginalRetentionPolicyTest {
    private val enabled = 1_000L
    private val confirmed = 2_000L
    private val item = InboxItemEntity("event", InboxType.MIXED, null, 1_500, confirmed, InboxStatus.SYNCED,
        InboxSource.ASSISTANT, 0, null, "remote", syncedAt = confirmed)
    private fun settings(policy: String) = AppSettings(localCopyPolicy = policy, cleanupEnabledAt = enabled)

    @Test fun `по умолчанию оригиналы остаются`() {
        assertFalse(OriginalRetentionPolicy.eligible(item, AppSettings(), Long.MAX_VALUE))
        assertFalse(OriginalRetentionPolicy.eligible(item, settings("unknown"), Long.MAX_VALUE))
    }
    @Test fun `возраст не удаляет неотправленное ошибочное и частичное событие`() {
        for (status in listOf(InboxStatus.QUEUED, InboxStatus.ERROR, InboxStatus.SYNCING, InboxStatus.DRAFT)) {
            assertFalse(status, OriginalRetentionPolicy.eligible(item.copy(status = status), settings("after_upload"), Long.MAX_VALUE))
        }
        assertFalse(OriginalRetentionPolicy.eligible(item.copy(syncedAt = null), settings("after_upload"), Long.MAX_VALUE))
        assertFalse(OriginalRetentionPolicy.eligible(item.copy(remoteId = null), settings("after_upload"), Long.MAX_VALUE))
    }
    @Test fun `включение не чистит существующие и исторические записи`() {
        assertFalse(OriginalRetentionPolicy.eligible(item.copy(createdAt = enabled - 1), settings("after_upload"), Long.MAX_VALUE))
        assertFalse(OriginalRetentionPolicy.eligible(item.copy(syncedAt = enabled - 1), settings("after_upload"), Long.MAX_VALUE))
        assertFalse(OriginalRetentionPolicy.eligible(item, settings("after_upload").copy(cleanupEnabledAt = 0), Long.MAX_VALUE))
    }
    @Test fun `семь дней отсчитываются от полного подтверждения а не создания`() {
        val deadline = confirmed + TimeUnit.DAYS.toMillis(7)
        assertFalse(OriginalRetentionPolicy.eligible(item, settings("after_7_days"), deadline - 1))
        assertTrue(OriginalRetentionPolicy.eligible(item, settings("after_7_days"), deadline))
        assertFalse(OriginalRetentionPolicy.eligible(item.copy(originalsDeletedAt = deadline), settings("after_7_days"), deadline))
        assertTrue(OriginalRetentionPolicy.eligible(item, settings("after_upload"), confirmed))
    }
}
