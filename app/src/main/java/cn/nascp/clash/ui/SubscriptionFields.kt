package cn.nascp.clash.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.saveable.rememberSaveable
import cn.nascp.clash.data.*
import kotlinx.coroutines.launch

@Composable fun SubscriptionUserAgentField(value: String, onChange: (String) -> Unit) {
    var custom by rememberSaveable { mutableStateOf(value.takeUnless { it == DEFAULT_SUBSCRIPTION_UA }.orEmpty()) }
    var customMode by rememberSaveable { mutableStateOf(value != DEFAULT_SUBSCRIPTION_UA) }
    LaunchedEffect(value) { if (value == DEFAULT_SUBSCRIPTION_UA && custom != value) customMode = false }
    val selected = if (!customMode && value == DEFAULT_SUBSCRIPTION_UA) DEFAULT_SUBSCRIPTION_UA else "custom"
    Panel {
        UiText("订阅 User-Agent")
        Choices(listOf(DEFAULT_SUBSCRIPTION_UA to "默认", "custom" to "自定义"), selected) {
            customMode = it == "custom"
            if (!customMode && value != DEFAULT_SUBSCRIPTION_UA) custom = value
            onChange(if (customMode) custom else DEFAULT_SUBSCRIPTION_UA)
        }
        if (selected == "custom") Field("自定义 User-Agent", value) { custom=it; onChange(it) }
        else UiText(value)
        UiText("默认使用 mihomo；其他 User-Agent 请自定义，仅用于此订阅的下载与更新。")
    }
}

@Composable fun SubscriptionConnectionDiagnostics(address: String, userAgent: String, onChoose: (String) -> Unit) {
    // Editing the address or identity disposes the scope, cancels in-flight work and clears stale results.
    key(address, userAgent) { SubscriptionDiagnosticsContent(address, userAgent, onChoose) }
}

@Composable private fun SubscriptionDiagnosticsContent(address: String, userAgent: String, onChoose: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var running by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<SubscriptionProbeResult>() }
    val valid = address.isNotBlank() && runCatching { subscriptionUserAgent(userAgent) }.isSuccess
    Panel {
        UiText("手动连接诊断")
        UiText("对比当前与默认 User-Agent，相同值只测一次。响应耗时含DNS、TLS和跳转，不含完整下载，受缓存及网络波动影响。", style=MaterialTheme.typography.bodySmall)
        MainButton(if(running) "正在对比…" else "开始对比", valid && !running) {
            running=true;cancelled=false;error="";results.clear()
            job=scope.launch {
                try { diagnoseSubscription(address,userAgent) { results.add(it) } }
                catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                catch (failure: Exception) { error=failure.message ?: "订阅连接诊断失败" }
                finally { running=false }
            }
        }
        if(running) SubpageTextButton(modifier=Modifier.fillMaxWidth(),onClick={cancelled=true;job?.cancel()}) { UiText("取消诊断") }
        if(cancelled) UiText("诊断已取消")
        if(error.isNotBlank()) UiText(error)
        results.forEach { result ->
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    UiText(result.label,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
                    UiText("${result.status?.let { "HTTP $it" } ?: "未取得最终响应"} · ${result.elapsedMillis} ms",style=MaterialTheme.typography.bodyMedium)
                }
                UiText(result.userAgent,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                result.redirects?.let { UiText("跳转${it}次",style=MaterialTheme.typography.bodySmall) }
                if(result.error != null) UiText(result.error,style=MaterialTheme.typography.bodySmall)
                if(result.successful) SubpageTextButton(modifier=Modifier.fillMaxWidth(),
                    onClick={onChoose(result.userAgent)}, enabled=!running && result.userAgent != userAgent
                ) { UiText(if(result.userAgent == userAgent) "当前已选择" else "使用${result.label}身份") }
            }
        }
        if(results.isNotEmpty()) UiText("HTTP成功仅表示服务器已响应，尚未校验配置。选择身份后需保存配置或订阅信息才会持久生效。",style=MaterialTheme.typography.bodySmall)
    }
}
