package cn.nascp.clash.ui

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.abs
import kotlin.math.tanh
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path

internal data class DockLensBounds(val left:Float,val right:Float)
private val leadingEdge=CubicBezierEasing(0.18f,0.72f,0.24f,1f)
private val trailingEdge=CubicBezierEasing(0.28f,0.12f,0.30f,1f)

/** Cell coordinates keep shape motion separate from layout. A single finite timeline
 * retargets from the visible edges and cannot create a second pulse after landing. */
internal fun dockLensBounds(start:DockLensBounds,target:Float,progress:Float):DockLensBounds {
    val p=progress.coerceIn(0f,1f)
    if(p==0f)return start
    if(p==1f)return DockLensBounds(target,target+1f)
    val rightward=target+0.5f >= (start.left+start.right)/2f
    val front=leadingEdge.transform(p);val rear=trailingEdge.transform(p)
    fun mix(a:Float,b:Float,f:Float)=a+(b-a)*f
    val left=mix(start.left,target,if(rightward)rear else front)
    val right=mix(start.right,target+1f,if(rightward)front else rear)
    // Long jumps must not turn the lens into a second navigation bar.
    val excess=(abs(right-left)-1f).coerceAtLeast(0f)
    val width=1f+0.42f*tanh(excess/0.42f)
    val center=((left+right)/2f).coerceIn(width/2f,5f-width/2f)
    return DockLensBounds(center-width/2f,center+width/2f)
}

/** Motion changes only horizontal bounds; height and corner radius stay fixed. */
internal fun dockLensOutline(origin: Offset, size: Size, radius: Float): Path {
    val corner = radius.coerceIn(0f, minOf(size.width, size.height) / 2f)
    return Path().apply {
        addRoundRect(RoundRect(Rect(origin, size), CornerRadius(corner, corner)))
    }
}
