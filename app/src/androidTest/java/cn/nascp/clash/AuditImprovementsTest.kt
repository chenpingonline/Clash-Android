package cn.nascp.clash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.nascp.clash.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuditImprovementsTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun click(text:String) {
        val node=ui.onAllNodesWithText(text).onLast()
        if(!node.isDisplayed())node.performScrollTo()
        node.performClick();ui.waitForIdle()
    }
    private fun fill(label:String,value:String) {ui.onNodeWithTag("setting-field:$label").performScrollTo().performTextReplacement(value);ui.waitForIdle()}
    @Test fun runtimeFailuresAreIndependentAndRecoverWithoutRestart()=runBlocking {
        val repo=ClashRepository.get(ui.activity);repo.initialized()
        val previous=repo.runtimeApiFetcher
        try {
            repo.foregroundVisible(false)
            repo.phase(VpnPhase.RUNNING)
            repo.runtimeApiFetcher={path->when(path){"/proxies"->JSONObject("{\"proxies\":{\"DIRECT\":{\"type\":\"Direct\"}}}");else->error("fixture failure")}}
            repo.refreshRuntime()
            assertFalse(repo.state.value.proxyError)
            assertTrue(repo.state.value.proxies.has("DIRECT"))
            assertTrue(repo.state.value.controllerError)
            assertTrue(repo.state.value.connectionsError)
            assertFalse(repo.state.value.logsError)
            repo.runtimeApiFetcher={path->when(path){"/proxies"->error("fixture failure");"/configs"->JSONObject("{\"mode\":\"rule\"}");else->JSONObject("{\"connections\":[]}")}}
            repo.refreshRuntime()
            assertTrue(repo.state.value.proxyError)
            assertFalse(repo.state.value.controllerError)
            assertFalse(repo.state.value.connectionsError)
            assertTrue(repo.state.value.proxies.has("DIRECT")) // Keep prior data, label as stale.
            repo.runtimeApiFetcher={path->when(path){"/proxies"->JSONObject("{\"proxies\":{}}");"/configs"->JSONObject("{\"mode\":\"direct\"}");else->JSONObject("{\"connections\":[]}")}}
            repo.refreshRuntime()
            assertFalse(repo.state.value.proxyError)
            assertEquals("direct",repo.state.value.mode)
        } finally {repo.runtimeApiFetcher=previous;repo.phase(VpnPhase.STOPPED);repo.foregroundVisible(true)}
    }
    @Test fun localControllerBypassesSystemProxy()=runBlocking {
        val repo=ClashRepository.get(ui.activity);repo.initialized()
        val original=java.net.ProxySelector.getDefault()
        val server=okhttp3.mockwebserver.MockWebServer()
        try {
            repo.foregroundVisible(false)
            server.start(19097)
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("{\"mode\":\"rule\"}"))
            java.net.ProxySelector.setDefault(object:java.net.ProxySelector() {
                override fun select(uri:java.net.URI):List<java.net.Proxy> = error("Local controller must not use the system proxy")
                override fun connectFailed(uri:java.net.URI,sa:java.net.SocketAddress,ioe:java.io.IOException) {}
            })
            repo.phase(VpnPhase.RUNNING)
            assertEquals("rule",repo.api("/configs").getString("mode"))
        } finally {java.net.ProxySelector.setDefault(original);repo.phase(VpnPhase.STOPPED);repo.foregroundVisible(true);server.shutdown()}
    }
    @Test fun remoteCanSaveOfflineButRejectsInvalidAddress() {
        val repo=ClashRepository.get(ui.activity);runBlocking{repo.initialized()};val before=repo.backup()
        try {
            repo.language("zh");repo.fontScale(1f);repo.clearMessage()
            ui.openSettingsPage("remote-editor")
            fill("实例名称","Offline audit fixture");fill("HTTPS 控制器地址","http://invalid.example")
            click("保存实例")
            ui.waitUntil(5000){repo.state.value.error.isNotBlank()}
            assertFalse(repo.remotes().any{it.name=="Offline audit fixture"})
            fill("HTTPS 控制器地址","https://offline.invalid");click("保存实例")
            ui.waitUntil(5000){repo.remotes().any{it.name=="Offline audit fixture"}}
            assertEquals("https://offline.invalid",repo.remotes().first{it.name=="Offline audit fixture"}.address)
        } finally {repo.clearMessage();runBlocking{repo.restoreBackup(before)}}
    }
    @Test fun appRowCanToggleAndSaveStaysVisible() {
        val repo=ClashRepository.get(ui.activity);runBlocking{repo.initialized()};val before=repo.backup()
        try {
            repo.language("zh");repo.fontScale(1f);repo.saveDraft("app-mode","仅选中");repo.saveDraft("app-packages","")
            ui.openSettingsPage("apps")
            ui.waitUntil(10000){ui.onAllNodes(hasTestTag("app-row:com.android.chrome")).fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("app-row:com.android.chrome").performClick()
            ui.onNodeWithText("保存应用选择").assertIsDisplayed().performClick()
            ui.waitUntil(5000){repo.draft("app-packages").contains("com.android.chrome")}
        } finally {repo.clearMessage();runBlocking{repo.restoreBackup(before)}}
    }
}
