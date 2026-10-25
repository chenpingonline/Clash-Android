package cn.nascp.clash.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.nascp.clash.MainViewModel
import cn.nascp.clash.data.AppState
import cn.nascp.clash.data.VpnPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable internal fun PlainProfileList(s: AppState, vm: MainViewModel, go: (String) -> Unit) {
    val busy by vm.busy.collectAsState()
    val canSwitch = s.phase in listOf(VpnPhase.STOPPED, VpnPhase.FAILED) && !busy
    val large = LocalDensity.current.fontScale > 1.2f
    CompositionLocalProvider(LocalPlainCompact provides true) {
        LazyColumn(Modifier.fillMaxSize().testTag("plain-profiles"),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp + LocalDockClearance.current),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item("add") {
                FilledTonalButton(onClick = { go("import") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    UiText("添加配置")
                }
            }
            if (s.profiles.isEmpty()) item("empty") {
                Panel {
                    UiText("还没有配置", style = MaterialTheme.typography.titleMedium)
                    UiText("从订阅链接、本地文件或粘贴的 YAML 开始。", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (s.profiles.isNotEmpty() && !canSwitch && !busy) item("switch-hint") {
                Info("切换配置前请先停止 VPN")
            }
            items(s.profiles, key = { it.id }, contentType = { "profile" }) { profile ->
                val selected = profile.id == s.selectedId
                val running = profile.id == s.activeId && s.phase == VpnPhase.RUNNING
                val updated = remember(profile.updated) { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(profile.updated)) }
                Panel {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlainIcon(Icons.Outlined.Description)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            UiText(profile.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            UiText(if (profile.source.isBlank()) "本地配置" else "HTTPS订阅 · 地址已隐藏",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (selected || running) Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            UiText(if (running) "运行中" else "已选择", Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    @Composable fun Actions() {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { vm.run { vm.repo.selectProfile(profile.id) } }, enabled = canSwitch && !selected, modifier = Modifier.testTag("profile-select:${profile.id}")) { UiText("选择") }
                            FilledTonalButton(onClick = { vm.run {
                                if (!selected) vm.repo.selectProfile(profile.id)
                                go("profile")
                            } }, enabled = !busy && (selected || canSwitch), modifier = Modifier.testTag("profile-manage:${profile.id}"), shape = RoundedCornerShape(10.dp)) { UiText("管理") }
                        }
                    }
                    if (large) {
                        UiText("${tr("更新于")} $updated", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Actions() }
                    } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        UiText("${tr("更新于")} $updated", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Actions()
                    }
                }
            }
            item("extensions") {
                GlobalExtensions(go)
            }
        }
    }
}

@Immutable private data class ActivityConnection(val id: String, val title: String, val subtitle: String, val json: String)

@Composable internal fun PlainActivityPage(s: AppState, vm: MainViewModel, go: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf("connections") }
    var query by rememberSaveable { mutableStateOf("") }
    var paused by rememberSaveable { mutableStateOf(false) }
    var logSnapshot by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var closeIds by remember { mutableStateOf<List<String>?>(null) }
    val busy by vm.busy.collectAsState()
    val context = LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) vm.run {
            val content = (if (paused) logSnapshot else s.logs).joinToString("\n")
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) } ?: error("无法导出日志")
            }
        }
    }
    val connections by produceState(emptyList<ActivityConnection>(), s.connections) {
        value = withContext(Dispatchers.Default) {
            (0 until s.connections.length()).map { index ->
                val connection = s.connections.getJSONObject(index)
                val metadata = connection.optJSONObject("metadata") ?: JSONObject()
                ActivityConnection(connection.optString("id"), metadata.optString("host").ifBlank { metadata.optString("destinationIP") },
                    "${metadata.optString("network").uppercase()} · ↓ ${bytes(connection.optLong("download"))} · ↑ ${bytes(connection.optLong("upload"))}",
                    connection.toString())
            }
        }
    }
    val filtered = remember(connections, query) { connections.filter { it.json.contains(query, true) } }
    val logs = if (paused) logSnapshot else s.logs
    val filteredLogs = remember(logs, query) { logs.filter { it.contains(query, true) }.takeLast(100) }
    CompositionLocalProvider(LocalPlainCompact provides true) {
        LazyColumn(Modifier.then(sectionMotion(tab)).fillMaxSize().testTag("plain-activity"),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp + LocalDockClearance.current),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item("tabs") { Choices(listOf("connections" to "连接", "logs" to "日志"), tab) { tab = it } }
            item("search") {
                AppTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { UiText("搜索域名、IP或日志") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(20.dp)) }, singleLine = true,
                    trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, tr("清除搜索")) } }) else null)
            }
            if (s.phase != VpnPhase.RUNNING) item("stopped") { Info("VPN未启动，暂无实时活动。") }
            item("summary:$tab") {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Column(Modifier.weight(1f).heightIn(min = 48.dp), verticalArrangement = Arrangement.Center) {
                        UiText("${tr(if (tab == "logs") "日志" else "活动连接")} · ${if (tab == "logs") filteredLogs.size else filtered.size}",
                            style = MaterialTheme.typography.titleMedium)
                        if (tab == "logs") UiText(if (paused) "已暂停" else "实时更新", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (tab == "logs") {
                        TextButton(onClick = { if (!paused) logSnapshot = s.logs; paused = !paused }) {
                            Icon(if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, null, Modifier.size(18.dp))
                            UiText(if (paused) "继续" else "暂停")
                        }
                        TextButton(onClick = { export.launch("clash-logs.txt") }, enabled = logs.isNotEmpty() && !busy) { UiText("导出日志") }
                    } else {
                        TextButton(onClick = { closeIds = filtered.map { it.id } }, enabled = s.phase == VpnPhase.RUNNING && filtered.isNotEmpty() && !busy) {
                            UiText("关闭连接")
                        }
                    }
                }
            }
            closeIds?.let { ids -> item("confirm") {
                InlineConfirmation("关闭筛选连接？", "仅关闭当前列表中与搜索内容匹配的连接；新连接不包含在此次操作。", "关闭",
                    onDismiss = { closeIds = null }) {
                    closeIds = null
                    vm.run { check(s.phase == VpnPhase.RUNNING); ids.forEach { vm.repo.api("/connections/${encode(it)}", "DELETE") } }
                }
            } }
            if (tab == "logs") {
                item("log-notice") { Info("内核日志已隐藏 URL 与常见凭据字段；导出前仍请检查内容。") }
                if (filteredLogs.isEmpty()) item("empty-logs") {
                    Panel { UiText(if (logs.isEmpty()) "暂无日志" else "没有匹配的日志", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(filteredLogs.size, key = { "log:$it" }, contentType = { "log" }) { index ->
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface) {
                        UiText(filteredLogs[index], Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                }
            } else {
                if (filtered.isEmpty()) item("empty-connections") {
                    Panel { UiText(if (connections.isEmpty()) "暂无活动连接" else "没有匹配的连接", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(filtered, key = { it.id }, contentType = { "connection" }) { connection ->
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(horizontal = 12.dp)) {
                            Entry(connection.title, connection.subtitle, Icons.Outlined.Public) {
                                vm.repo.saveDraft("connection", connection.json)
                                go("connection")
                            }
                        }
                    }
                }
            }
        }
    }
}
