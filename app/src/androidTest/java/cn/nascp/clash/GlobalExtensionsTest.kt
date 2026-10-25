package cn.nascp.clash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cn.nascp.clash.data.ClashRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GlobalExtensionsTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun click(text:String) {
        val node=ui.onAllNodesWithText(text).onLast()
        if(!node.isDisplayed())node.performScrollTo()
        node.performClick();ui.waitForIdle()
    }
    private fun back() { ui.onNodeWithContentDescription("返回").performClick();ui.waitForIdle() }
    @Test fun globalEntriesSaveWithoutProfilesAndStaySeparateFromProfileOverrides() {
        val repo=ClashRepository.get(ui.activity)
        runBlocking { repo.initialized() };val before=repo.backup()
        try {
            val empty=JSONObject(before).put("profiles",JSONArray()).put("selected","")
            val settings=empty.getJSONObject("settings")
            listOf("override","script","proxies","groups","rules").forEach { settings.remove("extension.global.$it");settings.remove("scratch.extension.global.$it") }
            runBlocking { repo.restoreBackup(empty.toString()) }
            repo.theme("light");repo.language("zh");repo.clearMessage()
            click("配置")
            ui.onNodeWithText("全局配置能力").assertDoesNotExist()
            click("全局覆写")
            ui.onNodeWithText("作用于所有配置").assertExists()
            ui.onNodeWithText("覆写内容（YAML）").assertExists()
            ui.onNode(hasSetTextAction()).performTextReplacement("log-level: warning")
            click("校验并保存增强")
            ui.waitUntil(5000) { repo.extensions("global").optString("override")=="log-level: warning" }
            back();click("全局扩展脚本")
            ui.onNodeWithText("脚本内容（JavaScript）").assertExists()
            val script="function main(config) { config['log-level'] = 'error'; return config; }"
            ui.onNode(hasSetTextAction()).performTextReplacement(script)
            click("校验并保存增强")
            ui.waitUntil(5000) { repo.extensions("global").optString("script")==script }
            assertTrue(repo.state.value.profiles.isEmpty())
            runBlocking { repo.saveProfile("Profile A",ClashRepository.SAMPLE) }
            val first=repo.state.value.selectedId
            assertTrue(runBlocking { repo.rendered() }.contains("log-level: error"))
            runBlocking { repo.saveProfile("Profile B",ClashRepository.SAMPLE) }
            val second=repo.state.value.selectedId
            runBlocking { repo.saveExtension(second,"override","log-level: debug") }
            assertTrue(runBlocking { repo.rendered() }.contains("log-level: debug"))
            repo.selectProfile(first)
            assertTrue(runBlocking { repo.rendered() }.contains("log-level: error"))
            val candidate="function main(config) { config['log-level'] = 'silent'; return config; }"
            assertTrue(runBlocking { repo.previewExtension("global","script",candidate) }.contains("log-level: silent"))
            assertEquals(script,repo.extensions("global").getString("script"))
            assertTrue(runCatching { runBlocking { repo.saveExtension("global","override","bad: [") } }.isFailure)
            assertEquals("log-level: warning",repo.extensions("global").getString("override"))
            back()
            ui.onNodeWithTag("profile-manage:$first").performScrollTo().performClick()
            click("订阅覆写")
            ui.onNodeWithText("作用于所有配置").assertDoesNotExist()
            back();click("配置文件（YAML）")
            ui.onNodeWithText("YAML 是配置文件的文本格式",substring=true).assertExists()
        } finally { repo.clearMessage();runBlocking { repo.restoreBackup(before) } }
    }
}
