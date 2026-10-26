package cn.nascp.clash.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Material handles gestures and accessibility; the track follows the reference capsule. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextAdjustmentSlider(
    value:Float, range:ClosedFloatingPointRange<Float>, steps:Int, title:String, description:String,
    weight:Boolean=false, onChange:(Float)->Unit, onFinished:()->Unit,
) {
    val primary=MaterialTheme.colorScheme.primary
    val track=MaterialTheme.colorScheme.surfaceVariant
    val muted=MaterialTheme.colorScheme.onSurfaceVariant
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        UiText("A",Modifier.width(24.dp),color=muted,
            style=MaterialTheme.typography.bodyLarge.copy(fontSize=if(weight)24.sp else 16.sp),fontWeight=FontWeight.Light)
        Slider(value=value,onValueChange=onChange,onValueChangeFinished=onFinished,valueRange=range,steps=steps,
            modifier=Modifier.weight(1f).heightIn(min=48.dp).semantics {
                contentDescription=title;stateDescription=description
            },
            thumb={Box(Modifier.size(20.dp).background(Color.White,CircleShape))},
            track={ state ->
                Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                    val radius=size.height/2
                    val fraction=((state.value-range.start)/(range.endInclusive-range.start)).coerceIn(0f,1f)
                    // Extend beneath the thumb's end positions to keep a round blue cap at zero.
                    val left=-radius
                    val width=size.width+2*radius
                    drawRoundRect(track,Offset(left,0f),Size(width,size.height),CornerRadius(radius))
                    val activeWidth=size.width*fraction+2*radius
                    val activeLeft=if(rtl)size.width+radius-activeWidth else left
                    drawRoundRect(primary,Offset(activeLeft,0f),Size(activeWidth,size.height),CornerRadius(radius))
                    if(!weight) {
                        for(index in 0..steps+1) {
                            val point=index.toFloat()/(steps+1)
                            val x=if(rtl)size.width*(1-point) else size.width*point
                            // The white thumb covers its current tick.
                            if(abs(point-fraction)*size.width>11.dp.toPx()) {
                                drawCircle(if(point<=fraction)Color.White.copy(alpha=.35f) else muted.copy(alpha=.16f),
                                    2.5.dp.toPx(),Offset(x,center.y))
                            }
                        }
                    }
                }
            })
        UiText("A",Modifier.width(28.dp),color=muted,
            style=MaterialTheme.typography.bodyLarge.copy(fontSize=28.sp),fontWeight=if(weight)FontWeight.ExtraBold else FontWeight.Normal)
    }
}

@Composable
internal fun FontScaleControl(value:Float,onValueChange:(Float)->Unit) {
    var draft by remember(value){mutableFloatStateOf(value)}
    val density=LocalDensity.current
    val percent="${(draft*100).roundToInt()}%"
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            UiText("应用字体大小",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            UiText(percent,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
            if(abs(draft-1f)>.001f)SubpageTextButton(onClick={draft=1f;onValueChange(1f)},contentPadding=PaddingValues(horizontal=8.dp)){UiText("重置")}
        }
        TextAdjustmentSlider(draft,0.8f..1.5f,6,tr("应用字体大小"),percent,
            onChange={draft=(it*10).roundToInt()/10f},onFinished={onValueChange(draft)})
        CompositionLocalProvider(LocalDensity provides Density(density.density,density.fontScale*draft/value)) {
            UiText("预览文字 Aa",Modifier.padding(top=8.dp,bottom=8.dp),style=MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
internal fun FontWeightControl(value:Int,onValueChange:(Int)->Unit) {
    var draft by remember(value){mutableFloatStateOf(value.toFloat())}
    val label=when(draft.roundToInt()){-100->"偏细";100->"加粗";else->"标准"}
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
            UiText("字体粗细",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            UiText(label,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
        }
        TextAdjustmentSlider(draft,-100f..100f,1,tr("字体粗细"),tr(label),weight=true,
            onChange={draft=(it/100).roundToInt()*100f},onFinished={onValueChange(draft.roundToInt())})
    }
}
