package cn.nascp.clash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import cn.nascp.clash.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun click(text: String) {
        val node=ui.onAllNodesWithText(text).onLast()
        if(!node.isDisplayed())node.performScrollTo()
        node.performClick()
        ui.waitForIdle()
    }
    private fun fill(label: String, value: String) {
        ui.onNodeWithText(label).performScrollTo().performTextReplacement(value)
        ui.waitForIdle()
    }
    private fun screenshot(name: String) {
        ui.waitForIdle()
        val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file=File(ui.activity.getExternalFilesDir(null),"subscription-qa/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        image.recycle()
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("mkdir -p /data/local/tmp/clash-subscription-qa")).use{it.readBytes()}
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/clash-subscription-qa/${file.name}")).use{it.readBytes()}
    }
    @Test fun importAndEditKeepSubscriptionIdentity() {
        val repo=ClashRepository.get(ui.activity)
        runBlocking { repo.initialized() }
        val before=repo.backup()
        val server=okhttp3.mockwebserver.MockWebServer()
        val certificate=okhttp3.tls.HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        server.useHttps(okhttp3.tls.HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(),false)
        server.start()
        val previous=javax.net.ssl.HttpsURLConnection.getDefaultSSLSocketFactory()
        javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(okhttp3.tls.HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build().sslSocketFactory())
        try {
            repo.language("zh");repo.fontScale(1f);repo.clearMessage()
            ui.onAllNodesWithText("设置").onLast().performClick()
            ui.openSettingsPage("import")
            ui.onNodeWithText("订阅 User-Agent").assertDoesNotExist()
            ui.onNodeWithText("手动连接诊断").assertDoesNotExist()
            ui.onNodeWithText("配置内容（YAML）").assertDoesNotExist()
            click("高级选项")
            ui.onNodeWithText(DEFAULT_SUBSCRIPTION_UA).assertExists()
            ui.onNodeWithText("CFA").assertDoesNotExist()
            ui.onNodeWithText("CMFA").assertDoesNotExist()
            click("自定义");fill("自定义 User-Agent","FixtureClient/1.0")
            click("默认");ui.onNodeWithText(DEFAULT_SUBSCRIPTION_UA).assertExists()
            click("自定义");ui.onNodeWithText("FixtureClient/1.0").assertExists()
            screenshot("import-custom")
            fill("订阅名称（可选）","Subscription UI fixture")
            fill("HTTPS订阅地址",server.url("/subscription").toString())
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody(ClashRepository.SAMPLE))
            click("导入订阅")
            ui.waitUntil(5000){repo.state.value.selected?.name=="Subscription UI fixture"}
            assertEquals("FixtureClient/1.0",repo.state.value.selected!!.userAgent)
            assertEquals("FixtureClient/1.0",server.takeRequest().getHeader("User-Agent"))
            click("订阅信息")
            ui.onNodeWithText("自定义 User-Agent").assertExists()
            fill("自定义 User-Agent",CFA_SUBSCRIPTION_UA);click("保存修改")
            ui.waitUntil(5000){repo.state.value.selected?.userAgent==CFA_SUBSCRIPTION_UA}
            ui.onNodeWithContentDescription("关闭提示").performClick()
            ui.onNodeWithContentDescription("返回").performClick();ui.waitForIdle()
            click("订阅信息");ui.onNodeWithText(CFA_SUBSCRIPTION_UA).assertExists()
            screenshot("profile-cfa")
            click("自定义");fill("自定义 User-Agent"," ");click("保存修改")
            ui.waitUntil(5000){repo.state.value.error.isNotBlank()}
            assertEquals(CFA_SUBSCRIPTION_UA,repo.state.value.selected!!.userAgent)
            ui.onNodeWithContentDescription("关闭提示").performClick()
        } finally { repo.clearMessage();runBlocking { repo.restoreBackup(before) };javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(previous);server.shutdown() }
    }

    @Test fun simpleImportValidatesBeforeSavingAndNameIsOptional() {
        val repo=ClashRepository.get(ui.activity)
        runBlocking { repo.initialized() };val before=repo.backup()
        val server=okhttp3.mockwebserver.MockWebServer()
        val certificate=okhttp3.tls.HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        server.useHttps(okhttp3.tls.HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(),false);server.start()
        val previous=javax.net.ssl.HttpsURLConnection.getDefaultSSLSocketFactory()
        javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(okhttp3.tls.HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build().sslSocketFactory())
        try {
            repo.language("zh");repo.clearMessage()
            click("配置");click("添加配置")
            ui.onNodeWithText("导入订阅").assertIsNotEnabled()
            ui.onNodeWithText("订阅 User-Agent").assertDoesNotExist()
            ui.onNodeWithText("校验").assertDoesNotExist()
            ui.onNodeWithText("配置内容（YAML）").assertDoesNotExist()
            val count=repo.state.value.profiles.size
            val address=server.url("/simple").toString()
            fill("HTTPS订阅地址",address)
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("invalid: ["))
            click("导入订阅")
            ui.waitUntil(5000) { repo.state.value.error.isNotBlank() }
            assertEquals(count,repo.state.value.profiles.size)
            ui.onNodeWithText("订阅名称（可选）").assertExists()
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody(ClashRepository.SAMPLE))
            click("导入订阅")
            ui.waitUntil(5000) { repo.state.value.profiles.size==count+1 }
            assertEquals("订阅配置",repo.state.value.selected!!.name)
            assertEquals(address,repo.state.value.selected!!.source)
            repeat(2) { assertEquals(DEFAULT_SUBSCRIPTION_UA,server.takeRequest().getHeader("User-Agent")) }
            // Paste remains a separate, single-save flow with no download step.
            click("添加配置");click("粘贴")
            ui.onNodeWithText("HTTPS订阅地址").assertDoesNotExist()
            fill("配置名称（可选）","Paste fixture")
            fill("配置内容（YAML）",ClashRepository.SAMPLE)
            click("导入配置")
            ui.waitUntil(5000) { repo.state.value.selected?.name=="Paste fixture" }
            assertEquals("",repo.state.value.selected!!.source)
            assertEquals(2,server.requestCount)
        } finally {
            repo.clearMessage();runBlocking { repo.restoreBackup(before) }
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(previous);server.shutdown()
        }
    }

    @Test fun diagnosisShowsComparisonAndSelectionRequiresExplicitSave() {
        val repo=ClashRepository.get(ui.activity)
        runBlocking{repo.initialized()};val before=repo.backup()
        val certificate=okhttp3.tls.HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val trust=okhttp3.tls.HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val previous=javax.net.ssl.HttpsURLConnection.getDefaultSSLSocketFactory()
        val server=okhttp3.mockwebserver.MockWebServer()
        server.useHttps(okhttp3.tls.HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(),false)
        server.dispatcher=object:okhttp3.mockwebserver.Dispatcher(){
            override fun dispatch(request:okhttp3.mockwebserver.RecordedRequest):okhttp3.mockwebserver.MockResponse =
                okhttp3.mockwebserver.MockResponse().setResponseCode(when(request.getHeader("User-Agent")){
                    DEFAULT_SUBSCRIPTION_UA -> 200
                    else -> 403
                })
        }
        server.start();javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(trust.sslSocketFactory())
        try{
            runBlocking{repo.saveProfile("Diagnosis UI fixture",ClashRepository.SAMPLE,source=server.url("/probe?token=fixture").toString(),userAgent=CFA_SUBSCRIPTION_UA)}
            repo.language("zh");repo.fontScale(1f);repo.clearMessage()
            ui.onAllNodesWithText("设置").onLast().performClick()
            ui.openSettingsPage("profile-info");click("开始对比")
            ui.waitUntil(10000){ui.onAllNodesWithText("HTTP 200",substring=true).fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText("HTTP 403",substring=true).assertExists()
            ui.onNodeWithText("HTTP 200",substring=true).assertExists()
            ui.onNodeWithText("HTTP 200",substring=true).performScrollTo()
            screenshot("diagnostics-comparison")
            assertEquals(CFA_SUBSCRIPTION_UA,repo.state.value.selected!!.userAgent)
            assertEquals(ClashRepository.SAMPLE,repo.state.value.selected!!.yaml)
            click("使用默认身份")
            assertEquals(CFA_SUBSCRIPTION_UA,repo.state.value.selected!!.userAgent)
            ui.onNodeWithText(DEFAULT_SUBSCRIPTION_UA).assertExists()
            click("保存修改")
            ui.waitUntil(5000){repo.state.value.selected?.userAgent==DEFAULT_SUBSCRIPTION_UA}
            ui.onNodeWithContentDescription("关闭提示").performClick()
            assertEquals(2,server.requestCount)
        }finally{
            repo.clearMessage();runBlocking{repo.restoreBackup(before)}
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(previous);server.shutdown()
        }
    }
}
