package cn.nascp.clash.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val dockRoutes = listOf("home", "proxies", "profiles", "activity", "settings")
private val dockLabels = listOf("首页", "代理", "配置", "活动", "设置")

/** Compact floating capsule; the moving selection covers both icon and label. */
@Composable internal fun BottomDock(route: String, backdrop: DockBackdrop? = null, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val plain = LocalPlain.current
    val pearl = LocalPearlGlass.current
    val glass = rememberDockGlass(backdrop, refractive = !plain)
    val cornerRadius = 27.dp
    val lensCornerRadius = 23.dp
    val textMeasurer = rememberTextMeasurer()
    // Keep the entire bar and tab cells stationary while only the lens moves.
    val dockInset = 12.dp
    val selectedIndex = dockRoutes.indexOf(route).coerceAtLeast(0).toFloat()
    val progress = remember { Animatable(1f) }
    var motionStart by remember { mutableStateOf(DockLensBounds(selectedIndex,selectedIndex+1f)) }
    var motionTarget by remember { mutableFloatStateOf(selectedIndex) }
    val selectionPulse = remember { Animatable(0f) }
    val previousRoute = remember { arrayOf(route) }
    LaunchedEffect(route) {
        if (route == previousRoute[0]) return@LaunchedEffect
        previousRoute[0] = route
        motionStart = dockLensBounds(motionStart,motionTarget,progress.value)
        motionTarget = selectedIndex
        progress.snapTo(0f)
        coroutineScope {
            launch { progress.animateTo(1f,tween(220,easing=LinearEasing)) }
            launch {
                selectionPulse.snapTo(1f)
                selectionPulse.animateTo(0f, tween(110))
            }
        }
    }
    val indicatorColor by animateColorAsState(if (plain) { if (colors.background.luminance() < 0.5f) colors.surfaceContainerHighest else Color(0xFFE2E3E4) } else colors.primaryContainer, tween(180), label = "dockIndicatorColor")
    val icons = if (plain) listOf(Icons.Filled.Home, Icons.Filled.Public, Icons.Filled.Description, Icons.Filled.BarChart, Icons.Filled.Settings) else if (pearl) listOf(Icons.Outlined.Home, Icons.AutoMirrored.Outlined.Send, Icons.Outlined.Dns, Icons.Outlined.BarChart, Icons.Outlined.Settings) else listOf(Icons.Outlined.Home, Icons.Outlined.AccountTree,
        Icons.Outlined.Description, Icons.Outlined.Insights, Icons.Outlined.Settings)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = dockInset).padding(top = 10.dp, bottom = 18.dp),
        contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("dock-surface").shadow(12.dp, RoundedCornerShape(cornerRadius),
                ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.14f)),
            shape = RoundedCornerShape(cornerRadius),
            color = Color.Transparent,
            border = null,
            shadowElevation = 0.dp
        ) {
            Box(Modifier.dockGlassSurface(glass, colors.surface, cornerRadius)) {
            Row(Modifier.selectableGroup().padding(horizontal = DockHorizontalPadding, vertical = DockVerticalPadding)
                .testTag("bottom-dock")
                .drawBehind {
                    val cellWidth = size.width / dockRoutes.size
                    val bounds=dockLensBounds(motionStart,motionTarget,progress.value)
                    val inset=1.dp.toPx()
                    val left=if(layoutDirection==LayoutDirection.Rtl)dockRoutes.size-bounds.right else bounds.left
                    val lensWidth=cellWidth*(bounds.right-bounds.left)-inset*2
                    val lensHeight=(size.height-2.dp.toPx()).coerceAtLeast(24.dp.toPx())
                    val origin=Offset(left*cellWidth+inset,(size.height-lensHeight)/2)
                    val lensSize = Size(lensWidth, lensHeight)
                    val stretch=((bounds.right-bounds.left-1f)/0.42f).coerceIn(0f,1f)
                    val outline=dockLensOutline(origin,lensSize,lensCornerRadius.toPx())
                    // The reference uses a frosted grey selection, not a clear lens.
                    // Keep the same blurred base underneath every tab so selection
                    // never exposes sharper page details than the surrounding Dock.
                    if (!plain) drawDockLensBackdrop(glass, origin, lensSize, outline,stretch)
                    drawPath(outline,indicatorColor.copy(alpha = if (plain) 0.96f else 0.64f))
                    drawPath(outline,Brush.linearGradient(listOf(colors.surface.copy(alpha = if (plain) 0.05f else 0.16f),
                        Color.Transparent, colors.surface.copy(alpha = 0.025f)),origin,origin+Offset(lensWidth,lensHeight)))
                    if (!plain) drawDockLensHighlights(outline,origin,lensSize,colors.surface.luminance()>0.5f,stretch)
                },
                verticalAlignment = Alignment.CenterVertically) {
                dockRoutes.forEachIndexed { index, item ->
                    val selected = item == route
                    val tint by animateColorAsState(if (plain) colors.onSurface else if (selected) colors.primary else colors.onSurfaceVariant, animationSpec = tween(180), label = "dockTint")
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    Column(
                        Modifier.weight(1f).padding(horizontal = 1.dp).clip(RoundedCornerShape(lensCornerRadius))
                            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = { onSelect(item) })
                            .heightIn(min = 48.dp).padding(horizontal = 2.dp, vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
                    ) {
                        // Read animation state in the layer phase; it must not recompose every tab each frame.
                        val pressedFeedback = if (pressed) 1f else 0f
                        Box(Modifier.graphicsLayer { alpha = 1f - 0.14f * max(pressedFeedback, if (selected) selectionPulse.value else 0f) }.size(width = 36.dp, height = 24.dp), contentAlignment = Alignment.Center) {
                            // The label supplies the accessibility name; avoid reading it twice.
                            Icon(icons[index], contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                        }
                        BoxWithConstraints(Modifier.fillMaxWidth().graphicsLayer { alpha = 1f - 0.14f * max(pressedFeedback, if (selected) selectionPulse.value else 0f) }) {
                            val label = tr(dockLabels[index])
                            val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp,
                                lineHeight = 16.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                            val availableWidth = constraints.maxWidth
                            // Measure a single unwrapped line. Cache by typography and width,
                            // so tint/selection animation never remeasures every label.
                            val fontSize = remember(label, availableWidth, labelStyle, textMeasurer) {
                                (26 downTo 16).firstOrNull { halfSp ->
                                    !textMeasurer.measure(label, style = labelStyle.copy(fontSize = (halfSp / 2f).sp),
                                        constraints = Constraints(maxWidth = availableWidth),
                                        softWrap = false, maxLines = 1).hasVisualOverflow
                                }?.let { (it / 2f).sp } ?: 8.sp
                            }
                            BasicText(label, modifier = Modifier.fillMaxWidth(),
                                style = labelStyle.copy(color = tint, fontSize = fontSize),
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            }
        }
    }
}
