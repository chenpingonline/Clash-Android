package cn.nascp.clash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cn.nascp.clash.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection

class RemoteWorkspaceUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun instanceWorkspaceLoadsTabsAndControlsOnlyRemoteNode() {
        val repo=ClashRepository.get(ui.activity);runBlocking{repo.initialized()};val backup=repo.backup()
        val selected=AtomicReference("DIRECT")
        val certificate=HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val server=MockWebServer()
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(),false)
        server.dispatcher=object:Dispatcher(){override fun dispatch(request:RecordedRequest):MockResponse {
            if(request.getHeader("Authorization")!="Bearer fixture-token")return MockResponse().setResponseCode(401)
            val body=when(request.path) {
                "/version"->"""{"version":"fixture"}"""
                "/proxies"->"""{"proxies":{"Manual":{"type":"Selector","now":"${selected.get()}","all":["DIRECT","REJECT"]}}}"""
                "/proxies/Manual"->{selected.set(org.json.JSONObject(request.body.readUtf8()).getString("name"));"{}"}
                "/connections"->"""{"connections":[{"id":"fixture-id","metadata":{"host":"fixture.invalid"}}]}"""
                else->"{}"
            }
            return MockResponse().setBody(body)
        }}
        server.start()
        val previous=HttpsURLConnection.getDefaultSSLSocketFactory()
        HttpsURLConnection.setDefaultSSLSocketFactory(HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build().sslSocketFactory())
        try {
            val phase=repo.state.value.phase;val profile=repo.state.value.selectedId
            repo.language("zh");repo.theme("light");repo.fontScale(1f)
            repo.saveRemotes(listOf(RemoteInstance("workspace-fixture","Fixture remote",server.url("/").toString(),"fixture-token")))
            ui.openSettingsPage("remote")
            ui.onNodeWithText("打开工作区").performClick()
            ui.waitUntil(7000){ui.onAllNodesWithText("REJECT").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText("REJECT").performScrollTo().performClick()
            ui.waitUntil(5000){selected.get()=="REJECT"}
            ui.onNodeWithText("连接").performScrollTo().performClick()
            ui.waitUntil(5000){ui.onAllNodesWithText("fixture.invalid").fetchSemanticsNodes().isNotEmpty()}
            assertEquals(phase,repo.state.value.phase);assertEquals(profile,repo.state.value.selectedId)
            ui.onNodeWithContentDescription("返回").performClick();ui.waitForIdle()
            ui.onNodeWithTag("route:remote").assertExists()
        } finally {
            HttpsURLConnection.setDefaultSSLSocketFactory(previous);server.shutdown()
            repo.clearMessage();runBlocking{repo.restoreBackup(backup)}
        }
    }
}
