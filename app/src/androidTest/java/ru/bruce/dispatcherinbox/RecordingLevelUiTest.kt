package ru.bruce.dispatcherinbox

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import ru.bruce.dispatcherinbox.ui.capture.CaptureUiState
import java.io.File

class RecordingLevelUiTest {
    @Test fun controlledPeaksChangeDrawnBarsAndStopRestartClearHistory() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val container = (context.applicationContext as DispatcherApplication).container
        container.settingsRepository.setAutoSync(false)
        container.settingsRepository.setDefaultOpenMode("voice")
        val device = UiDevice.getInstance(instrumentation)
        val dir = File(context.getExternalFilesDir(null), "catching-evidence").apply { mkdirs() }
        val journal = StringBuilder("Источник: управляемые искусственные пики debug LevelMeterTestActivity. Не реальный голос/физическая чувствительность.\nПолноэкранный Canvas: ширина 82% содержимого, высота 40dp на малой высоте / 64dp в обычном режиме; 32 столбца; высота = max(1 dp, level×высотаCanvas×0.92 dp).\n")
        val before = container.dao.observeAll().first().map { it.item.id }.toSet()
        ActivityScenario.launch<LevelMeterTestActivity>(Intent(context, LevelMeterTestActivity::class.java)).use { scenario ->
            assertTrue(device.wait(Until.hasObject(By.desc("Начать запись")), 10000))
            lateinit var state: CaptureUiState
            suspend fun phase(raw: Int, label: String, screenshot: Boolean = true): Float {
                var begin = 0
                scenario.onActivity { begin = it.measuredPeaks.size; it.testPeak = raw }
                withTimeout(10000) {
                    var done = false
                    while (!done) {
                        scenario.onActivity { done = it.measuredPeaks.size >= begin + 8; state = it.model.state.value }
                        if (!done) delay(20)
                    }
                }
                assertTrue(state.isRecording)
                assertEquals(32, state.recordingLevels.size)
                val bounds = device.findObject(By.desc("Уровень микрофона")).visibleBounds
                val density = context.resources.displayMetrics.density
                assertTrue("Индикатор слишком узкий", bounds.width() / density >= 200f)
                assertTrue("Неожиданная высота индикатора", listOf(40f, 64f).any { kotlin.math.abs(bounds.height() / density - it) < 1f })
                var visibleHeight = 0f
                if (screenshot) {
                    val image = File(dir, "meter-$label.png")
                    assertTrue(device.takeScreenshot(image))
                    val bitmap = BitmapFactory.decodeFile(image.path)
                    // Измеряем последний столбец: светлые пиксели, без полупрозрачной оси.
                    val x = (bounds.right - bounds.width() / 32f / 2).toInt()
                    visibleHeight = (bounds.top until bounds.bottom).count {
                        val pixel = bitmap.getPixel(x, it)
                        Color.red(pixel) > 110 && Color.green(pixel) > 110 && Color.blue(pixel) > 110
                    } / density
                    bitmap.recycle()
                    val expected = if (state.recordingAmplitude == 0f) 0f else maxOf(1f, state.recordingAmplitude * (bounds.height() / density) * 0.92f)
                    assertEquals("Высота столбца не следует измерению $label", expected, visibleHeight, 2.5f)
                }
                journal.append("$label: raw=$raw; level=${state.recordingAmplitude}; высотаПоследнегоСтолбцаDp=$visibleHeight; boundsPx=$bounds; history=${state.recordingLevels}\n")
                return visibleHeight
            }
            device.findObject(By.desc("Начать запись")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Остановить и сохранить")), 5000))
            assertEquals(0f, phase(0, "silence"), 0f)
            val low = phase(256, "low")
            val mid = phase(2048, "mid")
            val loud = phase(16000, "loud")
            assertTrue("Тихий сигнал не виден", low >= 2f)
            assertTrue("Геометрия не различает уровни", mid > low + 2f && loud > mid + 2f)
            assertEquals(0f, phase(0, "pause"), 0f)
            device.findObject(By.desc("Остановить и сохранить")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Начать запись").enabled(true)), 10000))
            scenario.onActivity { state = it.model.state.value }
            assertFalse(state.isRecording)
            assertEquals(0f, state.recordingAmplitude, 0f)
            assertTrue(state.recordingLevels.all { it == 0f })
            assertTrue(device.takeScreenshot(File(dir, "meter-stopped.png")))
            val saved = withTimeout(10000) {
                container.dao.observeAll().first { entries -> entries.any { it.item.id !in before } }.single { it.item.id !in before }
            }
            assertTrue(File(saved.attachments.single().localPath).length() > 0)
            assertEquals("queued", saved.item.status)
            // Новый настоящий recorder, но управляемый нулевой пик: старые полоски не возвращаются.
            device.findObject(By.desc("Начать запись")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Остановить и сохранить")), 5000))
            phase(0, "restart", screenshot = false)
            scenario.onActivity { state = it.model.state.value }
            assertTrue(state.recordingLevels.all { it == 0f })
            delay(600) // Вместе с восемью измерениями запись длится >1 секунды.
            device.findObject(By.desc("Остановить и сохранить")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Начать запись").enabled(true)), 10000))
            scenario.onActivity { journal.append("Полная последовательность искусственных raw: ${it.measuredPeaks}\n") }
            journal.append("Остановка/повторный старт: history=0, реальный AAC/M4A сохранён локально; это не проверка чувствительности физического микрофона.\n")
        }
        File(dir, "meter-measurements.txt").writeText(journal.toString())
    }
}
