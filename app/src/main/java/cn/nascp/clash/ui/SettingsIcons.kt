package cn.nascp.clash.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/** Canonical labels are resolved before translation, so language never changes an icon. */
internal fun settingsScreenIcon(route:String):ImageVector = when(route) {
    "setup" -> Icons.Outlined.Explore
    "import" -> Icons.Outlined.FileDownload
    "profile", "yaml" -> Icons.Outlined.Description
    "profile-info" -> Icons.Outlined.Info
    "group" -> Icons.Outlined.Hub
    "group-edit" -> Icons.Outlined.Hub
    "node-edit" -> Icons.Outlined.Dns
    "rules" -> Icons.AutoMirrored.Outlined.Rule
    "rule-edit" -> Icons.Outlined.EditNote
    "rule-import" -> Icons.AutoMirrored.Outlined.PlaylistAdd
    "rule-set" -> Icons.Outlined.Folder
    "override", "global-override" -> Icons.Outlined.Tune
    "script", "global-script" -> Icons.Outlined.Code
    "history" -> Icons.Outlined.History
    "connection" -> Icons.Outlined.SwapHoriz
    "vpn" -> Icons.Outlined.Shield
    "apps" -> Icons.Outlined.Apps
    "network" -> Icons.Outlined.Router
    "dns" -> Icons.Outlined.Dns
    "dns-servers" -> Icons.Outlined.Dns
    "fakeip" -> Icons.Outlined.DataArray
    "fallback" -> Icons.AutoMirrored.Outlined.AltRoute
    "hosts" -> Icons.Outlined.CompareArrows
    "core" -> Icons.Outlined.Memory
    "geo" -> Icons.Outlined.Public
    "automation" -> Icons.Outlined.Schedule
    "appearance" -> Icons.Outlined.Palette
    "backup" -> Icons.Outlined.SettingsBackupRestore
    "about" -> Icons.Outlined.Info
    "remote" -> Icons.Outlined.Cloud
    "diagnostics" -> Icons.Outlined.FactCheck
    else -> Icons.Outlined.Settings
}

internal fun settingIcon(label:String):ImageVector {
    // Navigation rows use the same icon as their destination, including generated menus.
    screens.values.firstOrNull { it.title==label }?.let { return settingsScreenIcon(it.id) }
    return when(label) {
        "Mixed 端口", "Mixed端口" -> Icons.Outlined.SwapHoriz
        "HTTP 端口", "HTTP端口" -> Icons.Outlined.Public
        "SOCKS 端口", "SOCKS端口" -> Icons.Outlined.Cable
        "监听地址" -> Icons.Outlined.SettingsEthernet
        "允许局域网连接" -> Icons.Outlined.Lan
        "认证（每行 用户:密码）" -> Icons.Outlined.VpnKey
        "启用 DNS" -> Icons.Outlined.PowerSettingsNew
        "解析模式" -> Icons.Outlined.ManageSearch
        "Fake IP 范围", "Fake IP范围" -> Icons.Outlined.DataArray
        "解析 IPv6", "接管 IPv6" -> Icons.Outlined.Public
        "默认 DNS（每行一个）", "默认DNS" -> Icons.Outlined.Dns
        "主解析（每行一个）", "主解析" -> Icons.Outlined.Dns
        "回退 DNS（每行一个）", "回退DNS" -> Icons.AutoMirrored.Outlined.AltRoute
        "代理节点 DNS（每行一个）", "代理节点DNS" -> Icons.Outlined.Hub
        "直连 DNS（每行一个）", "直连DNS" -> Icons.AutoMirrored.Outlined.ArrowForward
        "过滤模式" -> Icons.Outlined.FilterAlt
        "过滤域名（每行一个）" -> Icons.Outlined.FilterList
        "域名策略（JSON 对象）" -> Icons.AutoMirrored.Outlined.Rule
        "回退过滤（JSON 对象）" -> Icons.Outlined.FilterAlt
        "使用 DAT 格式 GeoIP" -> Icons.Outlined.Storage
        "内核运行时自动更新", "后台更新订阅" -> Icons.Outlined.Sync
        "GEO 更新间隔（小时）" -> Icons.Outlined.Update
        "数据源（JSON 对象）" -> Icons.Outlined.CloudDownload
        "MTU（1280–9000）" -> Icons.Outlined.Straighten
        "绕过局域网 IPv4" -> Icons.AutoMirrored.Outlined.AltRoute
        "排除 IPv4 网段（每行一个 CIDR）" -> Icons.Outlined.Block
        "测速 HTTPS 地址" -> Icons.Outlined.Speed
        "测速超时（1000–15000 ms）" -> Icons.Outlined.Timer
        "自定义 User-Agent" -> Icons.Outlined.Badge
        "实例名称" -> Icons.Outlined.Devices
        "访问令牌" -> Icons.Outlined.VpnKey
        "名称", "配置名称", "订阅名称（可选）", "配置名称（可选）" -> Icons.AutoMirrored.Outlined.Label
        "类型", "规则类型", "协议", "策略类型" -> Icons.Outlined.Category
        "服务器" -> Icons.Outlined.Dns
        "端口" -> Icons.Outlined.Cable
        "密码" -> Icons.Outlined.Lock
        "cipher（SS）", "加密方法" -> Icons.Outlined.EnhancedEncryption
        "成员（每行一个）" -> Icons.Outlined.GroupWork
        "目标策略" -> Icons.AutoMirrored.Outlined.AltRoute
        "规则内容", "规则内容（MATCH 留空）" -> Icons.AutoMirrored.Outlined.FormatListBulleted
        "代理组" -> Icons.Outlined.Hub
        "传输参数" -> Icons.Outlined.SettingsEthernet
        "健康检查地址" -> Icons.Outlined.MonitorHeart
        "容差（ms）" -> Icons.Outlined.Tune
        "内容", "规则文本" -> Icons.AutoMirrored.Outlined.Rule
        "位置", "合并方式" -> Icons.Outlined.Reorder
        "国家代码" -> Icons.Outlined.Flag
        "域名（每行一个）" -> Icons.Outlined.FilterList
        "域名策略" -> Icons.AutoMirrored.Outlined.Rule
        "IP地址", "IP段（每行一个）" -> Icons.Outlined.SettingsEthernet
        "no-resolve" -> Icons.Outlined.Dns
        "包含应用设置及远程实例" -> Icons.Outlined.Archive
        "behavior：auto / domain / ipcidr / classical" -> Icons.Outlined.Category
        "搜索规则", "搜索应用", "查找文本" -> Icons.Outlined.Search
        "类型过滤" -> Icons.Outlined.FilterList
        else -> when {
            label.contains("令牌") || label.contains("认证") -> Icons.Outlined.VpnKey
            label.contains("密码") -> Icons.Outlined.Lock
            label.contains("HTTPS") || label.contains("来源") -> Icons.Outlined.Link
            label.contains("端口") -> Icons.Outlined.Cable
            label.contains("超时") -> Icons.Outlined.Timer
            label.contains("间隔") || label.contains("分钟") || label.contains("小时") -> Icons.Outlined.Schedule
            label.contains("更新") -> Icons.Outlined.Update
            label.contains("DNS",true) || label.contains("解析") -> Icons.Outlined.Dns
            label.contains("域名") || label.contains("Hosts",true) -> Icons.AutoMirrored.Outlined.MenuBook
            label.contains("地址") || label.contains("网段") -> Icons.Outlined.SettingsEthernet
            label.contains("名称") -> Icons.AutoMirrored.Outlined.Label
            else -> Icons.Outlined.Settings
        }
    }
}
