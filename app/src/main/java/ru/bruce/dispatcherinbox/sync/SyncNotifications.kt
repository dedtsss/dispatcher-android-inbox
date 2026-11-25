package ru.bruce.dispatcherinbox.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ru.bruce.dispatcherinbox.MainActivity
import ru.bruce.dispatcherinbox.R
import ru.bruce.dispatcherinbox.data.InboxItemWithAttachments
import java.util.concurrent.TimeUnit

class SyncNotifications(private val context: Context) {
    fun update(pending: List<InboxItemWithAttachments>, now: Long = System.currentTimeMillis()) {
        val manager = NotificationManagerCompat.from(context)
        val stuck = pending.any { it.item.retryCount >= 3 || now - it.item.createdAt >= TimeUnit.HOURS.toMillis(6) }
        if (!stuck) { manager.cancel(ID); return }
        if (!manager.areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Состояние отправки Catching", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val intent = Intent(context, MainActivity::class.java).putExtra("destination", "queue")
        val open = PendingIntent.getActivity(context, ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // Не раскрываем текст, имена файлов и аккаунт на заблокированном экране.
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_quick_capture_tile)
            .setContentTitle("Catching: записи ожидают отправки")
            .setContentText("Оригиналы сохранены на телефоне. Откройте очередь для проверки")
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        try { manager.notify(ID, notification) } catch (_: SecurityException) { /* Разрешение отозвано пользователем. */ }
    }
    private companion object { const val CHANNEL = "catching-sync-status"; const val ID = 212 }
}
