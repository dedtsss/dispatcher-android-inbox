package ru.bruce.dispatcherinbox

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class TabTransitionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun ordinaryTabsAndBackCompleteWithoutFade() {
        val capture = "Сначала на телефоне · затем в Google Drive"
        val queue = "Последние локальные записи"
        val settings = "Версия и обновления"
        compose.onNodeWithText(capture).assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        val journal = StringBuilder("Источник: Compose test clock, не wall-clock производительность телефона.\n")
        fun settled(target: String, outgoing: String) {
            var frames = 0
            do {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                frames++
            } while (frames < 6 && compose.onAllNodesWithText(outgoing).fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithText(target).assertIsDisplayed()
            compose.onNodeWithText(outgoing).assertDoesNotExist()
            journal.append("$outgoing → $target: $frames кадров, не более 96 мс тестового времени\n")
        }
        repeat(3) {
            compose.onNodeWithText("Настройки").performClick()
            settled(settings, capture)
            compose.onNodeWithText("Очередь").performClick()
            settled(queue, settings)
            compose.onNodeWithText("Записать").performClick()
            settled(capture, queue)
        }
        compose.onNodeWithText("Настройки").performClick()
        settled(settings, capture)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settled(capture, settings)
        compose.onNodeWithText("Записать").performClick()
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithText(capture).assertIsDisplayed()
        journal.append("Повтор Capture: тот же экран; 2 кадра тестовых часов\n")
        val context = compose.activity
        val dir = java.io.File(context.getExternalFilesDir(null), "catching-evidence").apply { mkdirs() }
        java.io.File(dir, "meter-navigation.txt").writeText(journal.toString())
        compose.mainClock.autoAdvance = true
    }
}
