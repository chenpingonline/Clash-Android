package cn.nascp.clash

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.data.ClashRepository
import cn.nascp.clash.data.VpnPhase
import cn.nascp.clash.vpn.ClashVpnService
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in live fixture without a Compose test clock, for external Computer Use verification. */
class ProxyManualFixtureTest {
    @Test fun liveFixture() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("manualFixture") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val repo = ClashRepository.get(context); repo.initialized()
        val backup = repo.backup()
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(204) }
        server.start()
        val release = File(context.filesDir, "proxy-qa-release"); release.delete()
        try {
            ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("appops set ${context.packageName} ACTIVATE_VPN allow")).use { it.readBytes() }
            check(android.net.VpnService.prepare(context) == null)
            repo.theme("plain"); repo.language("zh"); repo.fontScale(1f); repo.clearMessage()
            repo.saveDraft("vpn-options", "{}"); repo.saveDraft("app-mode", "全部应用")
            repo.saveDraft("test-url", server.url("/generate_204").toString())
            val count = InstrumentationRegistry.getArguments().getString("nodeCount")?.toIntOrNull() ?: 6
            val names = if (count == 6) listOf("HK 01", "HK 02", "HK 05 电信2x", "JP 01", "JP 02", "US 01") else (1..count).map { "HK %04d 测试节点".format(it) }
            val config = "mode: rule\nlog-level: info\nproxies:\n" + names.joinToString("\n") { "  - name: $it\n    type: direct" } + "\nproxy-groups:\n" +
                listOf("节点选择", "自动选择", "AI", "TELEGRAM", "游戏").joinToString("\n") { group ->
                    "  - name: $group\n    type: select\n    proxies:\n" + names.joinToString("\n") { "      - $it" }
                } + "\nrules:\n  - MATCH,DIRECT\n"
            val profileCount = InstrumentationRegistry.getArguments().getString("profileCount")?.toIntOrNull() ?: 1
            for (i in 1 until profileCount) repo.saveProfile("订阅配置 $i", config, source = "https://example.invalid/fixture-$i.yaml")
            repo.saveProfile("代理排布本地验证", config)
            if (InstrumentationRegistry.getArguments().getString("offlinePreview") == "true") {
                withTimeout(10000) { while (!repo.state.value.previewReady) delay(100) }
                check(repo.state.value.phase == VpnPhase.STOPPED)
            } else {
                ContextCompat.startForegroundService(context, Intent(context, ClashVpnService::class.java))
                withTimeout(15000) { while (repo.state.value.phase !in listOf(VpnPhase.RUNNING, VpnPhase.FAILED)) delay(100) }
                check(repo.state.value.phase == VpnPhase.RUNNING) { repo.state.value.error }
            }
            File(context.filesDir, "proxy-qa-ready").writeText("ready")
            if (InstrumentationRegistry.getArguments().getString("benchmarkFlow") == "true") {
                withTimeout(10000) { while (!repo.state.value.proxies.has("AI")) delay(100) }
                delay(1500)
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val device = UiDevice.getInstance(instrumentation)
                fun command(value: String): String = ParcelFileDescriptor.AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand(value)).use { it.readBytes().toString(Charsets.UTF_8) }
                repeat(3) { run ->
                    checkNotNull(device.wait(Until.findObject(By.text("首页")), 5000)).click()
                    delay(900)
                    command("dumpsys gfxinfo ${context.packageName} reset")
                    checkNotNull(device.wait(Until.findObject(By.text("代理")), 5000)).click(); delay(900)
                    checkNotNull(device.wait(Until.findObject(By.text("首页")), 5000)).click(); delay(900)
                    checkNotNull(device.wait(Until.findObject(By.text("代理")), 5000)).click(); delay(900)
                    File(context.getExternalFilesDir(null), "navigation-benchmark-$run.txt")
                        .writeText(command("dumpsys gfxinfo ${context.packageName}"))
                }
            } else {
                withTimeout(900000) { while (!release.exists()) delay(250) }
            }
        } finally {
            context.startService(Intent(context, ClashVpnService::class.java).setAction(ClashVpnService.STOP))
            withTimeout(15000) { while (repo.state.value.phase != VpnPhase.STOPPED) delay(100) }
            repo.restoreBackup(backup); server.shutdown(); scenario.close()
        }
    }
}
