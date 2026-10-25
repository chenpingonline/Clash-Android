package cn.nascp.clash

import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import cn.nascp.clash.ui.*
import org.junit.Rule
import org.junit.Test

class CoreLogLevelUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun singleSettingMenuSupportsLargeTextAndDisabledState() {
        var running by mutableStateOf(false)
        var level by mutableStateOf("info")
        ui.setContent { ClashTheme("dark","en",1.5f) { Surface { RuntimeLogLevelSetting(level,running,false,true){level=it} } } }
        ui.onNodeWithTag("log-level-setting").assertIsNotEnabled()
        ui.onNodeWithText("Not running").assertIsDisplayed()
        ui.onNodeWithTag("log-level-option:debug").assertDoesNotExist()
        ui.runOnIdle { running=true }
        ui.onNodeWithTag("log-level-setting").performClick()
        ui.onNodeWithTag("log-level-option:info").assertIsSelected()
        ui.onNodeWithTag("log-level-option:debug").performClick()
        ui.onNodeWithText("Debug (debug)").assertIsDisplayed()
        ui.onNodeWithTag("log-level-option:debug").assertDoesNotExist()
        ui.onNodeWithTag("log-level-setting").performClick()
        ui.runOnIdle { running=false }
        ui.onNodeWithTag("log-level-option:debug").assertDoesNotExist()
        ui.onNodeWithTag("log-level-setting").assertIsNotEnabled()
    }
}
