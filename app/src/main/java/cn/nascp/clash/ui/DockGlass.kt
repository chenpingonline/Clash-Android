package cn.nascp.clash.ui

import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal val DockHorizontalPadding = 4.dp
internal val DockVerticalPadding = 5.dp

/** Only page content is recorded, so sampling it can never include the Dock itself. */
@Stable internal class DockBackdrop(val layer: GraphicsLayer, val ambientLayer: GraphicsLayer, val maskLayer: GraphicsLayer) {
    var ambientRadius = 0f
    var origin by mutableStateOf(Offset.Zero)
    var dockTop by mutableFloatStateOf(Float.NaN)
}

@Composable internal fun rememberDockBackdrop(): DockBackdrop {
    val layer = rememberGraphicsLayer()
    val ambientLayer = rememberGraphicsLayer()
    val maskLayer = rememberGraphicsLayer()
    return remember(layer, ambientLayer, maskLayer) { DockBackdrop(layer, ambientLayer, maskLayer) }
}

internal fun Modifier.dockBackdropSource(backdrop: DockBackdrop, background: Color, fadeColor: Color? = null): Modifier =
    onGloballyPositioned { backdrop.origin = it.positionInRoot() }.drawWithContent {
        val top = backdrop.dockTop - backdrop.origin.y
        val fadeStart = (top - 28.dp.toPx()).coerceAtLeast(0f)
        val fadeEnd = top + 56.dp.toPx()
        backdrop.layer.record {
            drawRect(background)
            this@drawWithContent.drawContent()
            // Soften page content before it passes under the floating Dock. Recording
            // the veil here also gives the glass the same already-softened backdrop.
            if (fadeColor != null && top.isFinite()) {
                drawRect(Brush.verticalGradient(
                    0f to fadeColor.copy(alpha = 0f),
                    0.35f to fadeColor.copy(alpha = 0.42f),
                    0.72f to fadeColor.copy(alpha = 0.86f),
                    1f to fadeColor.copy(alpha = 0.96f),
                    startY = fadeStart, endY = fadeEnd),
                    topLeft = Offset(0f, fadeStart), size = Size(size.width, (size.height - fadeStart).coerceAtLeast(0f)))
            }
        }
        drawLayer(backdrop.layer)
        if (fadeColor != null && top.isFinite() && Build.VERSION.SDK_INT >= 31) {
            // The reference softens page pixels outside the capsule as well. Blend
            // a blurred lower strip into the page, leaving the Dock foreground sharp.
            val blurRadius = 12.dp.toPx()
            val margin = blurRadius * 3
            val transition = 40.dp.toPx()
            val stripTop = (top - transition).coerceIn(0f, size.height)
            val stripSize = IntSize((size.width + margin * 2).roundToInt(),
                (size.height - stripTop + margin * 2).roundToInt())
            val densitySnapshot = androidx.compose.ui.unit.Density(density, fontScale)
            if (backdrop.ambientRadius != blurRadius) {
                backdrop.ambientRadius = blurRadius
                backdrop.ambientLayer.renderEffect = BlurEffect(blurRadius, blurRadius, TileMode.Clamp)
            }
            backdrop.ambientLayer.record(densitySnapshot, layoutDirection, stripSize) {
                translate(margin, margin - stripTop) { drawLayer(backdrop.layer) }
            }
            backdrop.maskLayer.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
            backdrop.maskLayer.record(densitySnapshot, layoutDirection, stripSize) {
                drawLayer(backdrop.ambientLayer)
                drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.White,
                    startY = margin, endY = margin + transition), blendMode = BlendMode.DstIn)
            }
            translate(-margin, stripTop - margin) { drawLayer(backdrop.maskLayer) }
        }
    }

@Stable internal class DockGlass(val layer: GraphicsLayer, val lensLayer: GraphicsLayer, val backdrop: DockBackdrop?, val refractive: Boolean) {
    var refraction: DockRefraction? = null
    var origin by mutableStateOf(Offset.Zero)
    // Written by the parent's draw before the lens is drawn, with no snapshot invalidation loop.
    var ready = false
    var margin = 0f
    var blurRadius = 0f
}

@Composable internal fun rememberDockGlass(backdrop: DockBackdrop?, refractive: Boolean = true): DockGlass {
    val layer = rememberGraphicsLayer()
    val lensLayer = rememberGraphicsLayer()
    return remember(layer, lensLayer, backdrop, refractive) { DockGlass(layer, lensLayer, backdrop, refractive) }
}

internal fun Modifier.dockGlassSurface(glass: DockGlass, tint: Color, cornerRadius: Dp): Modifier =
    onGloballyPositioned {
        glass.origin = it.positionInRoot()
        glass.backdrop?.dockTop = glass.origin.y
    }.drawWithContent {
        val source = glass.backdrop
        glass.ready = Build.VERSION.SDK_INT >= 31 && source != null && source.layer.size.width > 0
        if (glass.ready && source != null) {
            // Capture a small overscanned strip, not a blurred full-screen texture. Overscan
            // supplies real neighbours for the blur instead of dark/transparent capsule edges.
            // Supply three blur radii of real page pixels around the capsule.
            val margin = 84.dp.toPx()
            glass.margin = margin
            val relative = glass.origin - source.origin
            val blurRadius = 28.dp.toPx()
            if (glass.blurRadius != blurRadius) {
                glass.blurRadius = blurRadius
                glass.layer.renderEffect = BlurEffect(blurRadius, blurRadius, TileMode.Clamp)
            }
            glass.layer.record(androidx.compose.ui.unit.Density(density,fontScale), layoutDirection,
                IntSize((size.width + margin * 2).roundToInt(), (size.height + margin * 2).roundToInt())) {
                translate(margin - relative.x, margin - relative.y) { drawLayer(source.layer) }
            }
            // Frosted selection shares the base; only refractive themes need a second capture.
            if (glass.refractive) {
                // The lens uses a less blurred copy so its edge can bend real background details.
                if (Build.VERSION.SDK_INT >= 33) {
                    if (glass.refraction == null) glass.refraction = DockRefraction(3.dp.toPx())
                } else {
                    glass.lensLayer.renderEffect = BlurEffect(5.dp.toPx(), 5.dp.toPx(), TileMode.Clamp)
                }
                glass.lensLayer.record(androidx.compose.ui.unit.Density(density,fontScale), layoutDirection, glass.layer.size) {
                    translate(margin - relative.x, margin - relative.y) { drawLayer(source.layer) }
                }
            }
            translate(-margin, -margin) { drawLayer(glass.layer) }
        }
        val light = tint.luminance() > 0.5f
        // A broad blur supplies the frost; the neutral veil preserves diffuse page
        // colour without making the capsule look like an opaque white card.
        drawRect(tint.copy(alpha = if (glass.ready) { if(light) 0.64f else 0.72f } else 0.96f))
        drawRect(Color.Black.copy(alpha = if (light) 0.012f else 0.025f))
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (light) 0.12f else 0.035f),
            Color.White.copy(alpha = 0.025f), tint.copy(alpha = 0.08f))))
        drawContent()
        val radius = cornerRadius.toPx()
        // One white perimeter, with a broad low-contrast frosted shoulder inside.
        // Separation comes from the ambient shadow, not a second grey outline.
        val stroke = (if (light) 1.dp else 0.8.dp).toPx()
        val inset = stroke / 2
        val rim = Brush.verticalGradient(listOf(
            Color.White.copy(alpha = if (light) 0.90f else 0.18f),
            Color.White.copy(alpha = if (light) 0.76f else 0.04f),
            if (light) Color.White.copy(alpha = 0.82f) else Color.Black.copy(alpha = 0.18f)),
            startY = 0f, endY = size.height)
        if (light) {
            val shoulder = 3.dp.toPx()
            drawRoundRect(Color.White.copy(alpha = 0.12f),
                Offset(shoulder / 2, shoulder / 2), Size(size.width - shoulder, size.height - shoulder),
                CornerRadius((radius - shoulder / 2).coerceAtLeast(0f)), style = Stroke(shoulder))
        }
        drawRoundRect(rim, Offset(inset, inset), Size(size.width - stroke, size.height - stroke),
            CornerRadius((radius - inset).coerceAtLeast(0f)), style = Stroke(stroke))
        // A small upper-left catchlight, rather than a uniformly bright outline.
        drawRoundRect(Brush.radialGradient(listOf(Color.White.copy(alpha=if(light)0.12f else 0.16f),
            Color.Transparent),Offset(size.width*0.18f,0f),size.height*2.2f),
            Offset(inset,inset),Size(size.width-stroke,size.height-stroke),
            CornerRadius(cornerRadius.toPx()),style=Stroke(1.25.dp.toPx()))
    }

/** Smooth material response with zero slope at both endpoints, tied to geometry. */
internal fun dockGlassStretch(stretch: Float): Float {
    val s = stretch.coerceIn(0f, 1f)
    return s * s * (3f - 2f * s)
}

/** Bend real page details near the perimeter, inside exactly the animated contour. */
internal fun DrawScope.drawDockLensBackdrop(glass: DockGlass, origin: Offset, lensSize: Size, outline: Path, stretch: Float = 0f) {
    if (!glass.ready) return
    val offset=Offset(glass.margin+DockHorizontalPadding.toPx(),glass.margin+DockVerticalPadding.toPx())
    if (Build.VERSION.SDK_INT >= 33) {
        glass.lensLayer.renderEffect = glass.refraction!!.update(origin+offset,lensSize,outline,offset,(3.0f - 0.5f * dockGlassStretch(stretch)).dp.toPx())
    }
    clipPath(outline) {
        val zoom=if(Build.VERSION.SDK_INT>=33)1f else 1.09f
        scale(zoom,zoom,origin+Offset(lensSize.width/2,lensSize.height/2)) {
            translate(-offset.x,-offset.y) { drawLayer(glass.lensLayer) }
        }
    }
}

/** Thin, directional reflections: a broad soft shoulder plus a crisp hairline.
 * The low-alpha lower-right shade supplies thickness without a heavy outline. */
internal fun DrawScope.drawDockLensHighlights(outline: Path, origin: Offset, lensSize: Size, light: Boolean, stretch: Float = 0f) {
    val reflection = 1f - 0.15f * dockGlassStretch(stretch)
    val end=origin+Offset(lensSize.width,lensSize.height)
    clipPath(outline) {
        drawPath(outline,Brush.linearGradient(listOf(Color.White.copy(alpha=(if(light)0.22f else 0.14f)*reflection),
            Color.Transparent,Color.White.copy(alpha=0.025f)),origin,end),style=Stroke(1.8.dp.toPx()))
        drawPath(outline,Brush.linearGradient(listOf(Color.White.copy(alpha=(if(light)0.60f else 0.44f)*reflection),
            Color.Transparent,Color.Black.copy(alpha=if(light)0.055f else 0.16f)),origin,end),
            style=Stroke(0.7.dp.toPx()))
        drawPath(outline,Brush.radialGradient(listOf(Color.White.copy(alpha=(if(light)0.12f else 0.06f)*reflection),
            Color.Transparent),origin+Offset(lensSize.width*0.28f,0f),lensSize.height*0.95f),style=Stroke(1.3.dp.toPx()))
    }
}
