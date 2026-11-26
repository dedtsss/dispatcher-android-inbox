package ru.bruce.dispatcherinbox

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import ru.bruce.dispatcherinbox.assistant.AssistantEntryActivity
import java.io.File

class AssistantSurfaceUiTest {
    @Test fun compactSurfaceOnLightAndDarkHostAndWithKeyboardOnSmallScreen() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val settings = (context.applicationContext as DispatcherApplication).container.settingsRepository
        val previous = settings.settings.first()
        val device = UiDevice.getInstance(instrumentation)
        settings.setAssistantOpenMode("voice")
        settings.setAutoStartAssistantRecording(false)
        settings.setDriveAccount("")
        val output = File(context.getExternalFilesDir(null), "catching-evidence").apply { mkdirs() }
        val intent = Intent(context, AssistantEntryActivity::class.java).setAction(Intent.ACTION_ASSIST)
        val measurements = mutableListOf<String>()
        try {
            device.executeShellCommand("wm size 720x1280")
            for (night in listOf("no", "yes")) {
                device.executeShellCommand("cmd uimode night $night")
                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")), 10000))
                ActivityScenario.launch<AssistantEntryActivity>(intent).use {
                    assertTrue(device.wait(Until.hasObject(By.desc("Закрыть ассистент")), 10000))
                    device.waitForIdle()
                    val close = device.findObject(By.desc("Закрыть ассистент"))
                    assertTrue("Кнопка выхода у края экрана: ${close.visibleBounds}", close.visibleBounds.top >= 0)
                    val topBeforeKeyboard = close.visibleBounds.top
                    assertTrue("Кнопка записи скрыта", device.hasObject(By.desc("Начать запись")))
                    assertTrue(device.takeScreenshot(File(output, "assistant-host-$night.png")))
                    device.findObject(By.text("Текст")).click()
                    assertTrue(device.wait(Until.findObject(
                        By.pkg(context.packageName).clazz("android.widget.EditText").focused(true).enabled(true)), 5000) != null)
                    val stillVisible = device.findObject(By.desc("Закрыть ассистент"))
                    assertTrue("Панель потеряла верхний control при клавиатуре", stillVisible.visibleBounds.top >= 0)
                    val topWithKeyboard = stillVisible.visibleBounds.top
                    val maxShift = (120 * context.resources.displayMetrics.density).toInt()
                    assertTrue("Панель резко сместилась при появлении клавиатуры: $topBeforeKeyboard → $topWithKeyboard",
                        kotlin.math.abs(topWithKeyboard - topBeforeKeyboard) <= maxShift)
                    measurements += "night=$night size=720x1280 topBeforeIme=$topBeforeKeyboard topWithIme=$topWithKeyboard maxShiftPx=$maxShift"
                    assertTrue(device.takeScreenshot(File(output, "assistant-keyboard-$night.png")))
                    device.pressBack()
                }
            }
        } finally {
            device.executeShellCommand("wm size reset")
            device.executeShellCommand("cmd uimode night auto")
            settings.setAssistantOpenMode(previous.assistantOpenMode)
            settings.setAutoStartAssistantRecording(previous.autoStartAssistantRecording)
            settings.setDriveAccount(previous.driveAccount)
            File(output, "assistant-surface-measurements.txt").writeText(measurements.joinToString("\n", postfix = "\n"))
        }
    }
}
