package ru.bruce.dispatcherinbox

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import ru.bruce.dispatcherinbox.assistant.AssistantEntryActivity
import ru.bruce.dispatcherinbox.ui.capture.CaptureViewModel
import java.io.File

class RecordingLifecycleTest {
    @Test fun voiceAndOptionalFileSurviveRecreateScreenOffAndCommitOnceBeforeDelayedClose() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val container = (context.applicationContext as DispatcherApplication).container
        val device = UiDevice.getInstance(instrumentation)
        val dir = File(context.getExternalFilesDir(null), "catching-evidence").apply { mkdirs() }
        container.settingsRepository.setAutoSync(false)
        container.settingsRepository.setDriveAccount("")
        container.settingsRepository.setAssistantOpenMode("voice")
        container.settingsRepository.setAutoStartAssistantRecording(false)
        val marker = "lifecycle-${System.nanoTime()}"
        val intent = Intent(context, AssistantEntryActivity::class.java).setAction(Intent.ACTION_ASSIST)
        val closedAt = java.util.concurrent.atomic.AtomicLong(0)
        lateinit var model: CaptureViewModel
        ActivityScenario.launch<AssistantEntryActivity>(intent).use { scenario ->
            assertTrue(device.wait(Until.hasObject(By.desc("Начать запись")), 10000))
            scenario.onActivity { model = ViewModelProvider(it)[CaptureViewModel::class.java] }
            device.waitForIdle()
            assertTrue(device.takeScreenshot(File(dir, "lifecycle-idle.png")))
            val idleTop = device.findObject(By.desc("Закрыть ассистент")).visibleBounds.top
            scenario.onActivity { model.startRecording() }
            val recordingWindowStart = SystemClock.elapsedRealtime()
            assertTrue(device.wait(Until.hasObject(By.desc("Остановить и сохранить")), 10000))
            device.waitForIdle()
            val recordingTop = device.findObject(By.desc("Закрыть ассистент")).visibleBounds.top
            assertTrue("Высота панели изменилась при старте записи", kotlin.math.abs(idleTop - recordingTop) <= 4)
            assertTrue("Файл недоступен во время записи", device.findObject(By.text("Файл")).isEnabled)
            assertTrue(device.takeScreenshot(File(dir, "lifecycle-recording.png")))
            withTimeout(10000) {
                while (context.getSystemService(NotificationManager::class.java).activeNotifications.none {
                    it.notification.extras.getString("android.title") == "Catching · Идёт запись"
                }) kotlinx.coroutines.delay(20)
            }
            scenario.recreate()
            assertTrue(device.wait(Until.hasObject(By.desc("Остановить и сохранить")), 10000))
            scenario.onActivity { model = ViewModelProvider(it)[CaptureViewModel::class.java] }
            scenario.onActivity { activity ->
                activity.lifecycle.addObserver(androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_DESTROY) closedAt.set(SystemClock.elapsedRealtime())
                })
            }
            val secondsBefore = model.state.value.recordingSeconds
            scenario.moveToState(Lifecycle.State.CREATED)
            try {
                device.sleep()
                Thread.sleep(1500)
            } finally {
                device.wakeUp()
                device.executeShellCommand("wm dismiss-keyguard")
                scenario.moveToState(Lifecycle.State.RESUMED)
            }
            withTimeout(10000) { model.state.first { it.isRecording && it.recordingSeconds > secondsBefore } }
            assertTrue(device.wait(Until.hasObject(By.desc("Остановить и сохранить")), 10000))
            assertTrue("Во время записи нельзя открыть ввод текста", device.findObject(By.text("Текст")).isEnabled)
            device.findObject(By.text("Текст")).click()
            val textInput = checkNotNull(device.wait(Until.findObject(
                By.pkg(context.packageName).clazz("android.widget.EditText").focused(true).enabled(true)), 5000))
            textInput.text = marker
            assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName).text(marker)), 5000))
            assertTrue(device.takeScreenshot(File(dir, "lifecycle-recording-with-keyboard.png")))
            val secondsBeforeKeyboard = model.state.value.recordingSeconds
            Thread.sleep(1200)
            device.pressBack()
            withTimeout(5000) { model.state.first { it.isRecording && it.text == marker && it.recordingSeconds > secondsBeforeKeyboard } }
            // Настоящий ContentProvider и копирование байтов при работающем MediaRecorder.
            scenario.onActivity { model.addAttachment(Uri.parse("content://ru.bruce.dispatcherinbox.test.originals/original")) }
            withTimeout(10000) { model.state.first { it.isRecording && !it.isBusy && it.attachments.size == 1 } }
            assertTrue(device.takeScreenshot(File(dir, "lifecycle-recording-with-file.png")))
            val recordingWindowStop = SystemClock.elapsedRealtime()
            scenario.onActivity { model.stopRecordingAndSave(); model.stopRecordingAndSave() }
            if (device.wait(Until.hasObject(By.desc("Сохраняем запись")), 500)) {
                assertTrue(device.takeScreenshot(File(dir, "lifecycle-saving.png")))
            }
            withTimeout(10000) { model.state.first { it.localCommitted } }
            val commitObserved = SystemClock.elapsedRealtime()
            assertTrue("Кнопка не показала подтверждающую галочку", device.hasObject(By.descContains("Сохранено на телефоне")))
            assertTrue(device.takeScreenshot(File(dir, "lifecycle-local-commit.png")))
            val exitFrames = mutableListOf<Pair<Long, Int>>()
            val exitStarted = SystemClock.elapsedRealtime()
            var frame = 0
            while (SystemClock.elapsedRealtime() - exitStarted < 3000 && scenario.state != Lifecycle.State.DESTROYED) {
                val node = device.findObject(By.desc("Закрыть ассистент"))
                if (node != null) {
                    exitFrames += (SystemClock.elapsedRealtime() - exitStarted) to node.visibleBounds.top
                    if (frame < 5) assertTrue(device.takeScreenshot(File(dir, "lifecycle-exit-$frame.png")))
                    frame++
                }
                Thread.sleep(40)
            }
            val saved = withTimeout(10000) {
                container.dao.observeAll().first { entries -> entries.any { it.item.text == marker } }.single { it.item.text == marker }
            }
            assertEquals(2, saved.attachments.size)
            assertEquals(1, saved.attachments.count { it.mimeType == "audio/mp4" })
            assertTrue(saved.attachments.single { it.mimeType == "audio/mp4" }.sizeBytes > 0)
            val original = saved.attachments.single { it.mimeType != "audio/mp4" }
            assertArrayEquals(byteArrayOf(0, 1, -1, 10, 42), File(original.localPath).readBytes())
            assertEquals(saved.attachments.sumOf { it.sizeBytes } + (saved.item.text?.toByteArray(Charsets.UTF_8)?.size ?: 0), model.state.value.savedOriginalBytes)
            assertEquals("queued", saved.item.status)
            assertTrue("LOCAL commit выдан за Drive", model.state.value.statusMessage == "Сохранено на телефоне")
            assertTrue(device.wait(Until.gone(By.desc("Закрыть ассистент")), 10000))
            instrumentation.waitForIdleSync()
            val closeDelay = closedAt.get() - commitObserved
            assertTrue("Подтверждение не удерживалось примерно секунду: ${closeDelay}мс", closeDelay in 900..3000)
            val movingFrames = exitFrames.filter { it.first > 700 }
            assertTrue("Не измерено закрытие панели: $exitFrames", movingFrames.size >= 2)
            assertTrue("Панель не уходила вниз монотонно: $movingFrames", movingFrames.zipWithNext().all { (a, b) -> b.second >= a.second })
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
            assertEquals(1, container.dao.observeAll().first().count { it.item.text == marker })
            val voice = saved.attachments.single { it.mimeType == "audio/mp4" }
            val retriever = android.media.MediaMetadataRetriever()
            val recordedMillis = try {
                retriever.setDataSource(voice.localPath)
                checkNotNull(retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong()
            } finally { retriever.release() }
            val wallWindowMillis = recordingWindowStop - recordingWindowStart
            assertTrue("M4A не содержит полный интервал записи, включая выключенный экран: audio=$recordedMillis wall=$wallWindowMillis",
                kotlin.math.abs(recordedMillis - wallWindowMillis) <= 700)
            File(dir, "lifecycle-measurements.txt").writeText(
                "Источник: Android AVD, не физический телефон и не защищённая PIN-блокировка.\n" +
                "idleHeaderTopPx=$idleTop recordingHeaderTopPx=$recordingTop\n" +
                "Образцы кадров выхода (ms:topPx): ${exitFrames.joinToString()}\n" +
                "Экран выключен 1500мс; запись продолжилась после recreate/background.\n" +
                "m4aDurationMs=$recordedMillis wallRecordingWindowMs=$wallWindowMillis toleranceMs=700\n" +
                "localCommitObservedToClosedMs=$closeDelay originals=${saved.attachments.size} originalBytes=${saved.attachments.sumOf { it.sizeBytes }}\n" +
                "Один LOCAL commit, status=queued; загрузка в настоящий Drive не проверялась.\n")
        }
    }
}
