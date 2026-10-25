package cn.nascp.clash.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Keep Material's gestures, discrete steps and accessibility with quieter visuals. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FontScaleControl(value: Float, onValueChange: (Float) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    val density = LocalDensity.current
    val title = tr("应用字体大小")
    val percent = "${(draft * 100).roundToInt()}%"
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UiText("应用字体大小", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            UiText(percent, color = muted, style = MaterialTheme.typography.bodyMedium)
            if (abs(draft - 1f) > 0.001f) {
                SubpageTextButton(onClick = { draft = 1f; onValueChange(1f) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    UiText("重置")
                }
            }
        }
        Slider(value = draft, onValueChange = { draft = (it * 10).roundToInt() / 10f },
            onValueChangeFinished = { onValueChange(draft) },
            valueRange = 0.8f..1.5f, steps = 6,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                contentDescription = title
                stateDescription = percent
            },
            thumb = {
                Box(Modifier.size(20.dp).background(primary, CircleShape))
            },
            track = { state ->
                Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                    val start = if (rtl) size.width else 0f
                    val end = if (rtl) 0f else size.width
                    val fraction = ((state.value - 0.8f) / 0.7f).coerceIn(0f, 1f)
                    drawLine(track, Offset(start, center.y), Offset(end, center.y), size.height, StrokeCap.Round)
                    drawLine(primary, Offset(start, center.y), Offset(start + (end - start) * fraction, center.y), size.height, StrokeCap.Round)
                }
            })
        // The default label follows its actual position, two of seven intervals from the start.
        Layout(content = {
            UiText("80%", color = muted, style = MaterialTheme.typography.labelSmall)
            UiText("100% 默认", color = muted, style = MaterialTheme.typography.labelSmall)
            UiText("150%", color = muted, style = MaterialTheme.typography.labelSmall)
        }, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) { measurables, constraints ->
            val labels = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
            layout(constraints.maxWidth, labels.maxOf { it.height }) {
                labels[0].placeRelative(0, 0)
                labels[1].placeRelative((constraints.maxWidth * 2f / 7f - labels[1].width / 2f).roundToInt(), 0)
                labels[2].placeRelative(constraints.maxWidth - labels[2].width, 0)
            }
        }
        // Only the preview resizes during a drag, keeping the track under the finger.
        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * draft / value)) {
            UiText("预览文字 Aa", Modifier.padding(top = 16.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodyLarge)
        }
    }
}
