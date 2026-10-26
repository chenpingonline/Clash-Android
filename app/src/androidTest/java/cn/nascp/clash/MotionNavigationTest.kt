package cn.nascp.clash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.data.ClashRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import org.junit.Assert.assertTrue

class MotionNavigationTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun click(text: String) = ui.onAllNodesWithText(text).onLast().performClick()
    private fun settle() { ui.waitForIdle(); ui.mainClock.advanceTimeBy(650); ui.waitForIdle() }
    @Test fun edgeBackKeepsPageStationaryUntilReleasedAndCanCancel() {
        val repo = ClashRepository.get(ui.activity)
        runBlocking { repo.initialized() }
        val before = repo.backup()
        fun event(progress: Float, edge: Int = androidx.activity.BackEventCompat.EDGE_LEFT) = androidx.activity.BackEventCompat(
            500f * progress, 800f, progress, edge)
        fun capture(name: String) {
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(ui.activity.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        try {
            repo.theme("light"); repo.language("zh"); repo.fontScale(1f); repo.clearMessage()
            ui.waitForIdle(); click("设置"); ui.waitForIdle()
            ui.mainClock.autoAdvance = false
            ui.onNodeWithText("VPN 设置").performClick()
            ui.mainClock.advanceTimeBy(96); ui.waitForIdle()
            val entryToolbar = ui.onNodeWithTag("route:vpn").getUnclippedBoundsInRoot().left
            assertTrue("The entering page must remain a visible full panel", entryToolbar > 100.dp)
            capture("page-entry-panel.png")
            settle()
            val toolbarLeft = ui.onNodeWithTag("route:vpn").getUnclippedBoundsInRoot().left
            val contentLeft = ui.onNodeWithText("接管 IPv6").getUnclippedBoundsInRoot().left
            ui.mainClock.autoAdvance = false
            ui.runOnUiThread { ui.activity.onBackPressedDispatcher.dispatchOnBackStarted(event(0f)) }
            ui.mainClock.advanceTimeBy(32)
            ui.runOnUiThread { ui.activity.onBackPressedDispatcher.dispatchOnBackProgressed(event(0.5f)) }
            ui.mainClock.advanceTimeBy(48); ui.waitForIdle()
            val toolbarDelta = ui.onNodeWithTag("route:vpn").getUnclippedBoundsInRoot().left - toolbarLeft
            val contentDelta = ui.onNodeWithText("接管 IPv6").getUnclippedBoundsInRoot().left - contentLeft
            assertTrue("Held back gesture must not move the toolbar", kotlin.math.abs(toolbarDelta.value) < 1f)
            assertTrue("Held back gesture must not move content", kotlin.math.abs(contentDelta.value) < 1f)
            capture("back-gesture-held.png")
            ui.runOnUiThread { ui.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
            settle()
            ui.onNodeWithTag("route:vpn").assertExists()
            assertTrue("Cancelled gesture must return to origin", ui.onNodeWithTag("route:vpn").getUnclippedBoundsInRoot().left == toolbarLeft)
            ui.runOnUiThread { ui.activity.onBackPressedDispatcher.dispatchOnBackStarted(event(0f)) }
            ui.mainClock.advanceTimeBy(32)
            ui.runOnUiThread { ui.activity.onBackPressedDispatcher.dispatchOnBackProgressed(event(0.6f)) }
            ui.mainClock.advanceTimeBy(48)
            ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
            settle()
            ui.onNodeWithTag("route:settings").assertExists()
            ui.onNodeWithTag("route:vpn").assertDoesNotExist()
            ui.onNodeWithText("外观与语言").assertIsDisplayed()
            capture("back-gesture-complete.png")
        } finally {
            ui.mainClock.autoAdvance = true
            runBlocking { repo.restoreBackup(before) }
        }
    }
    @Test fun interruptedNavigationAndBackRemainConsistent() {
        val repo = ClashRepository.get(ui.activity)
        runBlocking { repo.initialized() }
        val before = repo.backup()
        try {
            repo.theme("plain"); repo.language("zh"); repo.fontScale(1f); repo.clearMessage()
            ui.waitForIdle()
            ui.mainClock.autoAdvance = false
            click("设置"); ui.mainClock.advanceTimeBy(96); ui.waitForIdle()
            val incomingLeft = ui.onNodeWithText("外观与语言",useUnmergedTree=true).getUnclippedBoundsInRoot().left
            val intermediate = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(ui.activity.getExternalFilesDir(null), "tab-motion-intermediate.png").outputStream().use {
                intermediate.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }; intermediate.recycle()
            settle()
            val settledLeft = ui.onNodeWithText("外观与语言",useUnmergedTree=true).getUnclippedBoundsInRoot().left
            assertTrue("Incoming page must still be travelling horizontally at 96ms", incomingLeft > settledLeft + 24.dp)
            click("首页"); settle()
            click("代理"); ui.mainClock.advanceTimeBy(48)
            click("配置"); ui.mainClock.advanceTimeBy(48)
            click("设置"); settle()
            ui.onAllNodesWithText("设置").onLast().assertIsSelected()
            ui.onNodeWithText("VPN 设置").performClick()
            ui.waitForIdle(); ui.mainClock.advanceTimeBy(80); ui.waitForIdle()
            val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val file = File(ui.activity.getExternalFilesDir(null), "motion-intermediate.png")
            file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
            // Interrupt entry with Back before the transition completes.
            ui.onNodeWithContentDescription("返回").performClick(); settle()
            ui.onNodeWithText("外观与语言").assertIsDisplayed()
            ui.onNodeWithContentDescription("返回").assertDoesNotExist()
            ui.onNodeWithText("VPN 设置").performClick(); settle()
            ui.onNodeWithText("接管 IPv6").assertIsDisplayed()
            ui.onNodeWithContentDescription("返回").performClick(); settle()
            repo.theme("dark"); ui.mainClock.advanceTimeBy(80)
            repo.theme("light"); settle()
            click("首页"); settle()
            ui.onNodeWithText("当前配置").assertIsDisplayed()
        } finally {
            ui.mainClock.autoAdvance = true
            runBlocking { repo.restoreBackup(before) }
        }
    }
    @Test fun incomingTabKeepsItsOwnPixelsDuringTransition() {
        val repo = ClashRepository.get(ui.activity)
        runBlocking { repo.initialized() }
        val before = repo.backup()
        fun iconPixel(): androidx.compose.ui.graphics.Color {
            val bounds = ui.onNodeWithText("外观与语言",useUnmergedTree=true).getUnclippedBoundsInRoot()
            val density = ui.activity.resources.displayMetrics.density
            // Sample the solid left edge of the orange icon, outside its white glyph.
            val x = ((bounds.left.value - 38f) * density).toInt()
            val y = (((bounds.top.value + bounds.bottom.value) / 2f) * density).toInt()
            val image = ui.onRoot().captureToImage()
            File(ui.activity.getExternalFilesDir(null),"tab-pixel-probe.png").outputStream().use {
                image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            return image.toPixelMap()[x,y]
        }
        try {
            repo.theme("light"); repo.language("zh"); repo.fontScale(1f); repo.clearMessage()
            ui.waitForIdle(); click("设置"); settle()
            val expected = iconPixel()
            assertTrue("Sample must identify the orange settings icon: $expected", expected.red > .8f && expected.green > .4f && expected.blue < .2f)
            ui.mainClock.autoAdvance = false
            repeat(3) {
                click("首页"); settle()
                click("设置"); ui.mainClock.advanceTimeBy(160); ui.waitForIdle()
                val actual = iconPixel()
                assertTrue("Incoming tab must paint its own icon, not the outgoing page's cached pixels: $actual vs $expected",
                    kotlin.math.abs(actual.red-expected.red)<.08f && kotlin.math.abs(actual.green-expected.green)<.08f && kotlin.math.abs(actual.blue-expected.blue)<.08f)
                settle()
            }
        } finally {
            ui.mainClock.autoAdvance = true
            runBlocking { repo.restoreBackup(before) }
        }
    }

}
