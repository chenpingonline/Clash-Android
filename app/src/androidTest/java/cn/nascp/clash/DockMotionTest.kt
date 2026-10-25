package cn.nascp.clash

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import cn.nascp.clash.ui.BottomDock
import cn.nascp.clash.ui.ClashTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DockMotionTest {
    @get:Rule val ui = createComposeRule()

    @Test fun indicatorSlidesAndRetargetsDuringRapidSelection() {
        val route = mutableStateOf("home")
        ui.setContent {
            ClashTheme("plain") {
                BottomDock(route.value) { route.value = it }
            }
        }
        ui.waitForIdle()
        ui.mainClock.autoAdvance = false
        var frame = 0
        var measuredWidth = 0f
        fun center(): Float {
            val bitmap = ui.onNodeWithTag("bottom-dock").captureToImage()
            File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "dock-${frame++}.png").outputStream().use {
                bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            val pixels = bitmap.toPixelMap()
            // Sample the empty gap between icon and label. Keep the longest grey run,
            // excluding the small antialiased corners of the outer capsule.
            val y = (pixels.height * 0.60f).toInt()
            val bodyLuminance=(0 until pixels.width).map{pixels[it,y].luminance()}.sorted()[pixels.width/2]
            var runStart = 0
            var runLength = 0
            var left = 0
            var width = 0
            for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                val body = pixel.luminance() < bodyLuminance-0.05f
                if (body) {
                    if (runLength == 0) runStart = x
                    runLength++
                    if (runLength > width) { left = runStart; width = runLength }
                } else runLength = 0
            }
            println("dock frame ${frame-1}: ${pixels.width}x${pixels.height}, lensWidth=$width")
            assertTrue("The moving indicator must be visible", width > 40)
            // Normalize lens geometry for different test-device sizes.
            measuredWidth = width * 1000f / pixels.width
            return (left + width / 2f) * 1000f / pixels.width
        }
        // Drive selection state without a press ripple obscuring the measured indicator color.
        val start = center()
        val restingWidth = measuredWidth
        ui.runOnIdle { route.value = "proxies" }
        ui.mainClock.advanceTimeBy(80)
        center()
        assertTrue("Adjacent selection must stretch horizontally while travelling", measuredWidth > restingWidth * 1.08f)
        ui.mainClock.advanceTimeBy(500)
        center()
        assertTrue("Lens must gather back to its resting width", kotlin.math.abs(measuredWidth - restingWidth) < 4)
        ui.runOnIdle { route.value = "home" }
        ui.mainClock.advanceTimeBy(80)
        center()
        assertTrue("Reverse selection must stretch too", measuredWidth > restingWidth * 1.08f)
        ui.mainClock.advanceTimeBy(500)
        ui.runOnIdle { route.value = "settings" }
        ui.mainClock.advanceTimeBy(96)
        val mid = center()
        ui.onNodeWithText("设置").assertIsSelected()
        ui.mainClock.advanceTimeBy(320)
        val end = center()
        assertTrue("Indicator must pass through an intermediate position", mid > start + 5 && mid < end - 5)

        ui.runOnIdle { route.value = "home" }
        ui.mainClock.advanceTimeBy(96)
        val reverse = center()
        assertTrue("Indicator must move back from settings", reverse > start && reverse < end)
        ui.runOnIdle { route.value = "proxies" }
        ui.mainClock.advanceTimeBy(320)
        val redirected = center()
        assertTrue("Interrupted animation must finish at the new tab", redirected > start && redirected < end)
        assertTrue("Proxy target must be one quarter of the home-to-settings distance",
            kotlin.math.abs(redirected - (start + (end - start) / 4)) < 5)
        ui.onNodeWithText("代理").assertIsSelected()
    }

    @Test fun endpointsAndMiddleHaveTheSameTravelAndSettleWithoutPulsing() {
        val route = mutableStateOf("home")
        val direction = mutableStateOf(androidx.compose.ui.unit.LayoutDirection.Ltr)
        ui.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides direction.value) {
                ClashTheme("plain") { BottomDock(route.value) { route.value = it } }
            }
        }
        ui.waitForIdle()
        ui.mainClock.autoAdvance = false
        fun width(): Float {
            val image = ui.onNodeWithTag("bottom-dock").captureToImage().toPixelMap()
            val y = (image.height * 0.60f).toInt()
            val bodyLuminance=(0 until image.width).map{image[it,y].luminance()}.sorted()[image.width/2]
            var run = 0; var longest = 0
            for (x in 0 until image.width) {
                if (image[x, y].luminance() < bodyLuminance-0.05f) { run++; longest = maxOf(longest, run) }
                else run = 0
            }
            assertTrue("Lens must be rendered", longest > 40)
            return longest.toFloat() / image.width
        }
        for (layout in listOf(androidx.compose.ui.unit.LayoutDirection.Ltr, androidx.compose.ui.unit.LayoutDirection.Rtl)) {
            ui.runOnIdle { direction.value = layout }
            ui.mainClock.advanceTimeBy(700)
            for (tab in listOf("proxies", "profiles", "activity", "settings", "activity", "profiles", "proxies", "home")) {
                val resting = width()
                ui.runOnIdle { route.value = tab }
                ui.mainClock.advanceTimeBy(80)
                val travelling = width()
                assertTrue("$layout/$tab must show bounded leading/trailing edge separation", travelling > resting * 1.035f)
                assertTrue("$layout/$tab must not inflate into a wide secondary pulse", travelling < resting * 1.47f)
                ui.mainClock.advanceTimeBy(420)
                val landed = width()
                ui.mainClock.advanceTimeBy(300)
                assertTrue("$layout/$tab must not stretch again after landing", kotlin.math.abs(width() - landed) < 0.003f)
                assertTrue("$layout/$tab must settle at the standard lens width", kotlin.math.abs(landed - resting) < 0.006f)
            }
        }
    }

    @Test fun wholeBarAndAllTabCellsKeepTheirBoundsAcrossEveryTabSwitch() {
        val route = mutableStateOf("home")
        ui.setContent { ClashTheme("plain") { BottomDock(route.value) { route.value = it } } }
        ui.waitForIdle()
        ui.mainClock.autoAdvance = false
        val labels = listOf("首页", "代理", "配置", "活动", "设置")
        val bar = ui.onNodeWithTag("bottom-dock").getUnclippedBoundsInRoot()
        val cells = labels.map { ui.onNodeWithText(it).getUnclippedBoundsInRoot() }
        for (tab in listOf("proxies", "profiles", "activity", "settings", "activity", "profiles", "proxies", "home")) {
            ui.runOnIdle { route.value = tab }
            for (ms in listOf(16L, 64L, 240L, 400L)) {
                ui.mainClock.advanceTimeBy(ms)
                org.junit.Assert.assertEquals("Outer Dock must not resize or shift at $tab/$ms", bar,
                    ui.onNodeWithTag("bottom-dock").getUnclippedBoundsInRoot())
                labels.forEachIndexed { index, label ->
                    org.junit.Assert.assertEquals("Tab $label must not shift at $tab/$ms", cells[index],
                        ui.onNodeWithText(label).getUnclippedBoundsInRoot())
                }
            }
        }
    }

}
