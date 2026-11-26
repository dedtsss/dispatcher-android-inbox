package ru.bruce.dispatcherinbox

import android.content.Context
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.bruce.dispatcherinbox.data.InboxStatus
import ru.bruce.dispatcherinbox.sync.SyncScheduler
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Два отдельных instrumentation-вызова; между ними CI перезагружает одноразовый AVD. */
class WorkRebootRecoveryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as DispatcherApplication).container
    private val prefs get() = context.getSharedPreferences("reboot-recovery-fixture", Context.MODE_PRIVATE)
    private val evidence get() = File(context.getExternalFilesDir(null), "catching-evidence").apply { mkdirs() }

    private fun awaitOffline() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("svc wifi disable")
        device.executeShellCommand("svc data disable")
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (connectivity.activeNetwork != null && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(250)
        }
        assertNull("Сценарий должен начаться без сети", connectivity.activeNetwork)
    }

    @Test fun seedBeforeReboot() = runBlocking<Unit> {
        // Другой instrumentation-тест мог включить Wi-Fi; гарантируем реальный офлайн
        // непосредственно перед созданием записи и automatic WorkManager work.
        awaitOffline()
        container.settingsRepository.setDriveAccount("")
        container.settingsRepository.setAutoSync(true)
        container.settingsRepository.setWifiOnly(false)
        val original = container.inboxRepository.stage(android.net.Uri.parse("content://ru.bruce.dispatcherinbox.test.originals/original"))
        val item = container.inboxRepository.save("reboot-${UUID.randomUUID()}", listOf(original))
        // Только автоматический production scheduler; ручной scheduleManual не вызывается.
        SyncScheduler(context, container.settingsRepository).scheduleAutomatic(coalesce = true)
        val manager = WorkManager.getInstance(context)
        val pending = manager.getWorkInfosForUniqueWork(SyncScheduler.UNIQUE_WORK_NAME).get()
            .firstOrNull { it.state == WorkInfo.State.ENQUEUED }
        assertNotNull("Нет сохранённой automatic work, ожидающей сеть", pending)
        val saved = checkNotNull(container.dao.getById(item.id))
        assertEquals(InboxStatus.QUEUED, saved.item.status)
        val bytes = File(saved.attachments.single().localPath).readBytes()
        assertArrayEquals(byteArrayOf(0, 1, -1, 10, 42), bytes)
        val bootCount = android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
        assertTrue(prefs.edit().putString("itemId", item.id).putString("workId", pending!!.id.toString())
            .putString("sha256", sha256(bytes)).putInt("bootCount", bootCount).commit())
        File(evidence, "reboot-seed.txt").writeText("Источник: production Room/WorkManager, offline AVD.\nitemId=${item.id}\nworkId=${pending.id}\nstate=${pending.state}\nbootCount=$bootCount\n")
    }

    @Test fun verifyAfterRebootWithoutManualSchedule() = runBlocking<Unit> {
        val itemId = checkNotNull(prefs.getString("itemId", null))
        val workId = UUID.fromString(checkNotNull(prefs.getString("workId", null)))
        val bootCount = android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
        assertTrue("AVD не был перезагружен между двумя вызовами", bootCount > prefs.getInt("bootCount", Int.MAX_VALUE))
        assertNull("Сеть должна оставаться выключенной после reboot", context.getSystemService(android.net.ConnectivityManager::class.java).activeNetwork)
        assertTrue(container.settingsRepository.settings.first().autoSync)
        assertEquals("", container.settingsRepository.settings.first().driveAccount)
        val saved = checkNotNull(container.dao.getById(itemId))
        assertEquals(InboxStatus.QUEUED, saved.item.status)
        assertEquals(0, saved.item.retryCount)
        assertNull(saved.item.lastAttemptAt)
        assertEquals(prefs.getString("sha256", null), sha256(File(saved.attachments.single().localPath).readBytes()))
        val work = WorkManager.getInstance(context).getWorkInfoById(workId).get()
        assertNotNull("Исходный persisted work ID потерян после reboot", work)
        assertEquals(WorkInfo.State.ENQUEUED, work!!.state)
        val repair = WorkManager.getInstance(context).getWorkInfosForUniqueWork(SyncScheduler.REPAIR_WORK_NAME).get()
        assertTrue("Периодический repair отсутствует после reboot", repair.any { !it.state.isFinished })
        File(evidence, "reboot-verify.txt").writeText("Источник: отдельный процесс после настоящего adb reboot AVD.\nitemId=$itemId\nworkId=$workId\nstate=${work.state}\nbootCount=$bootCount\nRoom queued, исходные байты и automatic Work ID сохранены. Ручная отправка не вызывалась.\n" +
            "Doze/разблокировка защищённого телефона и настоящий Google OAuth этим сценарием не подтверждаются.\n")
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
