package cn.nascp.clash

import android.app.Application
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.data.AppState
import cn.nascp.clash.data.VpnPhase
import cn.nascp.clash.ui.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ProxyLayoutTest {
    @get:Rule val ui = createComposeRule()
    private val groups = listOf("节点选择", "自动选择", "AI", "TELEGRAM", "游戏")
    private val nodes = listOf("HK 01", "HK 02", "HK 05 电信2x", "JP 01", "JP 02", "US 01")
    private fun fixture(names: List<String> = groups): AppState {
        val proxies = JSONObject()
        nodes.forEach { proxies.put(it, JSONObject().put("type", "AnyTLS")) }
        names.forEach { name -> proxies.put(name, JSONObject().put("type", if (name == "自动选择") "URLTest" else "Selector")
            .put("now", nodes[2]).put("all", JSONArray(nodes))) }
        return AppState(phase = VpnPhase.RUNNING, proxies = proxies)
    }
    private lateinit var controller: ProxyUiState
    private fun screen(state: AppState = fixture(), scale: Float = 1f, theme: String = "light", language: String = "zh") {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val vm = MainViewModel(app)
        ui.setContent {
            val scope = rememberCoroutineScope()
            controller = remember { ProxyUiState(scope) { delay(100); 42L } }
            ClashTheme(theme, language, scale) {
                Surface { Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        UiText("代理", Modifier.padding(16.dp)); Row { ProxyToolbar(state, vm, controller, go = {}) }
                    }
                    ProxyList(state, vm, {}, controller)
                } }
            }
        }
    }
    private fun screenshot(name: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "$name.png")
        ui.waitForIdle()
        automation.takeScreenshot().also { image ->
            file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        }
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/$name.png")).use { it.readBytes() }
    }
    @Test fun groupsCollapseIndependentlyAndGridUsesTwoColumns() {
        screen()
        ui.onNodeWithTag("proxy-group:节点选择").performClick()
        val first = ui.onNodeWithTag("proxy-group:节点选择").fetchSemanticsNode().boundsInRoot
        val second = ui.onNodeWithTag("proxy-group:自动选择").fetchSemanticsNode().boundsInRoot
        assertTrue("Collapsed headers need a visible gap", second.top - first.bottom > 0)
        ui.onNodeWithTag("proxy-group:AI").performClick()
        val left = ui.onNodeWithTag("proxy-node:HK 01").fetchSemanticsNode().boundsInRoot
        val right = ui.onNodeWithTag("proxy-node:HK 02").fetchSemanticsNode().boundsInRoot
        assertEquals(left.top, right.top, 1f); assertTrue(right.left > left.right)
        ui.onNodeWithTag("proxy-node:HK 05 电信2x").assertIsSelected()
        ui.onNodeWithTag("proxy-group:TELEGRAM").performScrollTo().performClick()
        ui.onAllNodesWithTag("proxy-node:HK 01").assertCountEquals(2)
        screenshot("proxy-accordion-two-groups")
        ui.onNodeWithTag("proxy-group:AI").performScrollTo().performClick()
        ui.onAllNodesWithTag("proxy-node:HK 01").assertCountEquals(1)
    }
    @Test fun accordionMovesFollowingHeadersThroughIntermediatePositions() {
        screen(fixture(listOf("节点选择", "自动选择")))
        ui.waitUntil(5000) { ui.onAllNodesWithTag("proxy-node:HK 01").fetchSemanticsNodes().isNotEmpty() }
        ui.waitForIdle()
        val before=ui.onNodeWithTag("proxy-group:自动选择").getUnclippedBoundsInRoot().top
        ui.mainClock.autoAdvance=false
        try {
            ui.onNodeWithTag("proxy-group:节点选择").performClick()
            ui.mainClock.advanceTimeBy(96);ui.waitForIdle()
            val during=ui.onNodeWithTag("proxy-group:自动选择").getUnclippedBoundsInRoot().top
            ui.mainClock.advanceTimeBy(500);ui.waitForIdle()
            val after=ui.onNodeWithTag("proxy-group:自动选择").getUnclippedBoundsInRoot().top
            assertTrue("Header must travel rather than jump: $before / $during / $after", during < before - 4.dp && during > after + 4.dp)
            ui.onNodeWithTag("proxy-group:节点选择").performClick();ui.mainClock.advanceTimeBy(64)
            ui.onNodeWithTag("proxy-group:节点选择").performClick();ui.mainClock.advanceTimeBy(64)
            ui.onNodeWithTag("proxy-group:节点选择").performClick();ui.mainClock.advanceTimeBy(500);ui.waitForIdle()
            ui.onNodeWithTag("proxy-node:HK 01").assertIsDisplayed()
        } finally { ui.mainClock.autoAdvance=true }
    }

    @Test fun largeFontLongNamesAndAutomaticGroups() {
        val long = "非常长的代理组名称用于验证大字号显示不会越过屏幕边界"
        screen(fixture(listOf("自动选择", long)), 1.5f, "dark", "en")
        ui.onNodeWithTag("proxy-node:HK 01").assertIsNotEnabled()
        val left = ui.onNodeWithTag("proxy-node:HK 01").fetchSemanticsNode().boundsInRoot
        val next = ui.onNodeWithTag("proxy-node:HK 02").fetchSemanticsNode().boundsInRoot
        assertTrue("Large text uses one readable column", next.top > left.bottom)
        ui.onNodeWithTag("proxy-group:自动选择").performClick()
        ui.onNodeWithTag("proxy-group:$long").performScrollTo().performClick()
        ui.onNodeWithTag("proxy-node:HK 05 电信2x").performScrollTo().assertIsDisplayed()
        screenshot("proxy-accordion-large-dark")
    }
    @Test fun searchAndProtocolFilterAlsoLimitBatchTargets() {
        screen(theme = "plain")
        ui.onNodeWithContentDescription("搜索节点名称").performClick()
        ui.onNodeWithText("搜索节点名称").performTextInput("JP")
        ui.onNodeWithTag("proxy-node:HK 01").assertDoesNotExist()
        ui.onNodeWithTag("proxy-node:JP 01").assertIsDisplayed()
        ui.onNodeWithText("一键测速").performClick()
        ui.waitUntil(5000) { !controller.testing && controller.results.size == 2 }
        assertEquals(setOf("JP 01", "JP 02"), controller.results.keys)
        ui.runOnIdle { controller.protocol = "VLESS" }
        ui.onNodeWithText("没有匹配的节点").assertIsDisplayed()
        ui.onNodeWithText("一键测速").assertIsNotEnabled()
        screenshot("proxy-accordion-filtered")
    }
    @Test fun historyRefreshDoesNotInvalidateCatalogButSelectionDoes() {
        val state = fixture()
        val before = proxyCatalog(state.proxies)
        val refreshed = JSONObject(state.proxies.toString())
        refreshed.getJSONObject(nodes.first()).put("history", JSONArray().put(JSONObject().put("delay", 123))).put("alive", true)
        assertEquals(before, proxyCatalog(refreshed))
        refreshed.getJSONObject("AI").put("now", nodes.last())
        assertNotEquals(before, proxyCatalog(refreshed))
        refreshed.getJSONObject(nodes.first()).put("type", "VLESS")
        assertEquals("VLESS", proxyCatalog(refreshed).groups.first().nodes.first().type)
    }

    @Test fun hundredsOfNodesStayLazyAndCanScrollToEnd() {
        val proxies = JSONObject()
        val names = (1..400).map { "Node %04d".format(it) }
        names.forEach { proxies.put(it, JSONObject().put("type", "Direct")) }
        proxies.put("Large", JSONObject().put("type", "Selector").put("now", names.first()).put("all", JSONArray(names)))
        screen(AppState(phase = VpnPhase.RUNNING, proxies = proxies))
        ui.waitUntil(5000) { ui.onAllNodesWithTag("proxy-node:${names.first()}").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("proxy-node:${names.first()}").assertIsSelected()
        ui.onNodeWithTag("proxy-node:${names.last()}").assertDoesNotExist()
        ui.onNodeWithTag("proxy-groups").performScrollToIndex(200)
        ui.onNodeWithTag("proxy-node:${names.last()}").assertIsDisplayed()
        ui.onNodeWithTag("proxy-node:${names.first()}").assertDoesNotExist()
        ui.onNodeWithTag("proxy-groups").performScrollToIndex(1)
        ui.onNodeWithTag("proxy-node:${names.first()}").assertIsSelected()
    }

    @Test fun batchDeduplicatesBoundsConcurrencyAndCanCancel() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val called = mutableListOf<String>()
        var concurrent = 0; var peak = 0
        val control = ProxyUiState(scope) { name ->
            called += name; concurrent++; peak = maxOf(peak, concurrent)
            try { delay(150); if (name == "bad") error("failure"); 42L } finally { concurrent-- }
        }
        try {
            withContext(Dispatchers.Main) { control.test(listOf("a", "a", "b", "c", "d", "bad")) }
            withTimeout(5000) { while (control.testing) delay(20) }
            assertEquals(5, called.size); assertTrue(peak <= 4); assertEquals("失败", control.results["bad"])
            withContext(Dispatchers.Main) { control.test((1..20).map { "cancel-$it" }); delay(40); control.stop() }
            withTimeout(5000) { while (control.testing) delay(20) }
            assertTrue(control.results.values.none { it == "测试中" })
            assertTrue(control.completed < control.total)
        } finally { scope.cancel() }
    }
}
