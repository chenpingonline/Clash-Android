package cn.nascp.clash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.nascp.clash.data.AppState
import cn.nascp.clash.data.VpnPhase

@Composable internal fun HomeExitCard(state:AppState,refresh:()->Unit) {
    val running=state.phase==VpnPhase.RUNNING
    val data=state.exitLocation.takeIf{running}
    val place=listOfNotNull(data?.region?.takeIf{it.isNotBlank()},data?.city?.takeIf{it.isNotBlank()}).distinct().joinToString(" · ")
    Panel {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                UiText("出口 IP",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                UiText(data?.ip ?: tr(when { !running->"未连接";state.exitLoading->"查询中…";state.exitError.isNotBlank()->"获取失败";else->"—" }),
                    Modifier.testTag("home-exit-ip"),style=MaterialTheme.typography.titleLarge,
                    color=if(state.exitError.isNotBlank())MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick=refresh,enabled=running && !state.exitLoading,modifier=Modifier.testTag("refresh-exit-location")) {
                if(state.exitLoading)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
                else Icon(Icons.Outlined.Refresh,tr("刷新出口信息"))
            }
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                UiText("国家/地区",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                UiText(data?.country?.ifBlank{data.countryCode}?.ifBlank{"—"} ?: "—",style=MaterialTheme.typography.bodyMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                UiText("位置",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                UiText(place.ifBlank{"—"},style=MaterialTheme.typography.bodyMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.Top) {
            UiText("时区",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            UiText(listOfNotNull(data?.timezone?.takeIf{it.isNotBlank()},data?.utcOffset?.takeIf{it.isNotBlank()}).joinToString(" · ").ifBlank{"—"},
                Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
        }
    }
}
