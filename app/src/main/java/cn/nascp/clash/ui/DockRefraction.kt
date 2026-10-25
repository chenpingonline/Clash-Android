package cn.nascp.clash.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposeRenderEffect

internal const val DockContourCapacity = 128

/** Flatten the actual clipping path, not a second approximation of its geometry.
 * Error is measured in physical pixels, including on high-density devices. */
internal fun dockRefractionContour(outline: Path): FloatArray {
    var tolerance = 0.3f
    var points = outline.asAndroidPath().approximate(tolerance)
    // Current Dock curves fit comfortably; keep the shader bounded for future shapes.
    while (points.size / 3 > DockContourCapacity) {
        tolerance *= 1.5f
        points = outline.asAndroidPath().approximate(tolerance)
    }
    return FloatArray(points.size / 3 * 2) { index -> points[index / 2 * 3 + 1 + index % 2] }
}

/** Inward vertex normals of the closed, clockwise screen-space contour. Interpolating
 * these avoids visible changes in bend direction at polyline segment boundaries. */
internal fun dockContourNormals(points: FloatArray): FloatArray {
    val unique = points.size / 2 - 1 // Path.approximate repeats the closing point.
    val normals = FloatArray(points.size)
    fun edgeNormal(a: Int, b: Int): Offset {
        val dx = points[b * 2] - points[a * 2]
        val dy = points[b * 2 + 1] - points[a * 2 + 1]
        val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.0001f)
        return Offset(-dy / length, dx / length)
    }
    for (i in 0 until unique) {
        val n = edgeNormal((i + unique - 1) % unique, i) + edgeNormal(i, (i + 1) % unique)
        val length = n.getDistance().coerceAtLeast(0.0001f)
        normals[i * 2] = n.x / length
        normals[i * 2 + 1] = n.y / length
    }
    normals[unique * 2] = normals[0]
    normals[unique * 2 + 1] = normals[1]
    return normals
}

/** Refraction follows the same animated outline as clipping and reflections.
 * Only background samples are displaced; icons and labels are drawn afterwards. */
@RequiresApi(33)
internal class DockRefraction(blurPixels: Float) {
    private val shader = RuntimeShader("""
        uniform shader page;
        uniform float2 center;
        uniform float2 halfSize;
        uniform float2 contour[128];
        uniform float2 normals[128];
        uniform int pointCount;
        uniform float edgeBand;
        uniform float amount;
        half4 main(float2 coord) {
            float2 p = coord - center;
            // The recorded strip is wider than the lens; do not walk its contour there.
            if (abs(p.x) > halfSize.x || abs(p.y) > halfSize.y) return page.eval(coord);
            float bestSquared = 1e12;
            float2 inward = float2(0.0);
            float2 a = contour[0];
            for (int i = 1; i < 128; i++) {
                if (i >= pointCount) break;
                float2 b = contour[i];
                float2 ab = b - a;
                float t = clamp(dot(coord - a, ab) / max(dot(ab, ab), 0.0001), 0.0, 1.0);
                float2 delta = coord - (a + t * ab);
                float squared = dot(delta, delta);
                if (squared < bestSquared) {
                    bestSquared = squared;
                    inward = mix(normals[i - 1], normals[i], t);
                }
                a = b;
            }
            float distance = sqrt(bestSquared);
            float edge = 1.0 - smoothstep(0.0, edgeBand, distance);
            float2 normal = inward / max(length(inward), 0.001);
            float2 samplePoint = coord + normal * edge * edge * amount;
            return page.eval(samplePoint);
        }
    """.trimIndent())
    private val blur = RenderEffect.createBlurEffect(blurPixels, blurPixels, Shader.TileMode.CLAMP)
    private var lastOrigin: Offset? = null
    private var lastSize: Size? = null
    private var lastBend = -1f
    private var lastContour = FloatArray(0)
    private val uniforms = FloatArray(DockContourCapacity * 2)
    private val normalUniforms = FloatArray(DockContourCapacity * 2)
    private var cachedEffect: androidx.compose.ui.graphics.RenderEffect? = null

    fun update(origin: Offset, size: Size, outline: Path, pathOffset: Offset, bendPixels: Float): androidx.compose.ui.graphics.RenderEffect {
        val points = dockRefractionContour(outline)
        for (i in points.indices step 2) {
            points[i] += pathOffset.x
            points[i + 1] += pathOffset.y
        }
        if (origin == lastOrigin && size == lastSize && bendPixels == lastBend && points.contentEquals(lastContour)) return cachedEffect!!
        points.copyInto(uniforms)
        dockContourNormals(points).copyInto(normalUniforms)
        shader.setFloatUniform("center", origin.x + size.width / 2, origin.y + size.height / 2)
        shader.setFloatUniform("halfSize", size.width / 2, size.height / 2)
        shader.setFloatUniform("contour", uniforms)
        shader.setFloatUniform("normals", normalUniforms)
        shader.setIntUniform("pointCount", points.size / 2)
        shader.setFloatUniform("edgeBand", size.height * 0.22f)
        shader.setFloatUniform("amount", bendPixels)
        val effect = RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader, "page"), blur).asComposeRenderEffect()
        lastOrigin = origin
        lastSize = size
        lastBend = bendPixels
        lastContour = points
        cachedEffect = effect
        return effect
    }
}
