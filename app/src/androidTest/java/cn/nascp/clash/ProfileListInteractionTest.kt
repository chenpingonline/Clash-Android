package cn.nascp.clash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.data.ClashRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class ProfileListInteractionTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        ui.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = File(ui.activity.getExternalFilesDir(null), "profile-list-qa/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun bodySelectsAndArrowOpensDetails() {
        val repo = ClashRepository.get(ui.activity)
        runBlocking { repo.initialized() }
        val backup = repo.backup()
        val release = File(ui.activity.filesDir, "profile-list-qa-release")
        release.delete()
        try {
            repo.theme("light"); repo.language("zh"); repo.fontScale(1f)
            runBlocking {
                repo.restoreBackup(JSONObject(repo.backup()).put("profiles", JSONArray()).put("selected", "").toString())
                repo.saveProfile("日常订阅", ClashRepository.SAMPLE, source = "https://example.invalid/daily.yaml")
                repo.saveProfile("备用订阅", ClashRepository.SAMPLE, source = "https://example.invalid/backup.yaml")
                repo.saveProfile("本地配置", ClashRepository.SAMPLE)
            }
            val first = repo.state.value.profiles.first().id
            val second = repo.state.value.profiles[1].id
            repo.selectProfile(first)
            ui.onAllNodesWithText("配置").onLast().performClick()
            ui.onNodeWithTag("profile-select:$first").assertIsSelected()
            ui.onNodeWithTag("profile-select:$second").performClick()
            ui.waitUntil(5000) { repo.state.value.selectedId == second }
            ui.onNodeWithTag("route:profiles").assertExists()
            ui.onNodeWithTag("profile-select:$second").assertIsSelected()
            ui.onNodeWithTag("profile-manage:$second").performClick();ui.onNodeWithTag("profile-details:$second").performClick()
            ui.onNodeWithTag("route:profile").assertExists()
            assertEquals(second, repo.state.value.selectedId)
            ui.onNodeWithContentDescription("返回").performClick()
            ui.onNodeWithTag("profile-select:$first").performClick()
            ui.waitUntil(5000) { repo.state.value.selectedId == first }
            capture("light")
            ui.onNodeWithTag("profile-select:$first").performTouchInput{down(center)}
            capture("pressed-selected")
            ui.onNodeWithTag("profile-select:$first").performTouchInput{cancel()}
            ui.onNodeWithTag("profile-select:$second").performTouchInput{down(center)}
            capture("pressed-unselected")
            ui.onNodeWithTag("profile-select:$second").performTouchInput{cancel()}
            assertEquals(first,repo.state.value.selectedId)
            repo.theme("dark"); ui.waitForIdle(); capture("dark")
            repo.theme("light"); repo.fontScale(1.5f); ui.waitForIdle(); capture("large-font")
            repo.fontScale(1f); ui.waitForIdle()
            if (InstrumentationRegistry.getArguments().getString("preview") == "true") {
                File(ui.activity.filesDir, "profile-list-qa-ready").writeText("ready")
                val end = System.currentTimeMillis() + 900000
                while (!release.exists() && System.currentTimeMillis() < end) { ui.waitForIdle(); Thread.sleep(250) }
            }
        } finally {
            repo.clearMessage()
            runBlocking { repo.restoreBackup(backup) }
            release.delete()
            File(ui.activity.filesDir, "profile-list-qa-ready").delete()
        }
    }
}
