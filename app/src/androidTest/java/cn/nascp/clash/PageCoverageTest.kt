package cn.nascp.clash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.data.*
import cn.nascp.clash.ui.localized
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import java.io.File

class PageCoverageTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun screenshot(name:String) {
        val file=File(ui.activity.getExternalFilesDir(null),"settings-organization/$name.png");file.parentFile!!.mkdirs()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().also{image->
            file.outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};image.recycle()
        }
    }
    @Test fun secondaryPagesRenderThroughCanonicalEntrancesInBothThemesAndLanguages() {
        val repo=ClashRepository.get(ui.activity);runBlocking{repo.initialized()};val before=repo.backup()
        try {
            runBlocking{repo.saveProfile("UI validation",ClashRepository.SAMPLE)}
            val selected=InstrumentationRegistry.getArguments().getString("screenIds")?.split(",")
            for((theme,language,scale) in listOf(Triple("light","zh",1f),Triple("dark","zh",1f),Triple("light","en",1.5f),Triple("dark","en",1.5f))) {
                repo.theme(theme);repo.language(language);repo.fontScale(scale);repo.clearMessage();ui.waitForIdle()
                for(id in settingsPagePaths.keys.filter{selected==null || it in selected}) {
                    ui.openSettingsPage(id)
                    ui.onNodeWithTag("route:$id").assertExists()
                    ui.onNodeWithContentDescription(localized("返回",language)).assertExists()
                    screenshot("$theme-$language-$id")
                    ui.onNodeWithContentDescription(localized("返回",language)).performClick();ui.waitForIdle()
                    ui.onNodeWithTag("route:$id").assertDoesNotExist()
                    org.junit.Assert.assertTrue("$theme/$language/$id: ${repo.state.value.error}",repo.state.value.error.isBlank())
                }
            }
        } finally {repo.clearMessage();runBlocking{repo.restoreBackup(before)}}
    }
    @Test fun emptyProfileSettingsHaveClearNavigationAndGlobalEditingRemainsAvailable() {
        val repo=ClashRepository.get(ui.activity);runBlocking{repo.initialized()};val before=repo.backup()
        try {
            runBlocking{repo.restoreBackup(JSONObject(before).put("profiles",JSONArray()).put("selected","").toString())}
            repo.language("zh");repo.fontScale(1f);repo.clearMessage()
            val ids=listOf("appearance","vpn","apps","automation","system","backup","core","geo-status","speed-test","diagnostics","global-enhancements","global-override","global-script","remote","remote-editor","about","import","rules","runtime-providers")
            for(id in ids) {
                ui.openSettingsPage(id);ui.onNodeWithTag("route:$id").assertExists()
                ui.onNodeWithContentDescription("返回").performClick();ui.waitForIdle()
                org.junit.Assert.assertTrue("Empty $id: ${repo.state.value.error}",repo.state.value.error.isBlank())
            }
            ui.openSettingsPage("about");ui.onNodeWithText("页面目录").assertDoesNotExist()
        } finally {repo.clearMessage();runBlocking{repo.restoreBackup(before)}}
    }
}
