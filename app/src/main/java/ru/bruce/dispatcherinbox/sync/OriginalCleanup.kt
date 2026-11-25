package ru.bruce.dispatcherinbox.sync

import ru.bruce.dispatcherinbox.data.*
import java.util.concurrent.TimeUnit

object OriginalRetentionPolicy {
    fun eligible(item: InboxItemEntity, settings: AppSettings, now: Long): Boolean {
        val confirmed = item.syncedAt ?: return false
        if (settings.localCopyPolicy == "keep" || settings.cleanupEnabledAt <= 0) return false
        // Смена настройки не запускает массовое удаление уже существующих записей.
        if (item.createdAt < settings.cleanupEnabledAt || confirmed < settings.cleanupEnabledAt) return false
        if (item.status != InboxStatus.SYNCED || item.remoteId.isNullOrBlank() || item.originalsDeletedAt != null) return false
        return when (settings.localCopyPolicy) {
            "after_upload" -> now >= confirmed
            "after_7_days" -> now >= confirmed && now - confirmed >= TimeUnit.DAYS.toMillis(7)
            else -> false
        }
    }
}

class OriginalCleanup(private val dao: InboxDao, private val store: AttachmentStore) {
    suspend fun run(settings: AppSettings, now: Long = System.currentTimeMillis()) = QueueDrainer.withQueueLock {
        if (settings.localCopyPolicy == "keep" || settings.driveAccount.isBlank()) return@withQueueLock
        dao.getConfirmedForCleanup().filter { OriginalRetentionPolicy.eligible(it.item, settings, now) }.forEach { entry ->
            val parts = entry.attachments.map { it.id } + if (entry.item.text != null) listOf("text") else emptyList()
            // Один reserved-id не означает успех группы. syncedAt записывается лишь
            // после подтверждения Gateway всех частей; проверяем также состав checkpoint.
            val complete = parts.isNotEmpty() && parts.all { part ->
                !dao.driveId("${settings.driveAccount}/item/${entry.item.id}/$part").isNullOrBlank()
            }
            if (complete && entry.attachments.isNotEmpty()) {
                store.deleteConfirmedOriginals(entry)
                dao.markOriginalsDeleted(entry.item.id, now)
            }
        }
    }
}
