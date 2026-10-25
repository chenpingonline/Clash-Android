package cn.nascp.clash

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.data.ClashRepository
import cn.nascp.clash.data.VpnPhase
import cn.nascp.clash.vpn.ClashVpnService
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ProxyRuntimeTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun selectMembersAndTestActualCore() {
        val repo = ClashRepository.get(ui.activity)
        runBlocking { repo.initialized() }
        val backup = repo.backup()
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(204) }
        server.start()
        try {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("appops set ${ui.activity.packageName} ACTIVATE_VPN allow")).use { it.readBytes() }
            assertNull(android.net.VpnService.prepare(ui.activity))
            repo.theme("light"); repo.language("zh"); repo.fontScale(1f); repo.clearMessage()
            repo.saveDraft("vpn-options", "{}"); repo.saveDraft("app-mode", "全部应用")
            repo.saveDraft("test-url", server.url("/generate_204").toString())
            val names = listOf("HK 01", "HK 02", "HK 05 电信2x", "JP 01", "JP 02", "US 01")
            val config = """
                mode: rule
                log-level: info
                proxies:
            """.trimIndent() + "\n" + names.joinToString("\n") { "  - name: $it\n    type: direct" } + "\nproxy-groups:\n" +
                listOf("节点选择", "自动选择", "AI", "TELEGRAM", "游戏").joinToString("\n") { group ->
                    "  - name: $group\n    type: select\n    proxies:\n" + names.joinToString("\n") { "      - $it" }
                } + "\nrules:\n  - MATCH,DIRECT\n"
            runBlocking { repo.saveProfile("代理折叠布局验证", config) }
            ContextCompat.startForegroundService(ui.activity, Intent(ui.activity, ClashVpnService::class.java))
            ui.waitUntil(15000) { repo.state.value.phase in listOf(VpnPhase.RUNNING, VpnPhase.FAILED) }
            assertEquals(repo.state.value.error, VpnPhase.RUNNING, repo.state.value.phase)
            ui.waitUntil(10000) { repo.state.value.proxies.has("AI") }
            ui.onAllNodesWithText("代理").onLast().performClick()
            listOf("AI", "GLOBAL", "TELEGRAM", "节点选择", "自动选择", "游戏").forEach { name ->
                val header = ui.onNodeWithTag("proxy-group:$name").performScrollTo()
                if (header.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription] == "已展开") header.performClick()
            }
            ui.onNodeWithTag("proxy-group:AI").performScrollTo().performClick()
            ui.onNodeWithTag("proxy-node:HK 05 电信2x").performClick()
            ui.waitUntil(10000) { repo.state.value.proxies.optJSONObject("AI")?.optString("now") == "HK 05 电信2x" }
            ui.onNodeWithTag("proxy-node:HK 05 电信2x").assertIsSelected()
            ui.onNodeWithText("测本组").performClick()
            ui.waitUntil(15000) { server.requestCount >= names.size }
            ui.waitUntil(15000) { ui.onAllNodesWithText("停止").fetchSemanticsNodes().isEmpty() }
            assertEquals("HK 05 电信2x", repo.state.value.proxies.optJSONObject("AI")?.optString("now"))
            val beforeBatch = server.requestCount
            val allLeaves = names.size + 1 // Six named direct routes plus built-in DIRECT from GLOBAL.
            ui.onNodeWithText("一键测速").performClick()
            ui.waitUntil(15000) { server.requestCount >= beforeBatch + allLeaves }
            ui.waitUntil(15000) { ui.onAllNodesWithText("停止").fetchSemanticsNodes().isEmpty() }
            assertEquals("Batch must deduplicate members shared across groups", beforeBatch + allLeaves, server.requestCount)

        } finally {
            ui.activity.startService(Intent(ui.activity, ClashVpnService::class.java).setAction(ClashVpnService.STOP))
            ui.waitUntil(15000) { repo.state.value.phase == VpnPhase.STOPPED }
            runBlocking { repo.restoreBackup(backup) }; server.shutdown()
        }
    }
}
