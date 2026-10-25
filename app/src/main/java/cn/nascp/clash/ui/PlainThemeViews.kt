package cn.nascp.clash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import cn.nascp.clash.MainViewModel
import cn.nascp.clash.data.AppState
import cn.nascp.clash.data.VpnPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val PlainBlue = Color(0xFF237FFF)
internal val PlainPurple = Color(0xFF8151F5)
internal val PlainGreen = Color(0xFF20BC48)
internal val PlainOrange = Color(0xFFFFAA00)

private fun iconColor(icon: ImageVector): Color = when (icon) {
    Icons.Outlined.Shield, Icons.Outlined.Backup, Icons.Outlined.SettingsBackupRestore, Icons.Outlined.Wifi, Icons.Outlined.Lock, Icons.Outlined.VpnKey, Icons.Outlined.VpnLock, Icons.Outlined.EnhancedEncryption, Icons.Outlined.PowerSettingsNew -> PlainGreen
    Icons.Outlined.Palette, Icons.Outlined.Apps, Icons.Outlined.Schedule, Icons.Outlined.Update, Icons.Outlined.Sync, Icons.Outlined.Timer, Icons.Outlined.History -> PlainOrange
    Icons.Outlined.Dns, Icons.Outlined.Storage, Icons.Outlined.Memory, Icons.Outlined.Hub, Icons.Outlined.Code, Icons.Outlined.DataArray, Icons.Outlined.SettingsRemote, Icons.Outlined.Cloud, Icons.Outlined.CompareArrows, Icons.AutoMirrored.Outlined.Send -> PlainPurple
    Icons.AutoMirrored.Outlined.Rule, Icons.AutoMirrored.Outlined.FormatListBulleted, Icons.Outlined.FilterList, Icons.Outlined.FilterAlt, Icons.Outlined.EditNote, Icons.AutoMirrored.Outlined.PlaylistAdd -> Color(0xFFE56A74)
    Icons.Outlined.Info, Icons.Outlined.FactCheck -> Color(0xFF8993A1)
    else -> PlainBlue
}

@Composable internal fun PlainIcon(icon: ImageVector, color: Color = iconColor(icon)) {
    Box(Modifier.size(32.dp).background(color, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(20.dp), tint = Color.White)
    }
}

@Composable internal fun PlainEntry(title: String, subtitle: String = "", icon: ImageVector,
                                    onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
        .heightIn(min = if (LocalPlainCompact.current) { if (subtitle.isBlank()) 48.dp else 56.dp } else if (subtitle.isBlank()) 54.dp else 64.dp)
        .padding(horizontal = 2.dp, vertical = if (LocalPlainCompact.current) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PlainIcon(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            UiText(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) UiText(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = Color(0xFF9C9FA4))
    }
}

/** A single surface for a related list; no vertical gaps between its rows. */
@Composable internal fun PlainGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 14.dp), content = content)
    }
}

@Composable internal fun PlainDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

@Composable internal fun PlainHome(s: AppState, start: () -> Unit, stop: () -> Unit,
                                   go: (String) -> Unit, vm: MainViewModel) {
    val running = s.phase == VpnPhase.RUNNING
    val transitioning = s.phase in listOf(VpnPhase.STARTING, VpnPhase.STOPPING)
    val groups = s.proxies.keys().asSequence().filter { s.proxies.optJSONObject(it)?.has("all") == true }.toList()
    val activeGroup = if (s.mode == "global") "GLOBAL" else groups.firstOrNull { it != "GLOBAL" } ?: groups.firstOrNull()
    val node = activeGroup?.let { s.proxies.optJSONObject(it)?.optString("now") }.orEmpty()
    Page {
        Panel {
            val expanded = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f
            @Composable fun ConnectionButton() {
                Button(onClick = if (running) stop else start,
                    enabled = s.ready && s.selected != null && !transitioning,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(18.dp)) {
                    UiText(if (running) "断开" else "启动 VPN")
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlainIcon(Icons.Outlined.Shield, if (running) PlainGreen else Color(0xFF929AA5))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    UiText(when (s.phase) {
                        VpnPhase.RUNNING -> "已连接"
                        VpnPhase.STARTING -> "正在启动…"
                        VpnPhase.STOPPING -> "正在停止…"
                        VpnPhase.FAILED -> "启动失败"
                        else -> "未连接"
                    }, style = MaterialTheme.typography.titleLarge)
                    UiText(if (running) "VPN 已启动" else if (s.selected == null) "导入配置，然后启动 VPN" else s.selected!!.name,
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!expanded) ConnectionButton()
            }
            if (expanded) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { ConnectionButton() }
        }
        Choices(listOf("rule" to "规则", "global" to "全局", "direct" to "直连"), s.mode) { vm.run { vm.repo.mode(it) } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlainRate("下载", bytes(s.down) + "/s", bytes(s.totalDown), Icons.Outlined.ArrowDownward, PlainBlue, Modifier.weight(1f))
            PlainRate("上传", bytes(s.up) + "/s", bytes(s.totalUp), Icons.Outlined.ArrowUpward, PlainPurple, Modifier.weight(1f))
        }
        HomeExitCard(s,vm.repo::refreshExitLocation)
        PlainGroup {
            PlainEntry("当前配置", s.selected?.name ?: "尚未导入", Icons.Outlined.Description) { go(if (s.selected == null) "import" else "profile") }
            PlainDivider()
            PlainEntry("当前节点", node.takeIf { running && it.isNotBlank() } ?: tr(if (running) "选择节点" else "查看配置中的节点和分组"), Icons.AutoMirrored.Outlined.Send) { go("proxies") }
        }
        UiText("常用功能", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.clickable { go("apps") }.heightIn(min = 60.dp).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlainIcon(Icons.Outlined.Apps)
                    UiText("分应用代理", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                }
            }
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.clickable(enabled = running && !s.exitLoading) {
                    vm.repo.refreshExitLocation()
                }.heightIn(min = 60.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlainIcon(Icons.Outlined.Wifi, if (s.exitLocation!=null) PlainGreen else Color(0xFF929AA5))
                    UiText(if (s.exitLoading) "测试中" else "网络检测", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        color = if (running) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

    }
}

@Composable private fun PlainRate(label: String, value: String, total:String, icon: ImageVector, color: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.heightIn(min = 84.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, Modifier.size(19.dp), tint = color)
                UiText(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            UiText(value, style = MaterialTheme.typography.titleLarge, color = color)
            UiText("${tr("累计")} $total",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable internal fun PlainSettings(go: (String) -> Unit) {
    Page {
        UiText("网络", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PlainGroup {
            PlainEntry("VPN 设置", icon = Icons.Outlined.Shield) { go("vpn") }; PlainDivider()
            PlainEntry("网络与端口", icon = settingsScreenIcon("network")) { go("network") }; PlainDivider()
            PlainEntry("DNS 与解析", icon = Icons.Outlined.Dns) { go("dns") }
        }
        UiText("通用", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PlainGroup {
            PlainEntry("外观与语言", icon = Icons.Outlined.Palette) { go("appearance") }; PlainDivider()
            PlainEntry("自动化与后台", icon = Icons.Outlined.Schedule) { go("automation") }; PlainDivider()
            PlainEntry("备份与恢复", icon = settingsScreenIcon("backup")) { go("backup") }
        }
        PlainGroup {
            PlainEntry("内核管理", icon = settingsScreenIcon("core")) { go("core") }; PlainDivider()
            PlainEntry("关于与更新", icon = Icons.Outlined.Info) { go("about") }
        }
        UiText("高级", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PlainGroup {
            PlainEntry("路由规则", icon = settingsScreenIcon("rules")) { go("rules") }; PlainDivider()
            PlainEntry("远程控制器", icon = settingsScreenIcon("remote")) { go("remote") }
        }
    }
}



@Composable internal fun PlainAppearance(s: AppState, vm: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Page {
        Panel {
            UiText("主题模式", style = MaterialTheme.typography.titleMedium)
            Choices(listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色"), s.theme, vm.repo::theme)
            UiText("主题即时生效，保留当前配置与VPN连接。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel {
            UiText("语言", style = MaterialTheme.typography.titleMedium)
            Choices(listOf("zh" to "简体中文", "en" to "English"), s.language, vm.repo::language)
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            FontScaleControl(s.fontScale, vm.repo::fontScale)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Entry("系统字体设置", "应用字体会叠加系统字号", Icons.Outlined.TextFields) {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS))
            }
        }
        Info("页面与控件使用轻量过渡动画，跟随系统动画缩放与辅助功能设置。")
    }
}
