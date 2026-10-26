package cn.nascp.clash

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.data.*
import cn.nascp.clash.ui.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlainTabsTest {
    @get:Rule val ui = createComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    @Test fun profilesSelectAndManageAtLargeFont() {
        val vm = MainViewModel(app)
        val repo = vm.repo
        runBlocking { repo.initialized() }
        val backup = repo.backup()
        var route = ""
        try {
            runBlocking { repo.restoreBackup(JSONObject(backup).put("profiles", JSONArray()).put("selected", "").toString()) }
            runBlocking { repo.saveProfile("Compact profile with a long readable name", ClashRepository.SAMPLE) }
            val first = repo.state.value.selectedId
            runBlocking { repo.saveProfile("Subscription fixture", ClashRepository.SAMPLE, source = "https://example.invalid/sub.yaml") }
            ui.setContent {
                val state by repo.state.collectAsState()
                ClashTheme("plain", "en", 1.5f) { Surface { PlainProfileList(state, vm) { route = it } } }
            }
            ui.onNodeWithTag("profile-select:$first").performScrollTo().performClick()
            ui.waitUntil(5000) { repo.state.value.selectedId == first }
            ui.onNodeWithTag("profile-select:$first").assertIsSelected()
            ui.onNodeWithTag("profile-manage:$first").performClick();ui.onNodeWithTag("profile-details:$first").performClick()
            ui.waitUntil(5000) { route == "profile" }
            ui.onNodeWithTag("plain-profiles").performScrollToNode(hasText("Subscription fixture"))
            val second = repo.state.value.profiles.first { it.name == "Subscription fixture" }.id
            ui.onNodeWithTag("profile-select:$second").assertIsNotSelected()
            ui.onNodeWithTag("profile-manage:$second").performScrollTo().performClick();ui.onNodeWithTag("profile-details:$second").performClick()
            ui.waitUntil(5000) { repo.state.value.selectedId == second }
            assertEquals("profile", route)
        } finally { runBlocking { repo.restoreBackup(backup) } }
    }

    @Test fun runningVpnDisablesSwitchingButKeepsActiveManagementAvailable() {
        val vm = MainViewModel(app)
        val repo = vm.repo
        runBlocking { repo.initialized() }
        val backup = repo.backup()
        var route = ""
        try {
            runBlocking {
                repo.restoreBackup(JSONObject(backup).put("profiles", JSONArray()).put("selected", "").toString())
                repo.saveProfile("Other profile", ClashRepository.SAMPLE)
                repo.saveProfile("Active profile", ClashRepository.SAMPLE)
            }
            val active = repo.state.value.selectedId
            val other = repo.state.value.profiles.first { it.id != active }.id
            ui.setContent {
                ClashTheme("light", "en") { Surface {
                    PlainProfileList(repo.state.value.copy(phase = VpnPhase.RUNNING, activeId = active), vm) { route = it }
                } }
            }
            ui.onNodeWithTag("profile-select:$other").assertIsNotEnabled()
            ui.onNodeWithTag("profile-select:$active").assertIsNotEnabled().assertIsSelected()
            ui.onNodeWithTag("profile-manage:$other").performClick();ui.onNodeWithTag("profile-details:$other").assertIsNotEnabled();ui.onNodeWithText("Cancel").performClick()
            ui.onNodeWithTag("profile-manage:$active").performClick();ui.onNodeWithTag("profile-details:$active").performClick()
            ui.waitUntil(5000) { route == "profile" }
            assertEquals(active, repo.state.value.selectedId)
        } finally { runBlocking { repo.restoreBackup(backup) } }
    }

    private fun connections(vararg hosts: String) = JSONArray().also { array ->
        hosts.forEachIndexed { index, host -> array.put(JSONObject().put("id", "connection-$index")
            .put("metadata", JSONObject().put("host", host).put("network", "tcp"))
            .put("download", 4096).put("upload", 1024)) }
    }

    @Test fun searchDetailsAndCloseConfirmationRemainUsable() {
        val vm = MainViewModel(app)
        runBlocking { vm.repo.initialized() }
        val before = vm.repo.draft("connection")
        try {
            var route = ""
            val state = AppState(phase = VpnPhase.RUNNING, connections = connections("alpha.example", "beta.example"))
            ui.setContent { ClashTheme("plain", "en", 1.5f) { Surface { PlainActivityPage(state, vm) { route = it } } } }
            ui.waitUntil(5000) { ui.onAllNodesWithText("alpha.example").fetchSemanticsNodes().isNotEmpty() }
            ui.onNode(hasSetTextAction()).performTextInput("alpha")
            ui.onNodeWithText("beta.example").assertDoesNotExist()
            ui.onNodeWithText("Close connection").performClick()
            ui.onNodeWithText("取消").assertDoesNotExist()
            ui.onNodeWithText("Cancel").performScrollTo().performClick()
            ui.onNodeWithText("alpha.example").performScrollTo().performClick()
            assertEquals("connection", route)
            assertEquals("alpha.example", JSONObject(vm.repo.draft("connection")).getJSONObject("metadata").getString("host"))
            ui.onNode(hasSetTextAction()).performScrollTo().performTextReplacement("missing")
            ui.onNodeWithText("No matching connections").assertExists()
            ui.onNodeWithText("Close connection").assertIsNotEnabled()
            ui.onNodeWithContentDescription("Clear search").performClick()
            ui.onNodeWithText("alpha.example").assertExists()
        } finally { vm.repo.saveDraft("connection", before) }
    }

    @Test fun activityTabHasMotionButLiveUpdatesDoNotReplayIt() {
        val vm=MainViewModel(app)
        var state by mutableStateOf(AppState(phase=VpnPhase.RUNNING, logs=listOf("motion fixture")))
        ui.setContent { ClashTheme("light") { Surface { PlainActivityPage(state,vm) {} } } }
        ui.waitForIdle(); ui.mainClock.autoAdvance=false
        try {
            ui.onNodeWithText("日志").performClick()
            ui.mainClock.advanceTimeBy(80);ui.waitForIdle()
            val during=ui.onNodeWithTag("plain-activity").getUnclippedBoundsInRoot().top
            ui.mainClock.advanceTimeBy(400);ui.waitForIdle()
            val after=ui.onNodeWithTag("plain-activity").getUnclippedBoundsInRoot().top
            assertTrue("Tab content must still be moving at 80ms", during > after + 0.5.dp)
            ui.runOnIdle { state=state.copy(logs=listOf("updated fixture")) }
            ui.mainClock.advanceTimeBy(80);ui.waitForIdle()
            assertEquals(after.value,ui.onNodeWithTag("plain-activity").getUnclippedBoundsInRoot().top.value,0.1f)
            ui.onNodeWithText("updated fixture").assertExists()
            ui.onNodeWithText("连接", useUnmergedTree=true).performClick();ui.mainClock.advanceTimeBy(48)
            ui.onNodeWithText("日志", useUnmergedTree=true).performClick();ui.mainClock.advanceTimeBy(400);ui.waitForIdle()
            ui.onNodeWithText("updated fixture").assertExists()
        } finally { ui.mainClock.autoAdvance=true }
    }

    @Test fun pausedLogsKeepSnapshotAndResumeLatestData() {
        val vm = MainViewModel(app)
        runBlocking { vm.repo.initialized() }
        var state by mutableStateOf(AppState(phase = VpnPhase.RUNNING, logs = listOf("old fixture log")))
        ui.setContent { ClashTheme("plain") { Surface { PlainActivityPage(state, vm) {} } } }
        ui.onNodeWithText("日志").performClick()
        ui.onNodeWithText("暂停").performClick()
        ui.runOnIdle { state = state.copy(logs = emptyList()) }
        ui.onNodeWithText("old fixture log").assertExists()
        ui.onNodeWithText("暂无日志").assertDoesNotExist()
        ui.runOnIdle { state = state.copy(logs = listOf("latest fixture log")) }
        ui.onNodeWithText("old fixture log").assertExists()
        ui.onNodeWithText("继续").performClick()
        ui.onNodeWithText("latest fixture log").assertExists()
        ui.onNodeWithText("old fixture log").assertDoesNotExist()
        ui.onNode(hasSetTextAction()).performTextInput("missing")
        ui.onNodeWithText("没有匹配的日志").assertExists()
    }
}
