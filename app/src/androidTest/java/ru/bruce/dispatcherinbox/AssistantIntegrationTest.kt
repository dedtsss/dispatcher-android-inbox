package ru.bruce.dispatcherinbox

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.bruce.dispatcherinbox.assistant.AssistantEntryActivity
import java.io.File

class AssistantIntegrationTest {
    @Test fun assistantSettingsControlTextWaitingAndRecording() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val settings = (context.applicationContext as DispatcherApplication).container.settingsRepository
        val device = UiDevice.getInstance(instrumentation)
        settings.setCloseAfterSave(false)
        fun screenshot(name: String) {
            val dir = File(context.getExternalFilesDir(null), "catching-evidence").apply { mkdirs() }
            device.waitForIdle()
            assertTrue(device.takeScreenshot(File(dir, "$name.png")))
        }
        val intent = Intent(context, AssistantEntryActivity::class.java).setAction(Intent.ACTION_ASSIST)
        settings.setAssistantOpenMode("text")
        settings.setAutoStartAssistantRecording(true)
        ActivityScenario.launch<AssistantEntryActivity>(intent).use {
            assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText").focused(true)), 10000))
            assertFalse(device.hasObject(By.desc("Остановить и сохранить")))
            assertTrue(device.hasObject(By.text("Сохранить")))
            screenshot("04-assistant-text-focused")
            val marker = "assistant-text-${System.nanoTime()}"
            device.findObject(By.clazz("android.widget.EditText")).text = marker
            device.waitForIdle()
            device.findObject(By.text("Сохранить")).click()
            val dao = (context.applicationContext as DispatcherApplication).container.dao
            kotlinx.coroutines.withTimeout(10000) {
                while (dao.getPending().none { entry -> entry.item.text == marker && entry.item.source == "assistant" }) kotlinx.coroutines.delay(100)
            }
        }
        settings.setAssistantOpenMode("voice")
        settings.setAutoStartAssistantRecording(false)
        ActivityScenario.launch<AssistantEntryActivity>(intent).use {
            assertTrue(device.wait(Until.hasObject(By.desc("Начать запись")), 10000))
            Thread.sleep(1200)
            assertFalse(device.hasObject(By.desc("Остановить и сохранить")))
            screenshot("05-assistant-voice-waiting")
            device.findObject(By.desc("Начать запись")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Остановить и сохранить")), 5000))
            Thread.sleep(1500)
            assertTrue(device.wait(Until.hasObject(By.text(java.util.regex.Pattern.compile("(?!00:00)\\d{2}:\\d{2}"))), 5000))
            screenshot("06-assistant-manual-recording")
            device.findObject(By.desc("Остановить и сохранить")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Начать запись").enabled(true)), 10000))
        }
        settings.setAutoStartAssistantRecording(true)
        ActivityScenario.launch<AssistantEntryActivity>(intent).use {
            assertTrue(device.wait(Until.hasObject(By.desc("Остановить и сохранить")), 10000))
            Thread.sleep(1500)
            screenshot("07-assistant-auto-recording")
            device.findObject(By.desc("Остановить и сохранить")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Начать запись").enabled(true)), 10000))
        }
    }
}
