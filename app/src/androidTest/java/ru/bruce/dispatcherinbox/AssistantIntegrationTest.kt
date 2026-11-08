package ru.bruce.dispatcherinbox

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
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
    @Test fun logoPreservesOriginalAlphaCatAndAccent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = android.graphics.BitmapFactory.decodeResource(context.resources, ru.bruce.dispatcherinbox.R.drawable.catching_logo)
        assertEquals(890, original.width)
        assertEquals(210, original.height)
        val source = androidx.compose.ui.graphics.painter.BitmapPainter(original.asImageBitmap())
        fun render(painter: androidx.compose.ui.graphics.painter.Painter): android.graphics.Bitmap {
            val image = androidx.compose.ui.graphics.ImageBitmap(890, 210)
            androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
                androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(image), androidx.compose.ui.geometry.Size(890f, 210f),
            ) { with(painter) { draw(size) } }
            return image.asAndroidBitmap()
        }
        val reference = render(source)
        val result = render(ru.bruce.dispatcherinbox.ui.capture.CatchingLogoPainter(source, androidx.compose.ui.graphics.Color(0xFFF1EDF6)))
        var transparent = 0
        var letters = 0
        for (y in 0 until 210) for (x in 0 until 890) {
            val before = reference.getPixel(x, y)
            val after = result.getPixel(x, y)
            if (android.graphics.Color.alpha(before) != android.graphics.Color.alpha(after)) fail("Изменена прозрачность $x,$y")
            if (android.graphics.Color.alpha(before) == 0) transparent++
            if (x < 250 || (x in 680 until 720 && y in 20 until 62)) {
                if (before != after) fail("Изменён кот или фиолетовый акцент $x,$y")
            } else if (android.graphics.Color.alpha(before) == 255) {
                if (after != 0xFFF1EDF6.toInt()) fail("Буква не получила светлый цвет $x,$y")
                letters++
            }
        }
        assertTrue("Утрачена прозрачная область", transparent > 100000)
        assertTrue("Не проверены буквы", letters > 10000)
    }

    @Test fun compactOriginalLogoOnBothSurfaces() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val settings = (context.applicationContext as DispatcherApplication).container.settingsRepository
        val device = UiDevice.getInstance(instrumentation)
        settings.setAssistantOpenMode("voice")
        settings.setAutoStartAssistantRecording(false)
        val dir = File(context.getExternalFilesDir(null), "catching-evidence").apply { mkdirs() }
        fun verify(name: String, compact: Boolean) {
            assertTrue(device.wait(Until.hasObject(By.desc("Catching")), 10000))
            device.waitForIdle()
            assertTrue(device.takeScreenshot(File(dir, "$name.png")))
            device.dumpWindowHierarchy(File(dir, "$name.xml"))
            val logo = device.findObject(By.desc("Catching")).visibleBounds
            val density = context.resources.displayMetrics.density
            val screenshot = android.graphics.BitmapFactory.decodeFile(File(dir, "$name.png").path)
            val corner = screenshot.getPixel(logo.left + (4 * density).toInt(), logo.top + (4 * density).toInt())
            assertTrue("Вокруг логотипа появилась светлая плашка", android.graphics.Color.red(corner) < 60 && android.graphics.Color.green(corner) < 60 && android.graphics.Color.blue(corner) < 60)
            screenshot.recycle()
            assertTrue("Логотип превышает ограничение ширины: $logo", logo.width() <= (if (compact) 184 else 208) * density + 2)
            // В панели clickable объединяет всю область; у Image главного экрана bounds исключают padding 8dp.
            val expectedHeight = (if (compact) 48 else 40) * density
            assertTrue("Неверная высота области логотипа: $logo", kotlin.math.abs(logo.height() - expectedHeight) <= 2)
            if (compact) {
                for (label in listOf("Закрыть ассистент", "Настройки ассистента")) {
                    val icon = device.findObject(By.desc(label))
                    val target = if (icon.isClickable) icon else icon.parent
                    assertTrue("Не найдена кнопка для $label", target.isClickable)
                    val button = target.visibleBounds
                    assertFalse("Логотип перекрывает $label", android.graphics.Rect.intersects(logo, button))
                    assertTrue("Малая область нажатия $label: $button", button.width() >= 44 * density && button.height() >= 44 * density)
                }
            }
        }
        try {
            for (narrow in listOf(false, true)) {
                if (narrow) device.executeShellCommand("wm size 864x1920")
                val suffix = if (narrow) "narrow" else "normal"
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
                    verify("logo-main-$suffix", false)
                }
                ActivityScenario.launch<AssistantEntryActivity>(Intent(context, AssistantEntryActivity::class.java)
                    .setAction(Intent.ACTION_ASSIST)).use {
                    assertTrue(device.wait(Until.hasObject(By.desc("Закрыть ассистент")), 10000))
                    verify("logo-assistant-$suffix", true)
                }
            }
        } finally {
            device.executeShellCommand("wm size reset")
        }
    }

    @Test fun launcherWaitsAndOverlayControlsNavigate() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val settings = (context.applicationContext as DispatcherApplication).container.settingsRepository
        val device = UiDevice.getInstance(instrumentation)
        settings.setDefaultOpenMode("voice")
        settings.setAssistantOpenMode("voice")
        settings.setAutoStartAssistantRecording(true)
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)).use {
            assertTrue(device.wait(Until.hasObject(By.desc("Начать запись")), 10000))
            Thread.sleep(1200)
            assertFalse(device.hasObject(By.desc("Остановить и сохранить")))
        }
        settings.setAssistantOpenMode("text")
        val intent = Intent(context, AssistantEntryActivity::class.java).setAction(Intent.ACTION_ASSIST)
        ActivityScenario.launch<AssistantEntryActivity>(intent).use { scenario ->
            assertTrue(device.wait(Until.hasObject(By.desc("Закрыть ассистент")), 10000))
            device.findObject(By.desc("Закрыть ассистент")).click()
            assertTrue(device.wait(Until.gone(By.desc("Закрыть ассистент")), 5000))
            instrumentation.waitForIdleSync()
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.state)
        }
        for ((control, target) in listOf("Настройки ассистента" to "Версия и обновления", "Catching" to "Сначала на телефоне · затем в Google Drive")) {
            ActivityScenario.launch<AssistantEntryActivity>(intent).use {
                assertTrue("Панель ассистента не открылась", device.wait(Until.hasObject(By.desc("Закрыть ассистент")), 10000))
                if (control == "Catching" && device.hasObject(By.clazz("android.widget.EditText").focused(true))) {
                    device.pressBack() // Скрываем клавиатуру перед проверкой заголовка.
                }
                assertTrue("Недоступно управление: $control", device.wait(Until.hasObject(By.desc(control)), 10000))
                device.findObject(By.desc(control)).click()
                assertTrue(device.wait(Until.hasObject(By.text(target)), 10000))
                assertFalse(device.hasObject(By.desc("Закрыть ассистент")))
                assertFalse(device.hasObject(By.desc("Остановить и сохранить")))
            }
        }
        device.findObject(By.text("Настройки")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Версия и обновления")), 10000))
        ActivityScenario.launch<AssistantEntryActivity>(intent).use {
            assertTrue(device.wait(Until.hasObject(By.desc("Catching")), 10000))
            device.findObject(By.desc("Catching")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Сначала на телефоне · затем в Google Drive")), 10000))
        }
        device.pressBack()
    }

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
