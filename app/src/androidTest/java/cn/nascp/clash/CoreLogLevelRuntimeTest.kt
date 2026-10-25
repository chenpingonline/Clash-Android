package cn.nascp.clash

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.data.*
import cn.nascp.clash.vpn.ClashVpnService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CoreLogLevelRuntimeTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun settingReadsActualCoreAndResetsOnRestart() {
        val repo=ClashRepository.get(ui.activity)
        runBlocking{repo.initialized()};val before=repo.backup()
        fun start() {
            ContextCompat.startForegroundService(ui.activity,Intent(ui.activity,ClashVpnService::class.java))
            ui.waitUntil(15000){repo.state.value.phase in listOf(VpnPhase.RUNNING,VpnPhase.FAILED)}
            assertEquals(repo.state.value.error,VpnPhase.RUNNING,repo.state.value.phase)
        }
        fun stop() {
            ui.activity.startService(Intent(ui.activity,ClashVpnService::class.java).setAction(ClashVpnService.STOP))
            ui.waitUntil(15000){repo.state.value.phase==VpnPhase.STOPPED}
        }
        try {
            repo.theme("light");repo.language("zh");repo.clearMessage()
            runBlocking{repo.saveProfile("Log level fixture",ClashRepository.SAMPLE.replace("log-level: info","log-level: error"))}
            ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("appops set ${ui.activity.packageName} ACTIVATE_VPN allow")).use{it.readBytes()}
            ui.onAllNodesWithText("设置").onLast().performClick()
            ui.onNodeWithText("内核管理").performScrollTo().performClick()
            ui.onNodeWithTag("log-level-setting").performScrollTo().assertIsNotEnabled()
            start()
            ui.waitUntil(5000){ui.onAllNodesWithText("错误 (error)").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("log-level-setting").performScrollTo().performClick()
            ui.onNodeWithTag("log-level-option:debug").performClick()
            ui.waitUntil(5000){runBlocking{repo.api("/configs")}.optString("log-level")=="debug"}
            ui.onNodeWithText("调试 (debug)").assertExists()
            stop();ui.onNodeWithText("未运行").assertExists()
            start()
            ui.waitUntil(5000){ui.onAllNodesWithText("错误 (error)").fetchSemanticsNodes().isNotEmpty()}
        } finally { stop();repo.clearMessage();runBlocking{repo.restoreBackup(before)} }
    }
}
