package cn.nascp.clash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cn.nascp.clash.data.ClashRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SageThemeTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun retiredBackupMigratesToLight() {
        val repo = ClashRepository.get(ui.activity)
        runBlocking { repo.initialized() }
        val original = repo.backup()
        try {
            repo.language("zh"); repo.fontScale(1f); repo.clearMessage()
            val legacy = org.json.JSONObject(repo.backup()).put("theme", "sage").toString()
            runBlocking { repo.restoreBackup(legacy) }
            assertEquals("light", repo.state.value.theme)
            ui.waitForIdle()
            ui.onNodeWithContentDescription("外观与主题").performClick()
            ui.onNodeWithText("浅色").assertIsSelected()
            ui.onNodeWithText("素白").assertDoesNotExist()
            ui.onNodeWithText("雾绿磨砂").assertDoesNotExist()
            ui.onNodeWithText("流光白").assertDoesNotExist()
            ui.activityRule.scenario.recreate()
            ui.waitForIdle()
            ui.onNodeWithText("浅色").assertIsSelected()
        } finally {
            runBlocking { repo.restoreBackup(original) }
        }
    }
}
