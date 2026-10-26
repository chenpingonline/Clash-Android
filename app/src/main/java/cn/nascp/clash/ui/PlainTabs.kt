package cn.nascp.clash.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.nascp.clash.MainViewModel
import cn.nascp.clash.data.AppState
import cn.nascp.clash.data.VpnPhase
import cn.nascp.clash.data.SubscriptionUpdateStatus
import cn.nascp.clash.data.MessageKind
import cn.nascp.clash.system.updateSubscriptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable internal fun PlainProfileList(s: AppState, vm: MainViewModel, go: (String) -> Unit) {
    val busy by vm.busy.collectAsState()
    var menuId by rememberSaveable { mutableStateOf<String?>(null) }
    val canSwitch = s.phase in listOf(VpnPhase.STOPPED, VpnPhase.FAILED) && !busy
    CompositionLocalProvider(LocalPlainCompact provides true) {
        LazyColumn(Modifier.fillMaxSize().testTag("plain-profiles"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + LocalDockClearance.current),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.profiles.isEmpty()) item("empty") {
                Panel {
                    UiText("还没有配置", style = MaterialTheme.typography.titleMedium)
                    UiText("从订阅链接、本地文件或粘贴的 YAML 开始。", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { go("import") }) { UiText("添加配置") }
                }
            }
            if (s.profiles.isNotEmpty() && !canSwitch && !busy) item("switch-hint") {
                Info("切换配置前请先停止 VPN")
            }
            if (s.profiles.isNotEmpty()) item("profiles-heading") { SectionHeading("配置列表") }
            if (s.profiles.isNotEmpty()) item("profiles") {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.selectableGroup()) {
                        s.profiles.forEachIndexed { index, profile ->
                            val selected = profile.id == s.selectedId
                            val update=s.subscriptionUpdates[profile.id]
                            val pending=vm.repo.draft("pending-update.${profile.id}").isNotBlank()
                            val updating=update==SubscriptionUpdateStatus.DOWNLOADING
                            val running = profile.id == s.activeId && s.phase == VpnPhase.RUNNING
                            val updated = remember(profile.updated) {
                                SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(profile.updated))
                            }
                            if (index > 0) PlainDivider()
                            val selectionInteraction=remember(profile.id){MutableInteractionSource()}
                            val pressed by selectionInteraction.collectIsPressedAsState()
                            val selectionColor by animateColorAsState(
                                if(selected || pressed)MaterialTheme.colorScheme.primary.copy(alpha=when {selected && pressed->.20f;selected->.12f;else->.08f}) else Color.Transparent,
                                label = "profile-selection")
                            Row(Modifier.fillMaxWidth().background(selectionColor).heightIn(min = 64.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                // Share selection press feedback across the row, while keeping the menu action independent.
                                Row(Modifier.weight(1f).selectable(selected = selected, enabled = canSwitch,
                                    interactionSource=selectionInteraction,indication=null,
                                    role = Role.RadioButton, onClick = { if (!selected) vm.run { vm.repo.selectProfile(profile.id) } })
                                    .testTag("profile-select:${profile.id}").padding(start = 18.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    PlainIcon(Icons.Outlined.Description)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        UiText(profile.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        val subtitle=when {
                                            updating->"${tr(if(profile.source.isBlank())"本地配置" else "订阅配置")} · $updated"
                                            update==SubscriptionUpdateStatus.FAILED->tr("更新失败，原配置已保留")
                                            pending && running->tr("更新已下载，停止后可应用")
                                            pending->tr("更新已下载，待应用")
                                            update==SubscriptionUpdateStatus.UPDATED->"${tr("更新成功")} · $updated"
                                            running->"${tr("运行中")} · $updated"
                                            else->"${tr(if(profile.source.isBlank())"本地配置" else "订阅配置")} · $updated"
                                        }
                                        UiText(subtitle,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis,
                                            color=if(update==SubscriptionUpdateStatus.FAILED)MaterialTheme.colorScheme.error else if(pending || running)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                }
                                IconButton(onClick = { menuId=profile.id }, enabled=!busy && !updating,
                                    modifier=Modifier.size(48.dp).testTag("profile-manage:${profile.id}")) {
                                    if(updating)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
                                    else Icon(Icons.Outlined.MoreHoriz,"${tr("更多操作")} ${profile.name}",Modifier.size(24.dp))
                                }
                            }
                            if(pending && s.phase in listOf(VpnPhase.STOPPED,VpnPhase.FAILED))TextButton(
                                onClick={vm.run {vm.repo.applyPendingSubscription(profile.id);vm.repo.message("订阅更新已应用")}},
                                enabled=!busy && !updating,modifier=Modifier.fillMaxWidth().testTag("profile-apply:${profile.id}")) {UiText("应用已下载的订阅更新")}
                        }
                    }
                }
            }
            item("global-heading") { SectionHeading("全局增强") }
            item("global-extensions") {
                PlainGroup {
                    PlainEntry("全局覆写", "为所有配置统一修改指定设置", Icons.Outlined.Tune) { go("global-override") }
                    PlainDivider()
                    PlainEntry("全局扩展脚本", "用 JavaScript 处理所有配置", Icons.Outlined.Code) { go("global-script") }
                }
            }
        }
        s.profiles.firstOrNull { it.id==menuId }?.let { profile ->
            AppBottomSheet(profile.name,onDismiss={menuId=null}) {
                ProfileSheetAction("配置详情","profile-details:${profile.id}",enabled=!busy && (profile.id==s.selectedId || canSwitch)) {
                    menuId=null
                    vm.run { if(profile.id!=s.selectedId)vm.repo.selectProfile(profile.id);go("profile") }
                }
                if(profile.id!=s.selectedId && !canSwitch)Info("切换配置前请先停止 VPN")
                if(profile.source.isNotBlank())ProfileSheetAction("更新订阅","profile-update:${profile.id}",
                    enabled=!busy && s.subscriptionUpdates.values.none{it==SubscriptionUpdateStatus.DOWNLOADING}) {
                    menuId=null;vm.run { vm.repo.updateSubscription(profile.id) }
                }
            }
        }
    }
}

@Immutable private data class ActivityConnection(val id: String, val title: String, val subtitle: String, val json: String,val uid:Int?)

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
    val applications=rememberConnectionApplications(s.connections)
    val connections by produceState(emptyList<ActivityConnection>(), s.connections) {
        value = withContext(Dispatchers.Default) {
            (0 until s.connections.length()).map { index ->
                val connection = s.connections.getJSONObject(index)
                val metadata = connection.optJSONObject("metadata") ?: JSONObject()
                ActivityConnection(connection.optString("id"), metadata.optString("host").ifBlank { metadata.optString("destinationIP") },
                    "${metadata.optString("network").uppercase()} · ↓ ${bytes(connection.optLong("download"))} · ↑ ${bytes(connection.optLong("upload"))}",
                    connection.toString(),connectionUid(connection))
            }
        }
    }
    val filtered = remember(connections, query,applications) { connections.filter { it.json.contains(query, true) || applications[it.uid]?.searchText?.contains(query,true)==true } }
    val logs = if (paused) logSnapshot else s.logs
    val filteredLogs = remember(logs, query) { logs.filter { it.contains(query, true) }.takeLast(100) }
    CompositionLocalProvider(LocalPlainCompact provides true) {
        LazyColumn(Modifier.then(sectionMotion(tab)).fillMaxSize().testTag("plain-activity"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + LocalDockClearance.current),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item("tabs") { Choices(listOf("connections" to "连接", "logs" to "日志"), tab) { tab = it } }
            item("rules") { Entry("运行规则",icon=settingsScreenIcon("rules")){go("rules")} }
            item("search") {
                AppTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { UiText(if(tab=="logs")"搜索日志" else "搜索应用、包名、域名或 IP") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(20.dp)) }, singleLine = true,
                    trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, tr("清除搜索")) } }) else null)
            }
            if (s.phase != VpnPhase.RUNNING) item("stopped") { Info("VPN未启动，暂无实时活动。") }
            if (s.phase == VpnPhase.RUNNING && (if(tab=="logs")s.logsError else s.connectionsError)) item("read-error") {
                Panel { Info(if(tab=="logs")"日志读取失败；已有数据可能不是最新状态" else "连接读取失败；已有数据可能不是最新状态")
                    MainButton("重试"){vm.repo.retryRuntime()} }
            }
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
                        ActivityConnectionRow(connection.title,connection.subtitle,applications[connection.uid]) {
                                vm.repo.saveDraft("connection", connection.json)
                                go("connection")
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun ProfileUpdateToolbar(s:AppState,vm:MainViewModel) {
    val busy by vm.busy.collectAsState()
    val updating=s.subscriptionUpdates.values.any{it==SubscriptionUpdateStatus.DOWNLOADING}
    IconButton(onClick={vm.run {val result=vm.repo.updateSubscriptions();vm.repo.message(result)}},
        enabled=!busy && !updating && s.profiles.any{it.source.isNotBlank()},modifier=Modifier.testTag("profiles-update-all")) {
        if(updating)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
        else Icon(Icons.Outlined.Sync,tr("更新全部订阅"))
    }
}
