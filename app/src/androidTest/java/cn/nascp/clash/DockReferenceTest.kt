package cn.nascp.clash

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.ui.*
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Same native material over the reference photo, at its measured proportions.
 * The hidden photo row is reconstructed from the unobscured thumbnail supplied by
 * the user; it is a material comparison, not a claim of identical source pixels. */
class DockReferenceTest {
    @get:Rule val ui = createComposeRule()
    @Test fun referenceScene() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.context.assets.open("dock-reference-background.png").use {
            BitmapFactory.decodeStream(it).asImageBitmap()
        }
        val scale = 390f / 833f
        ui.setContent {
            ClashTheme("plain") {
                val backdrop = rememberDockBackdrop()
                val glass = rememberDockGlass(backdrop, refractive = false)
                Box(Modifier.size(390.dp, (610 * scale).dp).testTag("reference-scene")) {
                    Image(bitmap, null, Modifier.fillMaxSize().dockBackdropSource(backdrop, Color.White, Color.White),
                        contentScale = ContentScale.FillBounds)
                    Box(Modifier.offset((177 * scale).dp, (450 * scale).dp)
                        .size((367 * scale).dp, (116 * scale).dp)
                        .dockGlassHalo(27.dp, true).shadow(12.dp, RoundedCornerShape(27.dp), ambientColor = Color.Black.copy(alpha=.12f), spotColor = Color.Black.copy(alpha=.14f))
                        .clip(RoundedCornerShape(27.dp)).dockGlassSurface(glass, Color.White, 27.dp)) {
                        Box(Modifier.padding(3.dp).width((188 * scale).dp).fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(alpha=.065f)))
                        Row(Modifier.fillMaxSize().padding(3.dp)) {
                            listOf(Icons.Filled.WatchLater to "最近", Icons.Filled.Folder to "浏览").forEach { (icon, label) ->
                                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center) {
                                    Icon(icon, null, Modifier.size(23.dp), tint=Color.Black)
                                    Text(label, color=Color.Black, fontSize=12.sp, lineHeight=17.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        ui.waitForIdle()
        val capture=ui.onNodeWithTag("reference-scene").captureToImage()
        File(instrumentation.targetContext.getExternalFilesDir(null),"reference-native.png").outputStream().use {
            capture.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
}
