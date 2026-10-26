package cn.nascp.clash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cn.nascp.clash.data.ClashRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlainThemeTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        ui.waitForIdle()
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val image = automation.takeScreenshot()
        val file = java.io.File(ui.activity.getExternalFilesDir(null), "plain-qa/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/plain-$name.png")).use { it.readBytes() }
    }

    private fun selectTheme(label:String) {
        ui.onNodeWithText("主题模式").performScrollTo().performClick()
        ui.onNodeWithText(label).performClick();ui.waitForIdle()
    }
    private fun assertTheme(label:String) {
        ui.onNodeWithText("主题模式").performScrollTo().performClick()
        ui.onNode(isSelected() and (hasText(label) or hasAnyDescendant(hasText(label)))).assertExists()
        ui.onNodeWithText("取消").performClick()
    }
    @Test fun compactPagesAndRuntimeActions() {
        val repo = ClashRepository.get(ui.activity)
        runBlocking { repo.initialized() }
        val before = repo.backup()
        try {
            repo.theme("plain"); repo.language("zh"); repo.fontScale(1f); repo.clearMessage()
            runBlocking { repo.saveProfile("素白界面验证", ClashRepository.SAMPLE) }
            ui.waitForIdle()
            capture("home-stopped")
            ui.onAllNodesWithText("设置").onLast().performClick()
            capture("settings")
            ui.onNodeWithText("VPN 设置").performClick()
            ui.onNodeWithText("接管 IPv6").assertExists()
            ui.onNodeWithContentDescription("返回").performClick()
            ui.openSettingsPage("rules")
            ui.onNodeWithContentDescription("返回").performClick()
            ui.onAllNodesWithText("首页").onLast().performClick()
            val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("appops set ${ui.activity.packageName} ACTIVATE_VPN allow")).use { it.readBytes() }
            ui.onNodeWithText("启动 VPN").performClick()
            ui.waitUntil(15000) { repo.state.value.phase in listOf(cn.nascp.clash.data.VpnPhase.RUNNING, cn.nascp.clash.data.VpnPhase.FAILED) }
            assertEquals(repo.state.value.error, cn.nascp.clash.data.VpnPhase.RUNNING, repo.state.value.phase)
            ui.waitUntil(10000) { repo.state.value.proxies.length() > 0 }
            capture("home-running")
            ui.onAllNodesWithText("代理").onLast().performClick()
            capture("proxies")
            ui.onNodeWithText("REJECT").performClick()
            ui.waitUntil(10000) { repo.state.value.proxies.optJSONObject("默认策略")?.optString("now") == "REJECT" }
            ui.onNodeWithText("DIRECT").performClick()
            ui.waitUntil(10000) { repo.state.value.proxies.optJSONObject("默认策略")?.optString("now") == "DIRECT" }
            ui.onNodeWithContentDescription("搜索节点名称").performClick()
            ui.onNodeWithText("搜索节点名称").performTextInput("DIRECT")
            ui.onNodeWithText("REJECT").assertDoesNotExist()
            ui.onNode(hasText("DIRECT") and !hasSetTextAction()).assertExists()
            androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()).pressBack()
            ui.onAllNodesWithText("首页").onLast().performClick()
            ui.onNodeWithText("断开").performClick()
            ui.waitUntil(15000) { repo.state.value.phase == cn.nascp.clash.data.VpnPhase.STOPPED }
            repo.language("en"); repo.fontScale(1.5f)
            ui.waitForIdle()
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            ui.onAllNodesWithText("Disconnected").onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals("Connection status should fit without breaking the word", 1, layouts.single().lineCount)
            capture("home-en-large")
            ui.onAllNodesWithText("Settings").onLast().performClick()
            ui.onNodeWithText("About").performScrollTo().assertExists()
            capture("settings-en-large")
            ui.onNodeWithText("VPN settings").performScrollTo().performClick()
            val statusLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            ui.onAllNodesWithText("Disconnected").onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(statusLayouts) }
            assertEquals("VPN status must not break within a word at large font", 1, statusLayouts.single().lineCount)
            capture("vpn-en-large")
        } finally {
            if (repo.state.value.phase != cn.nascp.clash.data.VpnPhase.STOPPED) {
                ui.activity.startService(android.content.Intent(ui.activity, cn.nascp.clash.vpn.ClashVpnService::class.java).setAction(cn.nascp.clash.vpn.ClashVpnService.STOP))
                ui.waitUntil(15000) { repo.state.value.phase == cn.nascp.clash.data.VpnPhase.STOPPED }
            }
            repo.clearMessage(); runBlocking { repo.restoreBackup(before) }
        }
    }

    @Test fun darkPaletteAndSystemMode() {
        val repo = ClashRepository.get(ui.activity)
        runBlocking { repo.initialized() }
        val original = repo.backup()
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand(command)).bufferedReader().use { it.readText() }
        val nightMode = shell("cmd uimode night").trim().substringAfterLast(' ')
        fun assertBlackBackground() {
            ui.waitUntil(10000) {
                val image = automation.takeScreenshot()
                val black = image.getPixel(0, image.height / 2) == android.graphics.Color.BLACK
                image.recycle()
                black
            }
        }
        try {
            repo.language("zh"); repo.fontScale(1f); repo.clearMessage(); repo.theme("dark")
            ui.waitForIdle()
            assertBlackBackground()
            capture("dark-home")
            for (tab in listOf("代理", "配置", "活动", "设置")) {
                ui.onAllNodesWithText(tab).onLast().performClick()
                ui.waitForIdle()
                assertBlackBackground()
                capture("dark-$tab")
            }
            ui.onNodeWithText("外观与语言").performClick()
            assertTheme("深色")
            ui.onNodeWithText("素白").assertDoesNotExist()
            capture("dark-appearance")
            selectTheme("跟随系统")
            shell("cmd uimode night yes")
            ui.waitForIdle()
            assertBlackBackground()
            assertTheme("跟随系统")
            capture("system-dark")
            shell("cmd uimode night no")
            ui.waitForIdle()
            ui.waitUntil(10000) {
                val image = automation.takeScreenshot()
                val light = image.getPixel(0, image.height / 2) == android.graphics.Color.rgb(244, 244, 244)
                image.recycle()
                light
            }
            assertTheme("跟随系统")
            capture("system-light")
        } finally {
            shell("cmd uimode night ${nightMode.takeIf { it in listOf("yes", "no", "auto") } ?: "no"}")
            runBlocking { repo.restoreBackup(original) }
        }
    }

    @Test fun selectRecreateAndRestoreTheme() {
        val repo = ClashRepository.get(ui.activity)
        runBlocking { repo.initialized() }
        val original = repo.backup()
        try {
            repo.language("zh"); repo.fontScale(1f); repo.clearMessage()
            val missingTheme = org.json.JSONObject(repo.backup()).apply { remove("theme") }.toString()
            repo.theme("dark")
            runBlocking { repo.restoreBackup(missingTheme) }
            assertEquals("Missing backup theme must use the default", "system", repo.state.value.theme)
            val explicitSystem = org.json.JSONObject(missingTheme).put("theme", "system").toString()
            runBlocking { repo.restoreBackup(explicitSystem) }
            assertEquals("Explicit system theme must be preserved", "system", repo.state.value.theme)
            ui.waitForIdle()
            ui.onNodeWithContentDescription("外观与主题").performClick()
            selectTheme("浅色")
            ui.waitForIdle()
            assertEquals("light", repo.state.value.theme)
            assertEquals("light", ui.activity.getSharedPreferences("preferences", 0).getString("theme", null))
            ui.activityRule.scenario.recreate()
            ui.waitForIdle()
            assertTheme("浅色")
            val saved = repo.backup()
            repo.theme("dark")
            runBlocking { repo.restoreBackup(saved) }
            ui.waitForIdle()
            assertEquals("light", repo.state.value.theme)
            assertTheme("浅色")
            selectTheme("深色")
            ui.waitForIdle()
            assertEquals("dark", repo.state.value.theme)
            selectTheme("浅色")
            ui.waitForIdle()
            assertEquals("light", repo.state.value.theme)
            selectTheme("跟随系统")
            ui.waitForIdle()
            assertEquals("system", repo.state.value.theme)
        } finally {
            runBlocking { repo.restoreBackup(original) }
        }
    }
}
