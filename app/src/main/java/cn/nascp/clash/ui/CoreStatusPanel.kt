package cn.nascp.clash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.nascp.clash.data.AppState
import cn.nascp.clash.data.VpnPhase

@Composable internal fun CoreStatusPanel(
    state: AppState, enabled: Boolean, onValidate: () -> Unit, onRestart: () -> Unit,
) {
    val running = state.phase == VpnPhase.RUNNING
    val status = when (state.phase) {
        VpnPhase.RUNNING -> "运行中"
        VpnPhase.STARTING -> "正在启动…"
        VpnPhase.STOPPING -> "正在停止…"
        VpnPhase.FAILED -> "启动失败"
        VpnPhase.STOPPED -> "已停止"
    }
    val statusColor = when (state.phase) {
        VpnPhase.RUNNING -> MaterialTheme.colorScheme.primary
        VpnPhase.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val largeText = LocalDensity.current.fontScale > 1.2f
    Panel {
        @Composable fun Status() {
            Surface(color = statusColor.copy(alpha = 0.10f), contentColor = statusColor,
                shape = RoundedCornerShape(50)) {
                UiText(status, Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium)
            }
        }
        if (largeText) {
            UiText("Mihomo v1.19.30", style = MaterialTheme.typography.titleLarge)
            Status()
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UiText("Mihomo v1.19.30", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Status()
        }
        UiText("arm64-v8a · ${tr("内核随应用版本交付")}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        @Composable fun Validate(modifier: Modifier) {
            FilledTonalButton(onClick = onValidate, enabled = enabled && state.selected != null,
                modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)) {
                UiText("校验当前配置", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            }
        }
        @Composable fun Restart(modifier: Modifier) {
            OutlinedButton(onClick = onRestart, enabled = enabled && running,
                modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)) {
                UiText("重启并应用配置", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            }
        }
        if (largeText) {
            Validate(Modifier.fillMaxWidth())
            Restart(Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Validate(Modifier.weight(1f))
            Restart(Modifier.weight(1f))
        }
        UiText(if (running) "重启会短暂断开连接" else if (state.selected == null) "请先选择配置" else "启动 VPN 后可重启并应用配置",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
