package ru.bruce.dispatcherinbox

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class TabTransitionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun ordinaryTabsAndBackCompleteWithinTwoFramesWithoutFade() {
        val capture = "Сначала на телефоне · затем в Google Drive"
        val queue = "Последние локальные записи"
        val settings = "Версия и обновления"
        compose.onNodeWithText(capture).assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        fun twoFrames() { repeat(2) { compose.mainClock.advanceTimeByFrame() }; compose.waitForIdle() }
        repeat(3) {
            compose.onNodeWithText("Настройки").performClick()
            twoFrames()
            compose.onNodeWithText(settings).assertIsDisplayed()
            compose.onNodeWithText(capture).assertDoesNotExist()
            compose.onNodeWithText("Очередь").performClick()
            twoFrames()
            compose.onNodeWithText(queue).assertIsDisplayed()
            compose.onNodeWithText(settings).assertDoesNotExist()
            compose.onNodeWithText("Записать").performClick()
            twoFrames()
            compose.onNodeWithText(capture).assertIsDisplayed()
            compose.onNodeWithText(queue).assertDoesNotExist()
        }
        compose.onNodeWithText("Настройки").performClick()
        twoFrames()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        twoFrames()
        compose.onNodeWithText(capture).assertIsDisplayed()
        compose.onNodeWithText(settings).assertDoesNotExist()
        compose.onNodeWithText("Записать").performClick()
        twoFrames()
        compose.onNodeWithText(capture).assertIsDisplayed()
        val context = compose.activity
        val dir = java.io.File(context.getExternalFilesDir(null), "catching-evidence").apply { mkdirs() }
        java.io.File(dir, "meter-navigation.txt").writeText("Источник: Compose test clock; 3 цикла Settings→Queue→Capture + Back и повтор Capture.\nПосле каждого действия — 2 кадра тестовых часов; исходящий экран отсутствует, целевой виден.\nЭто проверка отсутствия fade, не измерение миллисекунд на телефоне.\n")
        compose.mainClock.autoAdvance = true
    }
}
