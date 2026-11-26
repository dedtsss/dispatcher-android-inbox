package ru.bruce.dispatcherinbox.capture

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ru.bruce.dispatcherinbox.R
import ru.bruce.dispatcherinbox.assistant.AssistantEntryActivity

/** Живёт только во время пользовательской диктовки и её локального сохранения. */
class RecordingService : Service() {
    private var sessionOwner: Any? = null
    private var sessionGeneration = -1L
    private var wakeLock: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?): IBinder? = null
    @android.annotation.SuppressLint("WakelockTimeout") // Освобождается при завершении foreground-сервиса, без ограничения длины диктовки.
    override fun onCreate() {
        super.onCreate()
        sessionOwner = owner
        sessionGeneration = generation
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Запись голоса", NotificationManager.IMPORTANCE_LOW))
        val activity = if (recordingSource == ru.bruce.dispatcherinbox.data.InboxSource.ASSISTANT) AssistantEntryActivity::class.java else ru.bruce.dispatcherinbox.MainActivity::class.java
        val open = PendingIntent.getActivity(this, 0, Intent(this, activity).setAction(Intent.ACTION_ASSIST).putExtra("destination", "capture"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, RecordingService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_quick_capture_tile).setContentTitle("Catching · Идёт запись")
            .setContentText("Запись продолжается при выключенном экране")
            .setContentIntent(open).setOngoing(true)
            .addAction(R.drawable.ic_quick_capture_tile, "Остановить и сохранить", stop).build()
        ServiceCompat.startForeground(this, 41, notification,
            if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Catching:recording").apply { acquire() }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) stopAction?.invoke()
        if (stopAction == null) stopSelf()
        return START_NOT_STICKY // Нельзя молча включать микрофон после смерти процесса.
    }
    override fun onTaskRemoved(rootIntent: Intent?) { if (owner === sessionOwner && generation == sessionGeneration) stopAction?.invoke() }
    override fun onDestroy() {
        if (owner === sessionOwner && generation == sessionGeneration) stopAction?.invoke()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "catching-recording"
        private const val STOP = "ru.bruce.dispatcherinbox.STOP_RECORDING"
        private var recordingSource = ru.bruce.dispatcherinbox.data.InboxSource.MANUAL
        private var generation = 0L
        private var owner: Any? = null
        private var stopAction: (() -> Unit)? = null
        @Synchronized fun begin(context: android.content.Context, token: Any, source: String, onStop: () -> Unit) {
            check(owner == null || owner === token) { "Другая запись уже идёт. Откройте её через уведомление" }
            generation++
            owner = token
            recordingSource = source
            stopAction = onStop
            try { ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java)) }
            catch (error: Exception) { owner = null; stopAction = null; throw error }
        }
        @Synchronized fun saving(context: android.content.Context, token: Any, waitingForPicker: Boolean) {
            if (owner !== token) return
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
            val activity = if (recordingSource == ru.bruce.dispatcherinbox.data.InboxSource.ASSISTANT) AssistantEntryActivity::class.java else ru.bruce.dispatcherinbox.MainActivity::class.java
            val open = PendingIntent.getActivity(context, 0, Intent(context, activity).setAction(Intent.ACTION_ASSIST).putExtra("destination", "capture"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_quick_capture_tile)
                .setContentTitle("Catching · Микрофон остановлен")
                .setContentText(if (waitingForPicker) "Завершите выбор файла для этой заметки" else "Сохраняем на телефоне…")
                .setContentIntent(open).setOngoing(true).build()
            // Разрешение уведомлений может быть отозвано; это не отменяет LOCAL commit.
            runCatching { context.getSystemService(NotificationManager::class.java).notify(41, notification) }
        }
        @Synchronized fun end(context: android.content.Context, token: Any) {
            if (owner !== token) return
            owner = null
            stopAction = null
            context.stopService(Intent(context, RecordingService::class.java))
        }
    }
}
