package cn.nascp.clash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp

private val logLevels=listOf("info" to "信息", "warning" to "警告", "error" to "错误", "debug" to "调试", "silent" to "关闭日志")

@Composable internal fun RuntimeLogLevelSetting(value:String?,running:Boolean,loading:Boolean,enabled:Boolean,onSelect:(String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    val large=LocalDensity.current.fontScale>1.2f
    val canSelect=running && !loading && value!=null && enabled
    LaunchedEffect(canSelect) { if(!canSelect)expanded=false }
    val display=when {
        !running -> tr("未运行")
        loading -> tr("读取中…")
        value==null -> tr("读取失败")
        else -> "${tr(logLevels.firstOrNull{it.first==value}?.second ?: value)} ($value)"
    }
    Panel {
        Box {
            Surface(onClick={expanded=true},enabled=canSelect,shape=RoundedCornerShape(10.dp),
                color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().testTag("log-level-setting")) {
                Row(Modifier.heightIn(min=54.dp).padding(horizontal=4.dp,vertical=8.dp),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(large) Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        UiText("日志等级",style=MaterialTheme.typography.titleMedium)
                        UiText(display,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        UiText("日志等级",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                        UiText(display,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.ExpandMore,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DropdownMenu(expanded && canSelect,onDismissRequest={expanded=false}) {
                logLevels.forEach { (level,label) ->
                    DropdownMenuItem(text={UiText("${tr(label)} ($level)")},
                        leadingIcon={RadioButton(selected=value==level,onClick=null)},
                        modifier=Modifier.testTag("log-level-option:$level").semantics{selected=value==level},
                        onClick={expanded=false;if(level!=value)onSelect(level)})
                }
            }
        }
        UiText(if(running) "仅本次运行生效，重启后按配置恢复" else "启动 VPN 后可调整",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
