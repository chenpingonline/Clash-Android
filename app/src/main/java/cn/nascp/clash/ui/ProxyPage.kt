package cn.nascp.clash.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.nascp.clash.MainViewModel
import cn.nascp.clash.data.AppState
import cn.nascp.clash.data.VpnPhase
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject

/** Shared toolbar and list state; tests use the same controller with a fake delay function. */
@Stable internal class ProxyUiState(private val scope: CoroutineScope, private val delayTest: suspend (String) -> Long) {
    var query by mutableStateOf("")
    var protocol by mutableStateOf("")
    var showSearch by mutableStateOf(false)
    var showFilters by mutableStateOf(false)
    var sort by mutableStateOf("original")
    val results = mutableStateMapOf<String, String>()
    var testing by mutableStateOf(false)
        private set
    var completed by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    private var job: Job? = null

    fun test(names: List<String>) {
        if (testing || names.isEmpty()) return
        val targets = names.distinct()
        testing = true; completed = 0; total = targets.size
        job = scope.launch {
            try {
                val gate = Semaphore(4)
                coroutineScope {
                    targets.map { name -> async {
                        gate.withPermit {
                            results[name] = "测试中"
                            val result = try { "${delayTest(name)} ms" }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { "失败" }
                            results[name] = result
                            completed++
                        }
                    } }.awaitAll()
                }
            } finally {
                results.entries.filter { it.value == "测试中" }.map { it.key }.forEach(results::remove)
                testing = false
            }
        }
    }
    fun stop() { job?.cancel() }
}

@Composable internal fun rememberProxyUiState(s: AppState, vm: MainViewModel): ProxyUiState {
    val scope = rememberCoroutineScope()
    val state = remember(s.selected?.id, s.activeId, s.phase == VpnPhase.RUNNING) { ProxyUiState(scope, vm.repo::delayTest) }
    DisposableEffect(state, s.phase) { onDispose { state.stop() } }
    return state
}

// Only presentation fields participate in equality. Core history/alive updates must not
// invalidate the whole list. JSON traversal happens off the UI thread on each poll.
@Immutable internal data class ProxyNode(val name: String, val type: String, val current: String, val group: Boolean) {
    val testable: Boolean = name !in setOf("REJECT", "REJECT-DROP", "PASS") &&
        type.lowercase() !in setOf("reject", "rejectdrop", "pass")
}
@Immutable internal data class ProxyGroup(val name: String, val type: String, val current: String, val nodes: List<ProxyNode>) {
    // Built with the catalog on Dispatchers.Default, before a tab switch needs them.
    val singleRows = nodes.chunked(1)
    val doubleRows = nodes.chunked(2)
    val targets = nodes.filter { it.testable }.map { it.name }.toSet()
}
@Immutable internal data class ProxyCatalog(val groups: List<ProxyGroup> = emptyList()) {
    val leaves = groups.flatMap { it.nodes }.filter { !it.group && it.testable }.distinctBy { it.name }
}
internal fun proxyCatalog(proxies: JSONObject, groupOrder: List<String> = emptyList()): ProxyCatalog = ProxyCatalog(
    (groupOrder.asSequence() + proxies.keys().asSequence()).distinct().filter { proxies.optJSONObject(it)?.has("all") == true }.map { name ->
        val group = proxies.getJSONObject(name)
        val all = group.getJSONArray("all")
        ProxyGroup(name, group.optString("type"), group.optString("now"),
            (0 until all.length()).map(all::getString).distinct().map { node ->
                val detail = proxies.optJSONObject(node)
                ProxyNode(node, detail?.optString("type").orEmpty(), detail?.optString("now").orEmpty(), detail?.has("all") == true)
            })
    }.toList())

@Composable internal fun rememberProxyCatalog(proxies: JSONObject, groupOrder: List<String> = emptyList()): ProxyCatalog {
    val catalog by produceState(ProxyCatalog(), proxies, groupOrder) {
        if (proxies.length() == 0) value = ProxyCatalog()
        else value = withContext(Dispatchers.Default) { proxyCatalog(proxies, groupOrder) }
    }
    return catalog
}
private fun members(group: ProxyGroup, query: String, protocol: String, sort: String, results: Map<String, String>): List<ProxyNode> =
    group.nodes.filter { it.name.contains(query, true) && it.type.contains(protocol, true) }.let { nodes ->
        when (sort) {
            "name" -> nodes.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            "delay" -> nodes.sortedBy { results[it.name]?.substringBefore(" ms")?.toLongOrNull() ?: Long.MAX_VALUE }
            else -> nodes
        }
    }
@Composable internal fun ProxyToolbar(s: AppState, vm: MainViewModel, ui: ProxyUiState, go: (String) -> Unit,
                                     catalog: ProxyCatalog = rememberProxyCatalog(s.visibleProxies, s.visibleProxyGroupOrder)) {
    val busy by vm.busy.collectAsState()
    var menu by remember { mutableStateOf(false) }
    val query = ui.query; val protocol = ui.protocol
    val names = remember(catalog, query, protocol) {
        catalog.leaves.filter { it.name.contains(query, true) && it.type.contains(protocol, true) }.map(ProxyNode::name)
    }
    IconButton(onClick = { ui.showSearch = !ui.showSearch }) { Icon(Icons.Outlined.Search, tr("搜索节点名称")) }
    TextButton(onClick = { ui.test(names) }, enabled = s.phase == VpnPhase.RUNNING && names.isNotEmpty() && !ui.testing && !busy) {
        UiText("一键测速")
    }
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, tr("更多操作")) }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { UiText("筛选与排序") }, onClick = { menu = false; ui.showFilters = !ui.showFilters })
            DropdownMenuItem(text = { UiText("编辑节点") }, onClick = { menu = false; go("node-edit") })
            DropdownMenuItem(text = { UiText("编辑代理组") }, onClick = { menu = false; go("group-edit") })
        }
    }
}

@Composable internal fun ProxyList(s: AppState, vm: MainViewModel, go: (String) -> Unit,
                                  ui: ProxyUiState = rememberProxyUiState(s, vm),
                                  catalog: ProxyCatalog = rememberProxyCatalog(s.visibleProxies, s.visibleProxyGroupOrder)) {
    ProxyListContent(catalog, s.selected?.id, s.phase == VpnPhase.RUNNING, vm, go, ui, s)
}

@Composable private fun ProxyListContent(catalog: ProxyCatalog, profileId: String?, running: Boolean,
                                        vm: MainViewModel, go: (String) -> Unit, ui: ProxyUiState, state: AppState) {
    val groups = catalog.groups
    var expanded by rememberSaveable(profileId) { mutableStateOf(listOf<String>()) }
    var initialized by rememberSaveable(profileId) { mutableStateOf(false) }
    val busy by vm.busy.collectAsState()
    LaunchedEffect(groups) {
        if (!initialized && groups.isNotEmpty()) {
            expanded = listOf((groups.firstOrNull { it.name != "GLOBAL" } ?: groups.first()).name)
            initialized = true
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Preserve readable names and action targets at accessibility font sizes.
        val columns = if (maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.3f) 1 else 2
        val query = ui.query; val protocol = ui.protocol; val sort = ui.sort
        // Read the result table here only for delay sorting. Normal scrolling and
        // individual delay completions do not rebuild every group's rows.
        val sortResults = if (sort == "delay") ui.results.toMap() else emptyMap()
        val prepared = remember(catalog, query, protocol, sort, sortResults, columns) {
            groups.map { group ->
                if (query.isEmpty() && protocol.isEmpty() && sort == "original") {
                    Triple(group, if (columns == 1) group.singleRows else group.doubleRows, group.targets)
                } else {
                    val nodes = members(group, query, protocol, sort, sortResults)
                    Triple(group, nodes.chunked(columns), nodes.filter { it.testable }.map { it.name }.toSet())
                }
            }
        }
        // Render the default open group in the first layout, rather than replacing a
        // header-only frame with a viewport of cards in the middle of the slide.
        val defaultGroup = (groups.firstOrNull { it.name != "GLOBAL" } ?: groups.firstOrNull())?.name
        val visibleExpanded = if (!initialized && defaultGroup != null) listOf(defaultGroup) else expanded
        LazyColumn(Modifier.fillMaxSize().testTag("proxy-groups"),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp,
                bottom = 24.dp + LocalDockClearance.current)) {
            if (ui.showSearch || ui.query.isNotEmpty()) item(key = "search") {
                AppTextField(ui.query, { ui.query = it }, Modifier.animateItem().fillMaxWidth().padding(bottom = 12.dp),
                    singleLine = true, placeholder = { UiText("搜索节点名称") },
                    trailingIcon = { IconButton(onClick = { ui.query = ""; ui.showSearch = false }) { Icon(Icons.Outlined.Close, tr("收起搜索")) } })
            }
            if (ui.showFilters) item(key = "filters") {
                Column(Modifier.animateItem().padding(bottom = 12.dp)) {
                    AppTextField(ui.protocol, { ui.protocol = it }, Modifier.fillMaxWidth(), label = { UiText("协议类型筛选（可留空）") })
                    FlowRow {
                        listOf("original" to "原始排序", "name" to "按名称排序", "delay" to "按延迟排序").forEach { (key, label) ->
                            TextButton(onClick = { ui.sort = key }) { UiText(label, color = if (ui.sort == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
            if (!running) item(key = "stopped") {
                Info(when {
                    state.selected == null -> "请先导入或选择配置"
                    !state.previewReady || state.previewProfile != state.selected -> "正在读取配置中的节点和分组"
                    state.previewError -> "配置预览失败，请检查配置与增强"
                    else -> "可预选手动分组节点，启动 VPN 后生效；测速需启动 VPN"
                })
            }
            if (!running && state.pendingProviders.isNotEmpty()) item(key = "providers") {
                Info(tr("以下节点提供商暂无可用预览，启动 VPN 加载后可查看：") + state.pendingProviders.joinToString("、"))
            }
            if (ui.testing) item(key = "progress") {
                Column(Modifier.padding(bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UiText("${tr("正在测速")} ${ui.completed}/${ui.total}", Modifier.weight(1f))
                        TextButton(onClick = ui::stop) { UiText("停止") }
                    }
                    LinearProgressIndicator(progress = { if (ui.total == 0) 0f else ui.completed.toFloat() / ui.total }, modifier = Modifier.fillMaxWidth())
                }
            }
            if (running && state.proxyError) item(key = "runtime-error") {
                Panel {
                    Info("代理组读取失败；已有数据可能不是最新状态")
                    MainButton("重试"){vm.repo.retryRuntime()}
                    SubpageTextButton(onClick={go("core")}){UiText("查看内核状态")}
                }
            } else if (running && groups.isEmpty()) item(key = "loading") { Info(if(state.proxyLoaded)"当前没有可用代理组" else "正在读取代理组") }
            prepared.forEach { (group, rows, targets) ->
                val name = group.name
                val open = name in visibleExpanded
                item(key = "group:$name") {
                    GroupHeader(name, group.type, group.current, open, rows.sumOf { it.size }, running = running,
                        modifier = Modifier.animateItem(fadeInSpec = tween(160), fadeOutSpec = tween(120), placementSpec = tween(260, easing = FastOutSlowInEasing)),
                        canTest = running && targets.isNotEmpty() && !ui.testing && !busy,
                        toggle = { initialized = true; expanded = if (open) visibleExpanded - name else visibleExpanded + name },
                        test = { ui.test(targets.toList()) })
                }
                if (open) {
                    if (rows.isEmpty()) item(key = "empty:$name") {
                        Box(Modifier.fillMaxWidth().padding(top = 6.dp).proxyNodePanel(first = true, last = true).padding(16.dp)) {
                            UiText("没有匹配的节点", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    itemsIndexed(rows, key = { _, row -> "nodes:$name:${row.joinToString("\u0000") { it.name }}" }, contentType = { _, _ -> "proxy-row" }) { index, row ->
                        Row(Modifier.animateItem(fadeInSpec = tween(200), fadeOutSpec = tween(140),
                            placementSpec = tween(260, easing = FastOutSlowInEasing)).fillMaxWidth()
                            .padding(top = if (index == 0) 6.dp else 0.dp)
                            .proxyNodePanel(first = index == 0, last = index == rows.lastIndex)
                            .height(IntrinsicSize.Min)
                            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = if (index == rows.lastIndex) 8.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { node ->
                                ProxyTile(node.name, node.type, node.current, node.group, group.current == node.name,
                                    canSelect = state.phase in listOf(VpnPhase.STOPPED,VpnPhase.FAILED,VpnPhase.RUNNING) && group.type == "Selector" && !busy,
                                    result = if (running) ui.results[node.name] else null, modifier = Modifier.weight(1f).fillMaxHeight(),
                                    select = { vm.run { vm.repo.selectProxy(name, node.name) } })
                            }
                            if (row.size < columns) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                // Change the layout target once. Animating the gap's size would retarget
                // all following item placements on every frame and restart their easing.
                item(key = "gap:$name") {
                    Spacer(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null,
                        placementSpec = tween(260, easing = FastOutSlowInEasing)).height(if (open) 16.dp else 10.dp))
                }
            }
            if (groups.isEmpty()) item(key = "edit") {
                TextButton(onClick = { go("node-edit") }) { UiText("编辑节点") }
                TextButton(onClick = { go("group-edit") }) { UiText("编辑代理组") }
            }
        }
    }
}

/** A recessed node area spanning lazy rows without composing the whole group. */
@Composable private fun Modifier.proxyNodePanel(first: Boolean = false, last: Boolean = false): Modifier =
    background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(
        topStart = if (first) 16.dp else 0.dp, topEnd = if (first) 16.dp else 0.dp,
        bottomStart = if (last) 16.dp else 0.dp, bottomEnd = if (last) 16.dp else 0.dp))

@Composable private fun GroupHeader(name: String, groupType: String, current: String, expanded: Boolean, count: Int,
                                    canTest: Boolean, running: Boolean, modifier: Modifier = Modifier, toggle: () -> Unit, test: () -> Unit) {
    val large = LocalDensity.current.fontScale >= 1.3f
    val plain = LocalPlain.current
    val expansionLabel = tr(if (expanded) "已展开" else "已收起")
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, tween(240, easing = FastOutSlowInEasing), label = "groupChevron")
    Surface(onClick = toggle, shape = RoundedCornerShape(if (plain) 16.dp else 12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, lerp(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.onSurface, 0.06f)),
        modifier = modifier.fillMaxWidth().testTag("proxy-group:$name").semantics {
            stateDescription = expansionLabel
        }) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UiText(if (!plain) "$name · $count" else name, Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleMedium, fontWeight = if (plain) FontWeight.Medium else FontWeight.SemiBold)
                        if (plain) Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            UiText("$count", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val type = when (groupType) { "Selector" -> "手动"; "URLTest" -> "自动选择"; "Fallback" -> "故障转移"; "LoadBalance" -> "负载均衡"; else -> groupType }
                    UiText(if (running) "${tr("当前")}：${current.ifBlank { "—" }} · ${tr(type)}" else (if (current.isBlank()) tr("配置预览") else "${tr("预选")}：$current") + " · ${tr(type)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Keep the same controls and text width in both expansion states.
                if (!large) {
                    TextButton(onClick = test, enabled = canTest, contentPadding = PaddingValues(horizontal = 8.dp)) { UiText("测本组") }
                }
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(22.dp).rotate(chevron))
            }
            if (large) TextButton(onClick = test, enabled = canTest) { UiText("测本组") }
        }
    }
}

@Composable internal fun ProxyTile(name: String, type: String, current: String, group: Boolean, selected: Boolean, canSelect: Boolean,
                                 result: String?, modifier: Modifier, select: () -> Unit) {
    val plain = LocalPlain.current
    val primary = if (plain) PlainBlue else MaterialTheme.colorScheme.primary
    val resultColor = when {
        result == "失败" -> MaterialTheme.colorScheme.error
        result?.endsWith(" ms") == true -> MaterialTheme.colorScheme.tertiary
        plain -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(modifier.testTag("proxy-node:$name").selectable(selected = selected, enabled = canSelect, role = Role.RadioButton, onClick = select),
        shape = RoundedCornerShape(if (plain) 12.dp else 10.dp),
        color = when { plain && selected -> MaterialTheme.colorScheme.primaryContainer; selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f); else -> MaterialTheme.colorScheme.surface },
        border = BorderStroke(1.dp, if (selected) primary else lerp(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.onSurface, 0.05f))) {
        Row(Modifier.heightIn(min = 64.dp).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                UiText(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    color = if (selected && !plain) primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    UiText(if (group) tr("代理组") + if (current.isBlank()) "" else " · $current" else type,
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            UiText(result ?: "—", Modifier.testTag("proxy-delay:$name"),
                style = MaterialTheme.typography.bodySmall, color = resultColor)
        }
    }
}
