package cn.nascp.clash

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.data.ClashRepository
import cn.nascp.clash.data.VpnPhase
import cn.nascp.clash.ui.proxyCatalog
import cn.nascp.clash.vpn.ClashVpnService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProxyPreviewTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun config(node: String) = "proxies:\n - {name: $node, type: direct}\nproxy-groups:\n - {name: PreviewGroup, type: select, proxies: [$node, DIRECT]}\n - {name: Zulu, type: select, proxies: [DIRECT]}\n - {name: Alpha, type: select, proxies: [DIRECT]}\nrules: ['MATCH,DIRECT']\n"

    @Test fun offlinePreviewRefreshesAndFallsBackAfterStop() {
        val repo = ClashRepository.get(ui.activity)
        runBlocking { repo.initialized() }
        val backup = repo.backup()
        try {
            repo.theme("light"); repo.language("zh"); repo.clearMessage()
            runBlocking { repo.saveProfile("Preview A", config("NodeA")) }
            val first = repo.state.value.selectedId
            ui.waitUntil(10000) { repo.state.value.visibleProxies.has("NodeA") }
            assertEquals(VpnPhase.STOPPED, repo.state.value.phase)
            assertEquals(listOf("PreviewGroup", "Zulu", "Alpha", "GLOBAL"), proxyCatalog(repo.state.value.visibleProxies, repo.state.value.visibleProxyGroupOrder).groups.map { it.name })
            ui.onAllNodesWithText("代理").onLast().performClick()
            ui.waitUntil(10000) { ui.onAllNodesWithTag("proxy-node:NodeA").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("可预选手动分组节点，启动 VPN 后生效；测速需启动 VPN").assertIsDisplayed()
            ui.onNodeWithTag("proxy-node:NodeA").assertIsDisplayed()
            assertTrue(ui.onNodeWithTag("proxy-group:PreviewGroup").fetchSemanticsNode().boundsInRoot.top < ui.onNodeWithTag("proxy-group:Zulu").fetchSemanticsNode().boundsInRoot.top)
            assertTrue(ui.onNodeWithTag("proxy-group:Zulu").fetchSemanticsNode().boundsInRoot.top < ui.onNodeWithTag("proxy-group:Alpha").fetchSemanticsNode().boundsInRoot.top)
            ui.onNodeWithText("一键测速").assertIsNotEnabled()
            ui.onNodeWithTag("proxy-node:DIRECT").performClick()
            ui.waitUntil(10000) { repo.state.value.visibleProxies.optJSONObject("PreviewGroup")?.optString("now")=="DIRECT" }
            ui.onNodeWithTag("proxy-node:DIRECT").assertIsSelected()
            assertEquals("DIRECT", repo.selections(first).optString("PreviewGroup"))
            runBlocking { repo.saveProfile("Preview B", config("NodeB")) }
            ui.waitUntil(10000) { repo.state.value.visibleProxies.has("NodeB") }
            assertFalse(repo.state.value.visibleProxies.has("NodeA"))
            assertFalse(repo.state.value.visibleProxies.getJSONObject("PreviewGroup").has("now"))
            repo.selectProfile(first)
            ui.waitUntil(10000) { repo.state.value.visibleProxies.has("NodeA") }
            assertEquals("DIRECT",repo.state.value.visibleProxies.getJSONObject("PreviewGroup").getString("now"))
            runBlocking { repo.saveExtension(first,"override","proxies:\n - {name: Enhanced, type: direct}\nproxy-groups:\n - {name: PreviewGroup, type: select, proxies: [Enhanced, DIRECT]}\n - {name: Zulu, type: select, proxies: [DIRECT]}\n - {name: Alpha, type: select, proxies: [DIRECT]}") }
            ui.waitUntil(10000) { repo.state.value.visibleProxies.has("Enhanced") }
            assertFalse(repo.state.value.visibleProxies.has("NodeA"))
            ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("appops set ${ui.activity.packageName} ACTIVATE_VPN allow")).use { it.readBytes() }
            assertNull(android.net.VpnService.prepare(ui.activity))
            ContextCompat.startForegroundService(ui.activity, Intent(ui.activity, ClashVpnService::class.java))
            ui.waitUntil(15000) { repo.state.value.phase in listOf(VpnPhase.RUNNING,VpnPhase.FAILED) }
            assertEquals(repo.state.value.error,VpnPhase.RUNNING,repo.state.value.phase)
            ui.waitUntil(10000) { repo.state.value.visibleProxies.optJSONObject("PreviewGroup")?.optString("now") == "DIRECT" }
            assertEquals(listOf("PreviewGroup", "Zulu", "Alpha", "GLOBAL"), proxyCatalog(repo.state.value.visibleProxies, repo.state.value.visibleProxyGroupOrder).groups.map { it.name })
            ui.onNodeWithText("可预选手动分组节点，启动 VPN 后生效；测速需启动 VPN").assertDoesNotExist()
            ui.activity.startService(Intent(ui.activity, ClashVpnService::class.java).setAction(ClashVpnService.STOP))
            ui.waitUntil(15000) { repo.state.value.phase == VpnPhase.STOPPED && repo.state.value.previewReady }
            assertTrue(repo.state.value.visibleProxies.has("Enhanced"))
            assertEquals("DIRECT",repo.state.value.visibleProxies.getJSONObject("PreviewGroup").getString("now"))
            // A removed choice is not highlighted or allowed to prevent startup.
            runBlocking { repo.selectProxy("PreviewGroup","Enhanced") }
            runBlocking { repo.saveExtension(first,"override","proxies:\n - {name: Replacement, type: direct}\nproxy-groups:\n - {name: PreviewGroup, type: select, proxies: [Replacement, DIRECT]}") }
            ui.waitUntil(10000) { repo.state.value.visibleProxies.has("Replacement") }
            assertFalse(repo.state.value.visibleProxies.getJSONObject("PreviewGroup").has("now"))
            ContextCompat.startForegroundService(ui.activity, Intent(ui.activity, ClashVpnService::class.java))
            ui.waitUntil(15000) { repo.state.value.phase in listOf(VpnPhase.RUNNING,VpnPhase.FAILED) }
            assertEquals(repo.state.value.error,VpnPhase.RUNNING,repo.state.value.phase)
            ui.waitUntil(10000) { repo.state.value.visibleProxies.optJSONObject("PreviewGroup")?.optString("now")=="Replacement" }
            assertEquals("部分预选节点已失效，已使用当前可用节点", repo.state.value.error)
            runBlocking { repo.selectProxy("PreviewGroup","DIRECT") }
            assertEquals("DIRECT",repo.selections(first).getString("PreviewGroup"))
            ui.activity.startService(Intent(ui.activity, ClashVpnService::class.java).setAction(ClashVpnService.STOP))
            ui.waitUntil(15000) { repo.state.value.phase == VpnPhase.STOPPED && repo.state.value.previewReady }
            ui.waitUntil(10000) { ui.onAllNodesWithText("可预选手动分组节点，启动 VPN 后生效；测速需启动 VPN").fetchSemanticsNodes().isNotEmpty() }
        } finally {
            ui.activity.startService(Intent(ui.activity, ClashVpnService::class.java).setAction(ClashVpnService.STOP))
            ui.waitUntil(15000) { repo.state.value.phase == VpnPhase.STOPPED }
            runBlocking { repo.restoreBackup(backup) }
        }
    }
}
