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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProxyNavigationTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun runningProxyViewportIsReusedAcrossHomeAndHiddenFromHomeSemantics() {
        val repo = ClashRepository.get(ui.activity)
        runBlocking { repo.initialized() }
        val backup = repo.backup()
        try {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
                "appops set ${ui.activity.packageName} ACTIVATE_VPN allow")).use { it.readBytes() }
            assertNull(android.net.VpnService.prepare(ui.activity))
            repo.theme("light"); repo.language("zh"); repo.fontScale(1f); repo.clearMessage()
            repo.saveDraft("vpn-options", "{}"); repo.saveDraft("app-mode", "全部应用")
            val names = (1..400).map { "Node %04d".format(it) }
            val config = "mode: rule\nlog-level: info\nproxies:\n" +
                names.joinToString("\n") { "  - name: $it\n    type: direct" } +
                "\nproxy-groups:\n  - name: Large\n    type: select\n    proxies:\n" +
                names.joinToString("\n") { "      - $it" } + "\nrules:\n  - MATCH,DIRECT\n"
            runBlocking { repo.saveProfile("代理切页本地回归", config) }
            ContextCompat.startForegroundService(ui.activity, Intent(ui.activity, ClashVpnService::class.java))
            ui.waitUntil(15000) { repo.state.value.phase in listOf(VpnPhase.RUNNING, VpnPhase.FAILED) }
            assertEquals(repo.state.value.error, VpnPhase.RUNNING, repo.state.value.phase)
            ui.waitUntil(10000) { repo.state.value.proxies.has("Large") }
            ui.waitForIdle()
            ui.onNodeWithTag("proxy-groups").assertDoesNotExist()
            ui.onAllNodesWithText("代理").onLast().performClick(); ui.waitForIdle()
            ui.onNodeWithTag("proxy-node:${names.first()}").assertIsDisplayed()
            val columns = if (ui.activity.resources.configuration.screenWidthDp < 360) 1 else 2
            ui.onNodeWithTag("proxy-groups").performScrollToIndex((names.size + columns - 1) / columns)
            ui.onNodeWithTag("proxy-node:${names.last()}").assertIsDisplayed()
            ui.onNodeWithTag("proxy-node:${names.first()}").assertDoesNotExist()
            ui.onAllNodesWithText("首页").onLast().performClick(); ui.waitForIdle()
            ui.onNodeWithText("当前配置").assertIsDisplayed()
            ui.onNodeWithTag("proxy-groups").assertDoesNotExist()
            ui.onAllNodesWithText("代理").onLast().performClick(); ui.waitForIdle()
            ui.onNodeWithTag("proxy-node:${names.last()}").assertIsDisplayed()
            ui.onNodeWithTag("proxy-node:${names.first()}").assertDoesNotExist()
        } finally {
            ui.activity.startService(Intent(ui.activity, ClashVpnService::class.java).setAction(ClashVpnService.STOP))
            ui.waitUntil(15000) { repo.state.value.phase == VpnPhase.STOPPED }
            runBlocking { repo.restoreBackup(backup) }
        }
    }
}
