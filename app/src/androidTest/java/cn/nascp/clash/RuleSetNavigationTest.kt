package cn.nascp.clash

import androidx.activity.BackEventCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import cn.nascp.clash.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class RuleSetNavigationTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun click(text:String) {val n=ui.onAllNodesWithText(text).onLast();if(!n.isDisplayed())n.performScrollTo();n.performClick();ui.waitForIdle()}
    private fun route(id:String) {ui.waitUntil(7000){ui.onAllNodesWithTag("route:$id").fetchSemanticsNodes().isNotEmpty()}}
    private fun imeVisible()=ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())==true
    @Test fun ruleProvidersSupportToolbarSystemBothEdgesAndCancelledPredictiveBack() {
        val repo=ClashRepository.get(ui.activity);runBlocking{repo.initialized()};val backup=repo.backup()
        try {
            val providers=(1..64).joinToString("\n") {"  provider-$it:\n    type: inline\n    behavior: domain\n    payload: ['+.example$it.org']"}
            runBlocking{repo.saveProfile("Rule provider navigation","proxies: []\nproxy-groups: []\nrule-providers:\n$providers\nrules: ['MATCH,DIRECT']\n")}
            repo.language("zh");repo.theme("light");repo.fontScale(1f);repo.clearMessage()
            click("配置");ui.onNodeWithTag("profile-manage:${repo.state.value.selectedId}").performScrollTo().performClick();ui.onNodeWithTag("profile-details:${repo.state.value.selectedId}").performClick();route("profile")
            click("规则与规则集");route("profile-rules")
            repeat(4) { attempt ->
                click("规则集管理");route("rule-set")
                ui.waitUntil(10000){ui.onAllNodesWithText("provider-64").fetchSemanticsNodes().isNotEmpty()}
                ui.onAllNodes(hasSetTextAction()).assertCountEquals(0)
                if(attempt==0) {
                    ui.runOnUiThread {
                        ui.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f,800f,0f,BackEventCompat.EDGE_LEFT))
                        ui.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(300f,800f,0.5f,BackEventCompat.EDGE_LEFT))
                        ui.activity.onBackPressedDispatcher.dispatchOnBackCancelled()
                    }
                    ui.waitForIdle();ui.onNodeWithTag("route:rule-set").assertExists()
                    ui.onNodeWithContentDescription("返回").performClick()
                } else if(attempt==1)device.pressBack()
                else {
                    val w=device.displayWidth;val y=device.displayHeight/2
                    device.swipe(if(attempt==2)1 else w-1,y,if(attempt==2)w*2/3 else w/3,y,24)
                }
                route("profile-rules")
            }
            click("规则集管理");if(ui.onAllNodesWithText("高级 YAML 编辑").fetchSemanticsNodes().isNotEmpty())click("高级 YAML 编辑")
            val editor=ui.onNode(hasSetTextAction());editor.performTextReplacement("{}\n# saved draft on immediate back")
            device.pressBack();ui.waitForIdle()
            if(ui.onAllNodesWithTag("route:rule-set").fetchSemanticsNodes().isNotEmpty())device.pressBack()
            route("profile-rules")
            ui.waitUntil(5000){repo.draft("editor.${repo.state.value.selectedId}.rule-providers").contains("saved draft")}
            click("规则集管理");if(ui.onAllNodesWithText("高级 YAML 编辑").fetchSemanticsNodes().isNotEmpty())click("高级 YAML 编辑")
            ui.onNode(hasSetTextAction()).assertTextContains("saved draft",substring=true)
            ui.onNodeWithContentDescription("返回").performClick();route("profile-rules")
        } finally {repo.clearMessage();runBlocking{repo.restoreBackup(backup)}}
    }
}
