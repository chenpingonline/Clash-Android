package cn.nascp.clash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cn.nascp.clash.data.AppState
import cn.nascp.clash.data.ClashRepository
import cn.nascp.clash.data.VpnPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

class ProxyRunningNavigationTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun runningCatalogCanMoveFromHomeToProxyAndBack() {
        val repo = ClashRepository.get(ui.activity)
        runBlocking { repo.initialized() }
        val field = ClashRepository::class.java.getDeclaredField("mutable").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val state = field.get(repo) as MutableStateFlow<AppState>
        val before = state.value
        try {
            ui.runOnIdle {
                state.value = before.copy(language = "zh", phase = VpnPhase.RUNNING,
                    proxies = JSONObject("""{"Test":{"type":"Selector","now":"A","all":["A","B"]},"A":{"type":"Direct"},"B":{"type":"Direct"}}"""))
            }
            // Allow the catalog and the off-screen list to be composed before navigation.
            Thread.sleep(1000)
            if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("manualReview") == "true") {
                repeat(120) { ui.waitForIdle(); Thread.sleep(500) }
            }
            repeat(3) {
                ui.onAllNodesWithText("代理").onLast().performClick()
                ui.onNodeWithTag("proxy-group:Test").assertIsDisplayed()
                ui.onAllNodesWithText("首页").onLast().performClick()
                ui.waitForIdle()
            }
        } finally { ui.runOnIdle { state.value = before } }
    }
}
