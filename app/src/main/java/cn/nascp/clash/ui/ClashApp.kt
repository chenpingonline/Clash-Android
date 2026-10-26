@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.nascp.clash.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.nascp.clash.update.AppUpdateViewModel
import cn.nascp.clash.MainViewModel
import cn.nascp.clash.data.AppState
import cn.nascp.clash.data.VpnPhase
import org.json.JSONObject
import java.net.URLEncoder

// Scroll content extends behind the floating dock; only its trailing space clears it.
internal val LocalDockClearance = staticCompositionLocalOf { 0.dp }

// Keep the panel's outline and backdrop until the final part of its travel.
private fun pageSeparation(displacement: Float): Float {
    val fraction = (displacement / 0.18f).coerceIn(0f, 1f)
    return fraction * fraction * (3f - 2f * fraction)
}

private val tabs = listOf("home", "proxies", "profiles", "activity", "settings")
private val labels = listOf("首页", "代理", "配置", "活动", "设置")
@Composable fun ClashApp(onStart: () -> Unit, onStop: () -> Unit, vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val proxyUi = rememberProxyUiState(state, vm)
    // Share the snapshot between the toolbar and list, and prepare it while Home is visible.
    val proxyCatalog = key(state.selected, state.previewRevision, state.phase == VpnPhase.RUNNING) { rememberProxyCatalog(state.visibleProxies) }
    val messageId=state.messageId
    val messageText=state.error
    val messageKind=state.messageKind
    var stack by rememberSaveable { mutableStateOf(listOf("home")) }
    val route = stack.last()
    val updates: AppUpdateViewModel = viewModel()
    // Use the committed route, so recomposition and cancelled back gestures do not repeat checks.
    LaunchedEffect(route) {
        if (route == "about") updates.check(automatic = true)
    }
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(route,lifecycle,vm.repo) {
        fun updateVisibility() {
            val visible=lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
            vm.repo.foregroundVisible(visible)
            vm.repo.homeVisible(route=="home" && visible)
        }
        val observer=androidx.lifecycle.LifecycleEventObserver { _,_->updateVisibility() }
        lifecycle.addObserver(observer);updateVisibility()
        onDispose { lifecycle.removeObserver(observer);vm.repo.homeVisible(false);vm.repo.foregroundVisible(false) }
    }
    val focusManager=androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val savedPages=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val go: (String) -> Unit = { destination ->
        val target=if(destination=="group")"group-edit" else destination
        if(stack.last()!=target){focusManager.clearFocus();keyboard?.hide();stack=stack+target}
    }
    val back: () -> Unit = {
        focusManager.clearFocus();keyboard?.hide()
        if (stack.size > 1) stack = stack.dropLast(1)
    }
    val navigationState = remember { SeekableTransitionState(stack) }
    val navigation = rememberTransition(navigationState, label = "navigation")
    var predictiveBack by remember { mutableStateOf(false) }
    val tabEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    val enterEasing = CubicBezierEasing(0.18f, 0.72f, 0.2f, 1f)
    val returnEasing = CubicBezierEasing(0.2f, 0.5f, 0.2f, 1f)
    LaunchedEffect(stack, predictiveBack) {
        if (!predictiveBack) {
            val remaining = when {
                navigationState.targetState == stack -> 1f - navigationState.fraction
                navigationState.currentState == stack -> navigationState.fraction
                else -> 1f
            }
            val tabSwitch = navigationState.currentState.last() in tabs && stack.last() in tabs
            val cancelled = navigationState.currentState == stack && navigationState.targetState != stack
            val returning = stack.size < maxOf(navigationState.currentState.size, navigationState.targetState.size)
            val duration = when {
                tabSwitch -> 260
                cancelled -> 260
                returning -> 440
                else -> 560
            }
            // Preserve a short settling phase even when a gesture ends close to an edge.
            val minimum = if (!tabSwitch && remaining > 0.001f) 120 else 1
            navigationState.animateTo(stack, tween((duration * remaining).toInt().coerceAtLeast(minimum),
                easing = if (tabSwitch) tabEasing else if (returning || cancelled) returnEasing else enterEasing))
        }
    }
    PredictiveBackHandler(enabled = stack.size > 1) { progress ->
        val origin = stack
        predictiveBack = true
        try {
            // Consume the gesture without moving the page. Normal completion means
            // the user released to commit; cancellation must leave the stack intact.
            progress.collect { }
            if (stack == origin) back()
        } finally {
            // On release, the normal stack transition animates the confirmed return.
            predictiveBack = false
        }
    }
    ClashTheme(state.theme,state.language,state.fontScale,state.fontWeight) {
        // AnimatedContent draws outgoing and incoming pages together. Each source must
        // own its recording layers; sharing one lets the later page overwrite both.
        val dockBackdrops = tabs.associateWith { tab -> key(tab) { rememberDockBackdrop() } }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (LocalSageGlass.current) androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(cn.nascp.clash.R.drawable.sage_mist),
            contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, alpha = 0.6f,
            modifier = Modifier.fillMaxSize())
        if (LocalPearlGlass.current) Box(Modifier.fillMaxSize().background(
            androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFF7F9FE), Color(0xFFEEF5FF), Color(0xFFF4F0FC), Color(0xFFF8FAFD)))))
        val layoutDirection = LocalLayoutDirection.current
        val sharedDock = navigation.currentState.last() in tabs && navigation.targetState.last() in tabs
        navigation.AnimatedContent(modifier = Modifier.fillMaxSize().clipToBounds(),
            contentKey = { it.last() },
            transitionSpec = {
                val from = initialState.last(); val to = targetState.last()
                val tabSwitch = from in tabs && to in tabs
                val direction = (if (layoutDirection == LayoutDirection.Rtl) -1 else 1)
                val motion = tween<androidx.compose.ui.unit.IntOffset>(360, easing = LinearEasing)
                if (tabSwitch) {
                    val tabDirection = (if (tabs.indexOf(to) > tabs.indexOf(from)) 1 else -1) * direction
                    (slideInHorizontally(motion) { tabDirection * it } togetherWith
                        slideOutHorizontally(motion) { -tabDirection * it }).using(null)
                } else if (targetState.size > initialState.size) {
                    (slideInHorizontally(motion) { direction * it } togetherWith
                        slideOutHorizontally(motion) { -direction * it / 4 })
                        .apply { targetContentZIndex = targetState.size.toFloat() }.using(null)
                } else {
                    (slideInHorizontally(motion) { -direction * it / 4 } togetherWith
                        slideOutHorizontally(motion) { direction * it })
                        .apply { targetContentZIndex = targetState.size.toFloat() }.using(null)
                }
            }) { visibleStack ->
        val route = visibleStack.last()
        val dockBackdrop = dockBackdrops[route]
        val navigate: (String) -> Unit = { if (stack == visibleStack && !predictiveBack) go(it) }
        val navigateBack: () -> Unit = { if (stack == visibleStack && !predictiveBack) back() }
        val displacement by navigation.animateFloat(
            transitionSpec = { tween(360, easing = LinearEasing) }, label = "pageExposure:$route"
        ) { if (it == visibleStack) 0f else 1f }
        val foreground = visibleStack.size > minOf(navigation.currentState.size, navigation.targetState.size)
        Box(Modifier.fillMaxSize()
            .then(if (visibleStack != stack) Modifier.clearAndSetSemantics {} else Modifier)
            .graphicsLayer {
            if (foreground) {
                val separation = pageSeparation(displacement)
                shape = RoundedCornerShape((40f * separation).dp)
                clip = true
                shadowElevation = 6.dp.toPx() * separation
            }
        }.background(MaterialTheme.colorScheme.background)) {
        Scaffold(containerColor = if (LocalSageGlass.current || LocalPearlGlass.current) Color.Transparent else MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(modifier = Modifier.testTag("route:$route"), title = { UiText(if (route in tabs) labels[tabs.indexOf(route)].let { if (route == "home") "Clash" else it } else screens[route]?.title ?: "详情") },
                    navigationIcon = { if (visibleStack.size > 1) IconButton(onClick = navigateBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, tr("返回")) } },
                    actions = {
                        if (route == "proxies") ProxyToolbar(state, vm, proxyUi, navigate, proxyCatalog)
                        if (route == "profiles") ProfileUpdateToolbar(state,vm)
                        if (route == "profiles") IconButton(onClick = { navigate("import") }) { Icon(Icons.Outlined.Add, tr("添加配置")) }
                        if (route == "home") IconButton(onClick = { navigate("appearance") }) { Icon(Icons.Outlined.Palette, tr("外观与主题")) }
                    }, colors = TopAppBarDefaults.topAppBarColors(containerColor = if (LocalSageGlass.current || LocalPearlGlass.current) Color.Transparent else MaterialTheme.colorScheme.background))
            }, bottomBar = {
                if (route in tabs) {
                    Box(if (sharedDock) Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics {} else Modifier) {
                        BottomDock(route, backdrop = if (LocalPlain.current || LocalPearlGlass.current) dockBackdrop else null) {
                            if (stack == visibleStack && !predictiveBack) stack = listOf(it)
                        }
                    }
                }
            }
        ) { padding ->
            val floatingDock = (LocalPearlGlass.current || LocalPlain.current) && route in tabs
            val layoutPadding = if (floatingDock) PaddingValues(
                start = padding.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current)
            ) else padding
            CompositionLocalProvider(LocalDockClearance provides (if (floatingDock) padding.calculateBottomPadding() else 0.dp), LocalOperationBusy provides busy) {
            Box(Modifier.padding(layoutPadding).fillMaxSize().imePadding()
                .then(if (floatingDock) Modifier.dockBackdropSource(requireNotNull(dockBackdrop), if (LocalPlain.current) MaterialTheme.colorScheme.background else Color.Transparent,
                    fadeColor = if (LocalPlain.current) MaterialTheme.colorScheme.background else null) else Modifier)) {
                Column(Modifier.fillMaxSize()) {
                AnimatedVisibility(route != "profiles" && (busy || state.phase in listOf(VpnPhase.STARTING, VpnPhase.STOPPING)),
                    enter = fadeIn(tween(120)) + expandVertically(tween(160)),
                    exit = fadeOut(tween(100)) + shrinkVertically(tween(160))) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                val visibleRoute = route
                    val scopeKey=if(visibleRoute in profileScopedRoutes)state.selectedId else "app"
                    val entityKey=when(visibleRoute) {
                        "rule-provider"->vm.repo.draft("rule-provider.selected.${state.selectedId}")
                        "remote-editor","remote-workspace"->vm.repo.draft("remote-selected")
                        else->""
                    }
                    savedPages.SaveableStateProvider("${state.editorEpoch}:$visibleRoute:$scopeKey:$entityKey") {
                    when (visibleRoute) {
                        "home" -> Home(state, onStart, onStop, navigate, vm)
                        "proxies" -> {
                            // Keep composition inside Scaffold: moving a precomposed lazy list
                            // across its subcomposition can corrupt Compose's slot table.
                            ProxyList(state, vm, navigate, proxyUi, proxyCatalog)
                        }
                        "profiles" -> ProfileList(state, vm, navigate)
                        "activity" -> ActivityPage(state, vm, navigate)
                        "settings" -> Settings(navigate)
                        else -> Secondary(visibleRoute, state, vm, navigate, navigateBack, onStart, onStop)
                    }
                    }
                }
                AnimatedVisibility(state.error.isNotBlank() && visibleStack==stack,
                    modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=if(floatingDock)padding.calculateBottomPadding()+8.dp else 8.dp),
                    enter=fadeIn(tween(150))+slideInVertically(tween(180)){it/3},
                    exit=fadeOut(tween(120))+slideOutVertically(tween(150)){it/3}) {
                    OperationNotice(state.error,state.messageKind) { vm.repo.clearMessage(messageId) }
                }
            }
        }
        }
        if (!foreground && navigation.currentState != navigation.targetState &&
            (navigation.currentState.size != navigation.targetState.size)) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = pageSeparation(displacement) * 0.20f }
                .background(Color.Black))
        }
        }
        }
        if (sharedDock) Box(Modifier.align(Alignment.BottomCenter)) {
            BottomDock(stack.last(), backdrop = if (LocalPlain.current || LocalPearlGlass.current) dockBackdrops[stack.last()] else null) {
                if (!predictiveBack) stack = listOf(it)
            }
        }
        }
        val accessibility=androidx.compose.ui.platform.LocalAccessibilityManager.current
        LaunchedEffect(messageId,messageText) {
            if(messageText.isNotBlank()) {
                val duration=if(messageKind==cn.nascp.clash.data.MessageKind.INFO)4000L else 8000L
                kotlinx.coroutines.delay(accessibility?.calculateRecommendedTimeoutMillis(duration,containsIcons=true,containsText=true,containsControls=true) ?: duration)
                vm.repo.clearMessage(messageId)
            }
        }
    }
}

@Composable fun Page(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 24.dp + LocalDockClearance.current), verticalArrangement = Arrangement.spacedBy(if (LocalPlain.current) 12.dp else 16.dp), content = content)
}

@Composable private fun Home(s: AppState, start: () -> Unit, stop: () -> Unit, go: (String) -> Unit, vm: MainViewModel) {
    if (LocalPlain.current) { PlainHome(s, start, stop, go, vm); return }
    val running = s.phase == VpnPhase.RUNNING
    Page {
        if (LocalPearlGlass.current) {
            UiText(vpnStatus(s.phase), color = if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
            Panel {
                val group = s.proxies.keys().asSequence().mapNotNull { s.proxies.optJSONObject(it) }.firstOrNull { it.has("all") && it.optString("now").isNotBlank() }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        UiText(if (running) group?.optString("now") ?: "选择当前节点" else "准备连接", style = MaterialTheme.typography.titleLarge)
                        UiText(s.selected?.name ?: "尚未导入", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = if (running) stop else start, enabled = s.ready && s.selected != null && s.phase !in listOf(VpnPhase.STARTING, VpnPhase.STOPPING)) { UiText(if (running) "停止" else "启动 VPN") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick = { go(if (running) "proxies" else if (s.selected == null) "import" else "profile") }) {
                    UiText(if (running) "切换节点" else if (s.selected == null) "导入配置" else "查看当前配置")
                    Spacer(Modifier.weight(1f)); Icon(Icons.Outlined.ChevronRight, null)
                }
            }
        } else if (LocalSageGlass.current) {
            Panel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        UiText(vpnStatus(s.phase), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        UiText(if (running) "选择当前节点" else "准备连接", style = MaterialTheme.typography.titleMedium)
                        UiText(if (running) "切换策略组与代理节点" else "导入配置，然后启动 VPN", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(onClick = if (running) stop else start,
                            enabled = s.ready && s.selected != null && s.phase !in listOf(VpnPhase.STARTING, VpnPhase.STOPPING),
                            modifier = Modifier.size(76.dp), shape = androidx.compose.foundation.shape.CircleShape) {
                            Icon(Icons.Outlined.PowerSettingsNew, tr(if (running) "停止" else "启动 VPN"), Modifier.size(36.dp))
                        }
                        UiText(if (running) "停止" else "启动 VPN", style = MaterialTheme.typography.labelLarge)
                    }
                }
                TextButton(onClick = { go(if (running) "proxies" else if (s.selected == null) "import" else "profile") }) {
                    UiText(if (running) "切换节点" else if (s.selected == null) "导入配置" else "查看当前配置")
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp))
                }
            }
        } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            UiText(when(s.phase) { VpnPhase.RUNNING -> "● VPN 已启动"; VpnPhase.STARTING -> "正在启动…"; VpnPhase.STOPPING -> "正在停止…"; VpnPhase.FAILED -> "启动失败"; else -> "VPN 未启动" }, color = if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = if (running) stop else start, enabled = s.ready && s.selected != null && s.phase !in listOf(VpnPhase.STARTING, VpnPhase.STOPPING)) { UiText(if (running) "停止" else "启动 VPN") }
        }
        Panel {
            Entry(if (running) "选择当前节点" else "准备连接", if (running) "切换策略组与代理节点" else "导入配置，然后启动 VPN", Icons.Outlined.Public) { go(if (running) "proxies" else "import") }
            MainButton(if (running) "切换节点" else if (s.selected == null) "导入配置" else "查看当前配置") { go(if (running) "proxies" else if (s.selected == null) "import" else "profile") }
        }
        }
        Choices(listOf("rule" to "规则", "global" to "全局", "direct" to "直连"), s.mode) { value -> vm.run { vm.repo.mode(value) } }
        Panel {
            UiText("实时流量", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("下载", bytes(s.down) + "/s", MaterialTheme.colorScheme.primary)
                Metric("上传", bytes(s.up) + "/s", MaterialTheme.colorScheme.secondary)
            }
            val values = remember { mutableStateListOf<Long>() }
            LaunchedEffect(s.down, s.totalDown) { values.add(s.down); if (values.size > 40) values.removeAt(0) }
            val color = MaterialTheme.colorScheme.primary
            val divider = MaterialTheme.colorScheme.outlineVariant
            Canvas(Modifier.fillMaxWidth().height(if (LocalPearlGlass.current) 52.dp else 92.dp)) {
                repeat(3) { val y = size.height * it / 2; drawLine(divider, Offset(0f,y), Offset(size.width,y), 1f) }
                val max = (values.maxOrNull() ?: 1L).coerceAtLeast(1).toFloat()
                values.zipWithNext().forEachIndexed { i, pair -> drawLine(color, Offset(size.width * i / 39, size.height * (1-pair.first/max)), Offset(size.width*(i+1)/39,size.height*(1-pair.second/max)), 3f) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("总上传", bytes(s.totalUp)); Metric("总下载", bytes(s.totalDown))
            }
        }
        Panel {
            Entry("当前配置", s.selected?.name ?: "尚未导入", Icons.Outlined.Description) { go(if (s.selected == null) "import" else "profile") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Entry("VPN 状态", if (running) "设备流量已接管 · 连通性需单独检测" else "未接管设备流量", Icons.Outlined.Shield) { go("vpn") }
        }
        HomeExitCard(s,vm.repo::refreshExitLocation)
    }
}

private fun vpnStatus(phase: VpnPhase): String = when (phase) {
    VpnPhase.RUNNING -> "● VPN 已启动"
    VpnPhase.STARTING -> "正在启动…"
    VpnPhase.STOPPING -> "正在停止…"
    VpnPhase.FAILED -> "启动失败"
    else -> "VPN 未启动"
}

@Composable private fun Metric(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) { Column {
    UiText(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    UiText(value, style = MaterialTheme.typography.titleLarge, color = color)
} }



@Composable private fun ProfileList(s: AppState, vm: MainViewModel, go: (String) -> Unit) {
    if (LocalPlain.current) { PlainProfileList(s, vm, go); return }
    Page {
        MainButton("添加配置") { go("import") }
        if (s.profiles.isEmpty()) Panel { UiText("还没有配置", style = MaterialTheme.typography.titleLarge); UiText("从订阅链接、本地文件或粘贴的 YAML 开始。") }
        s.profiles.forEach { profile -> Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                UiText(profile.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (profile.id == s.selectedId) UiText(if (profile.id == s.activeId) "运行中" else "已选择", color = MaterialTheme.colorScheme.primary)
            }
            UiText(if (profile.source.isBlank()) "本地配置" else "HTTPS订阅 · 地址已隐藏", color = MaterialTheme.colorScheme.onSurfaceVariant)
            UiText("更新于 ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(profile.updated))}", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { vm.run { vm.repo.selectProfile(profile.id) } }) { UiText("选择") }
                Button(onClick = { vm.run { if (profile.id != s.selectedId) vm.repo.selectProfile(profile.id); go("profile") } }) { UiText("管理") }
            }
        } }
    }
}

@Composable private fun ActivityPage(s: AppState, vm: MainViewModel, go: (String) -> Unit) {
    if (LocalPlain.current) { PlainActivityPage(s, vm, go); return }
    var tab by rememberSaveable { mutableStateOf("connections") }
    var query by rememberSaveable { mutableStateOf("") }
    var paused by rememberSaveable { mutableStateOf(false) }
    var logSnapshot by remember { mutableStateOf(emptyList<String>()) }
    val applications=rememberConnectionApplications(s.connections)
    val filteredConnections=remember(s.connections,query,applications) {
        (0 until s.connections.length()).map(s.connections::getJSONObject).filter { it.toString().contains(query,true) || applications[connectionUid(it)]?.searchText?.contains(query,true)==true }
    }
    val context=LocalContext.current
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")){uri->if(uri!=null)vm.run{val content=(if(paused)logSnapshot else s.logs).joinToString("\n");withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{it.write(content.toByteArray())}?:error("无法导出日志")}}}
    var closeAll by remember{mutableStateOf(false)}
    Page(sectionMotion(tab)) {
        Choices(listOf("connections" to "连接", "logs" to "日志"), tab) { tab = it }
        Entry("运行规则",icon=settingsScreenIcon("rules")){go("rules")}
        AppTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { UiText(if(tab=="logs")"搜索日志" else "搜索应用、包名、域名或 IP") }, singleLine = true)
        if (s.phase != VpnPhase.RUNNING) Info("VPN未启动，暂无实时活动。")
        if (tab == "logs") {
            Info("内核日志已隐藏 URL 与常见凭据字段；导出前仍请检查内容。")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)){TextButton(onClick={if(!paused)logSnapshot=s.logs;paused=!paused}){UiText(if(paused)"继续" else "暂停")};TextButton(onClick={export.launch("clash-logs.txt")}){UiText("导出日志")}}
            Panel { if (s.logs.isEmpty()) UiText("暂无日志"); (if(paused)logSnapshot else s.logs).filter { it.contains(query,true) }.takeLast(100).forEach { UiText(it, style = MaterialTheme.typography.bodyMedium) } }
        } else {
            UiText("活动连接 (${s.connections.length()})", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick={closeAll=true}){UiText("关闭筛选结果中的连接")}
            if(closeAll)InlineConfirmation("关闭筛选连接？","仅关闭当前列表中与搜索内容匹配的连接；新连接不包含在此次操作。","关闭",onDismiss={closeAll=false}) {
                val ids=filteredConnections.map{it.getString("id")}
                closeAll=false;vm.run{ids.forEach{vm.repo.api("/connections/${encode(it)}","DELETE")}}
            }
            Panel {
                if (s.connections.length() == 0) UiText("暂无活动连接", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.fillMaxWidth().heightIn(min=80.dp,max=480.dp)){items(filteredConnections,key={it.optString("id")}) { connection ->
                    val m = connection.optJSONObject("metadata") ?: JSONObject()
                    ActivityConnectionRow(m.optString("host").ifBlank { m.optString("destinationIP") }, "${m.optString("network").uppercase()} · ↓ ${bytes(connection.optLong("download"))} · ↑ ${bytes(connection.optLong("upload"))}", applications[connectionUid(connection)]) {
                        vm.repo.saveDraft("connection", connection.toString()); go("connection")
                    }
                }}
            }
        }
    }
}

@Composable private fun Settings(go: (String) -> Unit) = SettingsHome(go)

fun encode(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
