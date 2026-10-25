package cn.nascp.clash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.nascp.clash.ui.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class DockGlassTest {
    @get:Rule val ui = createComposeRule()

    @Test fun backdropIsBlurredUpdatesLiveAndLeavesControlsSharp() {
        val switched = mutableStateOf(false)
        ui.setContent {
            ClashTheme("plain") {
                val backdrop = rememberDockBackdrop()
                Box(Modifier.fillMaxSize().background(Color.White).testTag("glass-fixture")) {
                    Box(Modifier.fillMaxSize().dockBackdropSource(backdrop, Color.White).drawBehind {
                        val stripe = 8.dp.toPx()
                        val color = if (switched.value) Color(0xFFE030C0) else Color(0xFF00C878)
                        repeat((size.width / stripe).toInt() + 1) {
                            drawRect(if (it % 2 == 0) color else Color.White,
                                Offset(it * stripe, 0f), Size(stripe, size.height))
                        }
                    })
                    Box(Modifier.align(Alignment.BottomCenter)) { BottomDock("home", backdrop) {} }
                }
            }
        }
        ui.waitForIdle()
        fun sample(): Pair<Float, Float> {
            val capture = ui.onNodeWithTag("bottom-dock").captureToImage()
            File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
                if (switched.value) "glass-magenta.png" else "glass-green.png").outputStream().use {
                capture.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            val pixels = capture.toPixelMap()
            val line = (pixels.width / 2 until pixels.width * 9 / 10).map { x ->
                val p = pixels[x, (pixels.height * 0.60f).toInt()]
                p.green - p.red
            }
            val variation = line.zipWithNext().sumOf { (a, b) -> abs(a - b).toDouble() }.toFloat() /
                line.sumOf { abs(it).toDouble() }.toFloat().coerceAtLeast(0.001f)
            return line.average().toFloat() to variation
        }
        val (green, blurredVariation) = sample()
        assertTrue("Real page colour must remain visible through the glass", green > 0.035f)
        val source = ui.onNodeWithTag("glass-fixture").captureToImage().toPixelMap()
        val sharpLine = (source.width / 2 until source.width * 9 / 10).map { x ->
            val p = source[x, 100]; p.green - p.red
        }
        val sharpVariation = sharpLine.zipWithNext().sumOf { (a, b) -> abs(a - b).toDouble() }.toFloat() /
            sharpLine.sumOf { abs(it).toDouble() }.toFloat()
        assertTrue("Blur must soften stripe boundaries, not merely make the panel transparent",
            blurredVariation < sharpVariation * 0.4f)
        // Updating only the source draw must also refresh the sampled Dock background.
        ui.runOnIdle { switched.value = true }
        ui.waitForIdle()
        val (magenta, _) = sample()
        assertTrue("Backdrop must follow page changes rather than retain a stale capture", magenta < -0.035f)
        ui.onNodeWithText("代理").assertHasClickAction().assertHeightIsAtLeast(48.dp)
        val dock = ui.onNodeWithTag("bottom-dock").captureToImage().toPixelMap()
        var dark = 0
        for (y in 0 until dock.height) for (x in 0 until dock.width) {
            val p = dock[x, y]
            if (p.red < 0.2f && p.green < 0.2f && p.blue < 0.2f) dark++
        }
        assertTrue("Foreground icons and text must retain dark, sharp pixels", dark > 100)
    }

    @Test fun contentFadesBeforeDockWhileForegroundStaysSharp() {
        ui.setContent {
            ClashTheme("plain") {
                val backdrop = rememberDockBackdrop()
                Box(Modifier.size(320.dp, 360.dp).testTag("fade-fixture")) {
                    Box(Modifier.fillMaxSize().dockBackdropSource(backdrop, Color.White, fadeColor = Color.White)
                        .drawBehind { drawRect(Color(0xFF00C878)) })
                    Box(Modifier.align(Alignment.BottomCenter)) { BottomDock("home", backdrop) {} }
                }
            }
        }
        ui.waitForIdle()
        val fixture = ui.onNodeWithTag("fade-fixture").getUnclippedBoundsInRoot()
        val dock = ui.onNodeWithTag("dock-surface").getUnclippedBoundsInRoot()
        val capture = ui.onNodeWithTag("fade-fixture").captureToImage()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "fade-fixture.png")
            .outputStream().use { capture.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val pixels = capture.toPixelMap()
        val scale = pixels.height / (fixture.bottom - fixture.top).value
        val top = dock.top.value - fixture.top.value
        fun saturation(y: Float): Float {
            // Outside the capsule: this verifies the surrounding page fade too.
            val pixel = pixels[(2f * scale).toInt(), (y * scale).toInt()]
            return pixel.green - pixel.red
        }
        val clear = saturation(top - 36f)
        val approaching = saturation(top - 10f)
        val behind = saturation(top + 28f)
        println("fade samples: fixture=$fixture dock=$dock image=${pixels.width}x${pixels.height} clear=$clear approaching=$approaching behind=$behind")
        assertTrue("Content above the fade must retain its colour", clear > 0.7f)
        assertTrue("Content must start fading before it reaches the Dock", approaching < clear - 0.1f)
        assertTrue("Content behind the Dock must become substantially lighter", behind < approaching * 0.5f)
        val foreground = ui.onNodeWithTag("bottom-dock").captureToImage().toPixelMap()
        var dark = 0
        for (y in 0 until foreground.height) for (x in 0 until foreground.width) {
            if (foreground[x, y].luminance() < 0.05f) dark++
        }
        assertTrue("The page fade must leave Dock icons and labels sharp", dark > 100)
    }

    @Test fun refractionBendsBackgroundNearEdgeWithoutWarpingTheCenter() {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 33)
        val bending=mutableStateOf(false)
        var density=1f
        ui.setContent {
            val layer=androidx.compose.ui.graphics.rememberGraphicsLayer()
            val refraction=androidx.compose.runtime.remember { DockRefraction(1f) }
            Box(Modifier.size(160.dp,80.dp).testTag("refraction-proof")
                .then(Modifier.drawBehind {
                    density=this.density
                    layer.record(androidx.compose.ui.unit.Density(this.density,this.fontScale),layoutDirection,
                        androidx.compose.ui.unit.IntSize(size.width.toInt(),size.height.toInt())) {
                        // Several transitions inside the edge band avoid relying on uniform zoom
                        // to move a stripe boundary into the measurement window.
                        val stripe=2.dp.toPx()
                        repeat((size.width/stripe).toInt()+1) { index ->
                            drawRect(if(index%2==0)Color(0xFF00C878)else Color.White,
                                Offset(index*stripe,0f),Size(stripe,size.height))
                        }
                    }
                    layer.renderEffect=refraction.update(Offset(40.dp.toPx(),16.dp.toPx()),
                        Size(80.dp.toPx(),48.dp.toPx()),
                        dockLensOutline(Offset(40.dp.toPx(),16.dp.toPx()), Size(80.dp.toPx(),48.dp.toPx()),20.dp.toPx()),
                        Offset.Zero,if(bending.value)4.dp.toPx()else 0f)
                    drawLayer(layer)
                }))
        }
        ui.waitForIdle()
        val before=ui.onNodeWithTag("refraction-proof").captureToImage().toPixelMap()
        ui.runOnIdle { bending.value=true }
        ui.waitForIdle()
        val afterImage=ui.onNodeWithTag("refraction-proof").captureToImage()
        val after=afterImage.toPixelMap()
        fun difference(start:Int,end:Int):Float {
            val y=(40*density).toInt()
            return ((start*density).toInt() until (end*density).toInt()).map { x ->
                abs(before[x,y].red-after[x,y].red)+abs(before[x,y].green-after[x,y].green)+abs(before[x,y].blue-after[x,y].blue)
            }.average().toFloat()/3f
        }
        val edge=difference(43,50); val center=difference(75,85)
        println("Refraction pixel difference: edge=$edge, center=$center")
        assertTrue("The edge must bend actual background samples",edge>0.02f)
        assertTrue("Bending must fade out in the center",center<0.01f)
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "glass-refraction-proof.png").outputStream().use {
            afterImage.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }

    @Test fun refractionContourTracksHorizontallyStretchedOutline() {
        var worst = 0f
        var largest = 0
        for (density in listOf(1f, 2.75f, 4f)) {
            for (stretch in listOf(0f, 0.25f, 0.65f, 1f)) {
                val outline = dockLensOutline(Offset(17f, 23f),
                    Size((80f + 34f * stretch) * density, 48f * density),
                    23f * density)
                val bounds = outline.getBounds()
                assertTrue("Horizontal stretch must preserve top and bottom edges",
                    abs(bounds.top - 23f) < 0.001f && abs(bounds.bottom - (23f + 48f * density)) < 0.001f)
                val contour = dockRefractionContour(outline)
                val radius = 23f * density
                for (i in contour.indices step 2) {
                    val dx = contour[i] - (17f + radius)
                    val dy = contour[i + 1] - (23f + radius)
                    if (dx < 0f && dy < 0f) {
                        assertTrue("Corner radius must stay constant as the selection stretches",
                            abs(kotlin.math.sqrt(dx * dx + dy * dy) - radius) < 0.65f)
                    }
                }
                largest = maxOf(largest, contour.size / 2)
                val normals = dockContourNormals(contour)
                assertTrue("Closed contour normal must not have a seam",
                    normals[0] == normals[normals.size - 2] && normals[1] == normals.last())
                for (i in normals.indices step 2) {
                    val length = kotlin.math.sqrt(normals[i] * normals[i] + normals[i + 1] * normals[i + 1])
                    assertTrue("Interpolated normals must be well defined", abs(length - 1f) < 0.001f)
                    if (i > 0) assertTrue("Adjacent bend directions must remain smooth",
                        normals[i] * normals[i - 2] + normals[i + 1] * normals[i - 1] > 0.9f)
                }
                val measure = android.graphics.PathMeasure(
                    outline.asAndroidPath(), true)
                val position = FloatArray(2)
                repeat(1000) { step ->
                    measure.getPosTan(measure.length * step / 1000f, position, null)
                    var distance = Float.MAX_VALUE
                    for (i in 2 until contour.size step 2) {
                        val ax = contour[i - 2]; val ay = contour[i - 1]
                        val dx = contour[i] - ax; val dy = contour[i + 1] - ay
                        val t = (((position[0] - ax) * dx + (position[1] - ay) * dy) /
                            (dx * dx + dy * dy).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
                        val ex = position[0] - ax - t * dx
                        val ey = position[1] - ay - t * dy
                        distance = minOf(distance, kotlin.math.sqrt(ex * ex + ey * ey))
                    }
                    worst = maxOf(worst, distance)
                }
            }
        }
        println("Contour maximum boundary error=$worst physical pixels; max points=$largest")
        assertTrue("Refraction must track the real boundary at subpixel precision", worst < 0.65f)
        assertTrue("Contour must fit bounded shader uniforms", largest <= DockContourCapacity)
    }

    @Test fun capsuleAndSelectionRemainDistinctOnWhiteAndLightGrey() {
        val background = mutableStateOf(Color.White)
        ui.setContent {
            ClashTheme("plain") {
                val backdrop = rememberDockBackdrop()
                Box(Modifier.fillMaxSize().background(background.value)) {
                    Box(Modifier.fillMaxSize().dockBackdropSource(backdrop, background.value))
                    Box(Modifier.align(Alignment.BottomCenter)) { BottomDock("home", backdrop) {} }
                }
            }
        }
        listOf(Color.White, Color(0xFFF4F4F5)).forEachIndexed { index, color ->
            ui.runOnIdle { background.value = color }
            ui.waitForIdle()
            val image = ui.onNodeWithTag("bottom-dock").captureToImage()
            File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
                "glass-contrast-$index.png").outputStream().use {
                image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            val pixels = image.toPixelMap()
            val surfaceCapture = ui.onNodeWithTag("dock-surface").captureToImage()
            File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
                "glass-surface-$index.png").outputStream().use {
                surfaceCapture.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            val surface = surfaceCapture.toPixelMap()
            val centerX = surface.width / 2
            val rim = surface[centerX, 1].luminance()
            val shoulder = surface[centerX, (surface.height * 0.05f).toInt()].luminance()
            assertTrue("The white upper rim must separate from the local frosted edge on pale content",
                rim - shoulder > 0.07f)
            val y = (pixels.height * 0.60f).toInt()
            // Gap between icon and label avoids measuring foreground ink.
            val body = pixels[pixels.width / 2, y].luminance()
            val selected = pixels[pixels.width / 10, y].luminance()
            if (index == 0) {
                assertTrue("Edge frosting must leave the white-backed middle bright", body > 0.96f)
                assertTrue("The shaded edge must recover into a bright interior", body - shoulder > 0.06f)
            }
            println("glass contrast: background=${color.luminance()}, body=$body, selected=$selected")
            // Frost can share a grey page's tone; its upper rim-to-shoulder contrast
            // above establishes separation on both white and grey backgrounds.
            assertTrue("Selection must remain distinguishable from the glass body", body - selected > 0.045f)
        }
    }


    @Test fun largerLabelsFitEnglishAt150PercentOnNarrowScreens() {
        ui.setContent {
            ClashTheme("plain", language = "en", fontScale = 1.5f) {
                Box(Modifier.width(320.dp)) { BottomDock("settings") {} }
            }
        }
        ui.waitForIdle()
        val capture = ui.onNodeWithTag("bottom-dock").captureToImage()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "dock-large-en.png").outputStream().use {
            capture.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        listOf("Home", "Proxies", "Profiles", "Activity", "Settings").forEach { label ->
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            ui.onNodeWithText(label).assertIsDisplayed().assertHasClickAction()
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            layouts.forEach { println("dock label $label: size=${it.size}, font=${it.layoutInput.style.fontSize}, lines=${it.lineCount}, heightOverflow=${it.didOverflowHeight}, widthOverflow=${it.didOverflowWidth}") }
            assertTrue("$label must remain readable without clipping at large font size",
                layouts.size == 1 && !layouts.single().hasVisualOverflow)
        }
        ui.onNodeWithText("Settings").assertIsSelected()
    }

}
