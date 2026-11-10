package ru.bruce.dispatcherinbox

import android.content.Intent
import android.net.Uri
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

class LocalCommitUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as DispatcherApplication).container
    private val device = UiDevice.getInstance(instrumentation)
    private val assistant get() = Intent(context, AssistantEntryActivity::class.java).setAction(Intent.ACTION_ASSIST)

    private suspend fun prepare() {
        container.settingsRepository.setAutoSync(false)
        container.settingsRepository.setDriveAccount("")
        container.settingsRepository.setAssistantOpenMode("voice")
        container.settingsRepository.setAutoStartAssistantRecording(false)
        container.settingsRepository.setCloseAfterSave(false)
    }

    private fun awaitPanel() = assertTrue(device.wait(Until.hasObject(By.desc("Закрыть ассистент")), 10000))
    private fun awaitClosed(scenario: ActivityScenario<AssistantEntryActivity>) {
        assertTrue("Панель осталась после локального commit", device.wait(Until.gone(By.desc("Закрыть ассистент")), 10000))
        instrumentation.waitForIdleSync()
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }
    private suspend fun awaitSaved(marker: String) = withTimeout(10000) {
        container.dao.observeAll().first { list -> list.any { it.item.text == marker } }.single { it.item.text == marker }
    }
    private fun proof(name: String) {
        val dir = File(context.getExternalFilesDir(null), "catching-evidence").apply { mkdirs() }
        device.waitForIdle()
        assertTrue(device.takeScreenshot(File(dir, "ui-regression-$name.png")))
        device.dumpWindowHierarchy(File(dir, "ui-regression-$name.xml"))
    }

    @Test fun fileFailureRetainsDraftAndRetryCommitsOnceThenReturnsToPreviousContext() = runBlocking<Unit> {
        prepare()
        // Настоящий внешний контекст, а не фоновая Activity самого Catching.
        context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")), 10000))
        ActivityScenario.launch<AssistantEntryActivity>(assistant).use { scenario ->
            awaitPanel()
            lateinit var model: CaptureViewModel
            scenario.onActivity { model = ViewModelProvider(it)[CaptureViewModel::class.java] }
            val marker = "local-file-${System.nanoTime()}"
            scenario.onActivity {
                model.setText(marker)
                model.addAttachment(Uri.parse("content://ru.bruce.dispatcherinbox.test.originals/original"))
            }
            val draft = withTimeout(10000) { model.state.first { !it.isBusy && it.attachments.size == 1 }.attachments.single() }
            val original = File(draft.localPath).readBytes()
            assertTrue(File(draft.localPath).delete()) // Только тестовый staging, имитация реального сбоя копирования.
            assertTrue(device.wait(Until.hasObject(By.text("Сохранить")), 10000))
            device.findObject(By.text("Сохранить")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Локальный файл больше недоступен")), 10000))
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            assertEquals(marker, model.state.value.text)
            assertEquals(draft, model.state.value.attachments.single())
            assertTrue(container.dao.observeAll().first().none { it.item.text == marker })
            proof("assistant-save-failure")
            scenario.recreate()
            awaitPanel()
            assertEquals(marker, model.state.value.text)
            File(draft.localPath).writeBytes(original)
            // Два вызова в одном main-loop: busy выставляется до запуска coroutine.
            scenario.onActivity { model.save(); model.save() }
            val saved = awaitSaved(marker)
            awaitClosed(scenario)
            assertArrayEquals(original, File(saved.attachments.single().localPath).readBytes())
            assertEquals("queued", saved.item.status)
            assertEquals(1, container.dao.observeAll().first().count { it.item.text == marker })
            assertTrue("Не вернулись к предыдущему приложению", device.wait(Until.hasObject(By.pkg("com.android.settings")), 10000))
            proof("assistant-returned")
        }
    }

    @Test fun backgroundCommitReplaysAfterRecreationAndResume() = runBlocking<Unit> {
        prepare()
        ActivityScenario.launch<AssistantEntryActivity>(assistant).use { scenario ->
            awaitPanel()
            lateinit var model: CaptureViewModel
            scenario.onActivity { model = ViewModelProvider(it)[CaptureViewModel::class.java] }
            val marker = "local-background-${System.nanoTime()}"
            scenario.onActivity { model.setText(marker) }
            scenario.recreate()
            awaitPanel()
            scenario.moveToState(Lifecycle.State.CREATED)
            instrumentation.runOnMainSync { model.save(); model.save() }
            assertEquals("queued", awaitSaved(marker).item.status)
            assertEquals(Lifecycle.State.CREATED, scenario.state)
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitClosed(scenario)
            assertEquals(1, container.dao.observeAll().first().count { it.item.text == marker })
        }
    }

    @Test fun recordTabFromBothScreensBackBackgroundAndMainSaveRemainUsable() = runBlocking<Unit> {
        prepare()
        container.settingsRepository.setDefaultOpenMode("voice")
        container.settingsRepository.setCloseAfterSave(true) // Старая настройка не закрывает полноэкранное приложение.
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            assertTrue(device.wait(Until.hasObject(By.desc("Начать запись")), 10000))
            for (tab in listOf("Настройки", "Очередь")) {
                device.findObjects(By.text(tab)).last().click()
                val evidence = if (tab == "Настройки") "Версия и обновления" else "Последние локальные записи"
                assertTrue(device.wait(Until.hasObject(By.text(evidence)), 10000))
                proof(if (tab == "Настройки") "settings" else "queue")
                scenario.recreate()
                assertTrue(device.wait(Until.hasObject(By.text(evidence)), 10000))
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                assertTrue(device.wait(Until.hasObject(By.text(evidence)), 10000))
                device.findObject(By.text("Записать")).click()
                assertTrue("Не открыт экран записи из $tab", device.wait(Until.hasObject(By.desc("Начать запись")), 10000))
                device.findObject(By.text("Записать")).click()
                assertTrue(device.hasObject(By.desc("Начать запись")))
            }
            device.findObjects(By.text("Настройки")).last().click()
            assertTrue(device.wait(Until.hasObject(By.text("Версия и обновления")), 10000))
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.desc("Начать запись")), 10000))
            proof("main")
            device.findObject(By.text("Текст")).click()
            val input = device.wait(Until.findObject(By.pkg(context.packageName).clazz("android.widget.EditText").focused(true)), 10000)!!
            val marker = "main-stays-${System.nanoTime()}"
            input.text = marker
            device.pressBack()
            device.waitForIdle()
            val save = device.wait(Until.findObject(By.text("Сохранить")), 5000)
            if (save == null) device.swipe(device.displayWidth - 8, device.displayHeight * 3 / 4, device.displayWidth - 8, device.displayHeight / 3, 30)
            device.findObject(By.text("Сохранить")).click()
            awaitSaved(marker)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            assertTrue(device.hasObject(By.text("Записать")))
        }
        container.settingsRepository.setCloseAfterSave(false)
        ActivityScenario.launch<AssistantEntryActivity>(assistant).use {
            awaitPanel()
            proof("assistant")
        }
    }
}
