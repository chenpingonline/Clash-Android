package cn.nascp.clash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import javax.net.ssl.HttpsURLConnection

class ProfileUpdateUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun capture(name:String) {
        ui.waitForIdle()
        val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file=File(ui.activity.getExternalFilesDir(null),"two-improvements/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};image.recycle()
    }
    @Test fun updateButtonsPreserveSelectionStageRunningAndReportPartialFailure() {
        val repo=ClashRepository.get(ui.activity);runBlocking{repo.initialized()};val before=repo.backup()
        val cert=HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val server=MockWebServer();server.useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory(),false)
        val tls=HttpsURLConnection.getDefaultSSLSocketFactory();val proxy=java.net.ProxySelector.getDefault()
        val fail=java.util.concurrent.atomic.AtomicBoolean(false)
        server.dispatcher=object:Dispatcher(){override fun dispatch(request:RecordedRequest):MockResponse = MockResponse().setBodyDelay(1500,java.util.concurrent.TimeUnit.MILLISECONDS).setBody(
            if(fail.get() && request.path=="/first")"not valid yaml: [" else ClashRepository.SAMPLE+"\n# updated subscription\n")}
        server.start()
        HttpsURLConnection.setDefaultSSLSocketFactory(HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build().sslSocketFactory())
        java.net.ProxySelector.setDefault(object:java.net.ProxySelector(){override fun select(uri:java.net.URI)=listOf(java.net.Proxy.NO_PROXY);override fun connectFailed(uri:java.net.URI,sa:java.net.SocketAddress,e:java.io.IOException){}})
        try {
            runBlocking {
                repo.restoreBackup(JSONObject(before).put("profiles",JSONArray()).put("selected","").toString())
                repo.saveProfile("本地配置",ClashRepository.SAMPLE)
            }
            val local=repo.state.value.selectedId
            runBlocking {repo.saveProfile("日常订阅",ClashRepository.SAMPLE,source=server.url("/first").toString(),select=false,userAgent="UpdateFixture/1.0")}
            val first=repo.state.value.profiles.last().id
            runBlocking {repo.saveProfile("备用订阅",ClashRepository.SAMPLE,source=server.url("/second").toString(),select=false)}
            val second=repo.state.value.profiles.last().id
            repo.language("zh");repo.theme("light");repo.fontScale(1f)
            ui.onAllNodesWithText("配置").onLast().performClick()
            ui.onNodeWithTag("profile-manage:$local").performClick()
            ui.onNodeWithTag("profile-update:$local").assertDoesNotExist()
            ui.onNodeWithText("取消").performClick()
            ui.onNodeWithTag("profile-manage:$first").performClick()
            assertEquals(local,repo.state.value.selectedId)
            capture("profile-actions")
            ui.onNodeWithText("取消").performClick()
            ui.onNodeWithTag("profile-manage:$first").performClick()
            ui.onNodeWithTag("profile-update:$first").performClick()
            ui.waitUntil(5000){repo.state.value.subscriptionUpdates[first]==SubscriptionUpdateStatus.DOWNLOADING}
            capture("profiles-updating")
            ui.waitUntil(10000){repo.state.value.subscriptionUpdates[first]==SubscriptionUpdateStatus.UPDATED}
            assertEquals(local,repo.state.value.selectedId)
            assertEquals("UpdateFixture/1.0",server.takeRequest().getHeader("User-Agent"))
            val saved=repo.state.value.profiles.first{it.id==first}
            repo.foregroundVisible(false);repo.phase(VpnPhase.RUNNING,first)
            ui.onNodeWithTag("profile-manage:$first").performClick()
            ui.onNodeWithTag("profile-update:$first").performClick()
            ui.waitUntil(10000){repo.state.value.subscriptionUpdates[first]==SubscriptionUpdateStatus.STAGED}
            assertEquals(saved,repo.state.value.profiles.first{it.id==first})
            assertTrue(repo.draft("pending-update.$first").isNotBlank())
            ui.onNodeWithText("更新已下载，停止后可应用").assertExists();capture("profiles-staged")
            repo.phase(VpnPhase.STOPPED)
            ui.onNodeWithTag("profile-apply:$first").performClick()
            ui.waitUntil(10000){repo.draft("pending-update.$first").isBlank()}
            assertEquals(local,repo.state.value.selectedId)
            fail.set(true)
            ui.onNodeWithTag("profiles-update-all").performClick()
            ui.waitUntil(10000){repo.state.value.subscriptionUpdates[first]==SubscriptionUpdateStatus.FAILED && repo.state.value.subscriptionUpdates[second]==SubscriptionUpdateStatus.UPDATED}
            assertTrue(repo.state.value.profiles.first{it.id==first}.yaml.contains("# updated subscription"))
            assertEquals(ClashRepository.SAMPLE,repo.state.value.profiles.first{it.id==local}.yaml)
            assertEquals(local,repo.state.value.selectedId)
            repo.clearMessage();capture("profiles-light")
            repo.theme("dark");capture("profiles-dark")
            repo.theme("light");repo.fontScale(1.5f);capture("profiles-large")
        } finally {
            repo.phase(VpnPhase.STOPPED);repo.foregroundVisible(true);repo.clearMessage()
            runBlocking{repo.restoreBackup(before)}
            HttpsURLConnection.setDefaultSSLSocketFactory(tls);java.net.ProxySelector.setDefault(proxy);server.shutdown()
        }
    }
    @Test fun appChoiceVisibilityAndSelectionSurviveModeChanges() {
        val repo=ClashRepository.get(ui.activity);runBlocking{repo.initialized()};val before=repo.backup()
        fun click(text:String){ui.onAllNodesWithText(text).onLast().performClick();ui.waitForIdle()}
        try {
            repo.language("zh");repo.theme("light");repo.fontScale(1f);repo.saveDraft("app-mode","全部应用");repo.saveDraft("app-packages","")
            ui.openSettingsPage("apps")
            ui.waitUntil(10000){ui.onAllNodesWithTag("app-row:com.android.chrome").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("app-choice:com.android.chrome").assertDoesNotExist();capture("apps-all")
            click("代理模式");click("仅选中")
            ui.onNodeWithTag("app-row:com.android.chrome").performClick();ui.onNodeWithTag("app-row:com.android.chrome").assertIsOn()
            click("代理模式");click("全部应用")
            ui.onNodeWithTag("app-choice:com.android.chrome").assertDoesNotExist()
            click("代理模式");click("排除选中")
            ui.onNodeWithTag("app-row:com.android.chrome").assertIsOn();capture("apps-selected")
            ui.onNodeWithTag("app-search").performTextReplacement("chrome")
            ui.onNodeWithTag("app-row:com.android.chrome").assertIsDisplayed()
            ui.onNodeWithTag("app-row:com.android.camera2").assertDoesNotExist()
            ui.onNodeWithText("保存应用选择").assertIsDisplayed().performClick()
            ui.waitUntil(5000){repo.draft("app-packages").contains("com.android.chrome")}
            assertEquals("排除选中",repo.draft("app-mode"))
            ui.onNodeWithContentDescription("清除搜索").performClick();repo.clearMessage()
            repo.theme("dark");capture("apps-dark")
            repo.theme("light");repo.fontScale(1.5f);capture("apps-large")
        } finally {repo.clearMessage();runBlocking{repo.restoreBackup(before)}}
    }
}
