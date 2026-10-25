package cn.nascp.clash.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Local mode changes reuse one editor/list, preserving focus and lazy rendering.
 * Initial entry uses the page navigator's motion; live data updates do not replay it. */
@Composable internal fun sectionMotion(section: Any?): Modifier {
    val progress = remember { Animatable(1f) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(section) {
        if (!entered) entered = true else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
        }
    }
    return Modifier.graphicsLayer {
        alpha = 0.25f + 0.75f * progress.value
        translationY = (1f - progress.value) * 6.dp.toPx()
    }
}
