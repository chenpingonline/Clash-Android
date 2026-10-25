package cn.nascp.clash.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.nascp.clash.MainViewModel
import cn.nascp.clash.data.*
import cn.nascp.clash.system.updateSubscriptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable fun HistoryPage(vm:MainViewModel) {
    val state by vm.repo.state.collectAsState()
    val id=state.selectedId
    var history by remember{mutableStateOf(vm.repo.history(id))}
    var preview by rememberSaveable{mutableStateOf("")}
    var selected by remember{mutableStateOf(-1)}
    var confirm by remember{mutableStateOf(false)}
    Page {
        Info("保存原始配置或增强前自动保留快照，最多20个。恢复前将重新校验，当前状态也会保留。")
        if(history.length()==0)Info("还没有历史记录")
        for(i in 0 until history.length()) { val item=history.getJSONObject(i);Panel{
            UiText(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.getLong("time"))))
            MainButton("预览版本差异"){selected=i;val before=vm.repo.state.value.selected?.yaml.orEmpty().lines();val after=item.getString("yaml").lines();preview=(0 until maxOf(before.size,after.size)).joinToString("\n"){line->val a=before.getOrNull(line);val b=after.getOrNull(line);if(a==b)"  ${a.orEmpty()}"else "- ${a.orEmpty()}\n+ ${b.orEmpty()}"}+"\n\n增强快照：\n"+item.optJSONObject("extensions").toString()}
        } }
        if(selected>=0){UiText(preview);MainButton("恢复预览版本"){confirm=true}}
        if(confirm)InlineConfirmation("恢复此版本？","将恢复原始配置和该版本增强。需要停止VPN。","恢复",destructive=true,onDismiss={confirm=false}) {
            confirm=false;vm.run{vm.repo.restoreHistory(selected);history=vm.repo.history(id);preview="";selected=-1;vm.repo.message("版本已恢复")}
        }
    }
}

@Composable fun BackupPage(vm:MainViewModel) {
    val context=LocalContext.current
    var settings by rememberSaveable{mutableStateOf(true)}
    var incoming by remember {mutableStateOf("")}
    var summary by remember {mutableStateOf("")}
    val yamlExport=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/yaml")){uri->if(uri!=null)vm.run{val yaml=vm.repo.state.value.selected?.yaml?:error("未选择配置");withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{it.write(yaml.toByteArray())}?:error("无法导出")};vm.repo.message("已导出原始配置")}}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)vm.run{val text=vm.repo.backup(settings);withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{it.write(text.toByteArray())}?:error("无法写入文件")};vm.repo.message("备份已导出")}}
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)vm.run{val text=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{String(it.readBytesLimited(32*1024*1024))}?:error("无法读取备份")};val root=vm.repo.validateBackup(text);incoming=text;summary="校验通过：${root.getJSONArray("profiles").length()}个配置。恢复将替换本地配置与备份中的设置。"}}
    Page {
        Info("备份包含节点密码、订阅地址；包含设置时也包含远程令牌。文件为明文 JSON，请妥善保存。")
        CheckRow("包含应用设置及远程实例",settings){settings=it}
        MainButton("导出完整备份"){export.launch("clash-backup.json")}
        MainButton("导出当前 YAML"){yamlExport.launch("clash-config.yaml")}
        MainButton("选择备份并校验"){restore.launch(arrayOf("application/json","text/plain","application/octet-stream"))}
        if(incoming.isNotEmpty())InlineConfirmation("恢复备份",summary,"确认恢复",destructive=true,onDismiss={incoming=""}) {
            vm.run{vm.repo.restoreBackup(incoming);incoming="";vm.repo.message("备份已恢复")}
        }
    }
}

@Composable fun DiagnosticsPage(vm:MainViewModel) {
    val context=LocalContext.current
    val text=remember {vm.repo.diagnostics()}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->if(uri!=null)vm.run{withContext(Dispatchers.IO){val output=context.contentResolver.openOutputStream(uri)?:error("无法导出");java.util.zip.ZipOutputStream(output).use{zip->zip.putNextEntry(java.util.zip.ZipEntry("diagnostics.json"));zip.write(text.toByteArray());zip.closeEntry();zip.putNextEntry(java.util.zip.ZipEntry("README.txt"));zip.write("不含原始配置、订阅地址、令牌和连接目标。详细日志请在活动页自行检查后导出。".toByteArray());zip.closeEntry()}};vm.repo.message("诊断包已导出")}}
    Page{Panel{UiText(text)};Info("诊断包仅包含运行摘要，不包含连接目的地址与原始日志。") ; MainButton("导出诊断 ZIP"){export.launch("clash-diagnostics.zip")}}
}

@Composable fun RemotePage(vm:MainViewModel) {
    var instances by remember{mutableStateOf(vm.repo.remotes())}
    var current by remember{mutableStateOf<RemoteInstance?>(null)}
    var name by rememberSaveable{mutableStateOf("")};var address by rememberSaveable{mutableStateOf("")};var token by rememberSaveable{mutableStateOf("")}
    var result by remember{mutableStateOf("")};var content by remember{mutableStateOf(JSONObject())};var tab by rememberSaveable{mutableStateOf("proxies")}
    var tested by remember{mutableStateOf(false)}
    var logLines by remember{mutableStateOf(emptyList<String>())}
    var logPaused by rememberSaveable{mutableStateOf(false)}
    LaunchedEffect(current,tab,tested,logPaused){if(tested&&tab=="logs"&&!logPaused)remoteLogs(current!!).collect{line->logLines=(logLines+line).takeLast(100)}}
    Page(sectionMotion(tab)) {
        Info("独立远程工作区 · 以下操作仅作用于所选远程实例，不改变本机VPN。使用标准Mihomo API；不假定Manager扩展可用。")
        instances.forEach{instance->Panel{UiText(instance.name);FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)){SubpageTextButton(onClick={current=instance;name=instance.name;address=instance.address;token=instance.token;tested=false;content=JSONObject()}){UiText("选择")};SubpageTextButton(onClick={instances=instances.filterNot{it.id==instance.id};vm.repo.saveRemotes(instances);if(current?.id==instance.id){current=null;tested=false}}){UiText("删除", color = MaterialTheme.colorScheme.error)}}}}
        Field("实例名称",name){name=it};Field("HTTPS 控制器地址",address){address=it;tested=false};Field("访问令牌",token,true){token=it;tested=false}
        MainButton("测试并保存"){vm.run{require(name.isNotBlank());val candidate=RemoteInstance(current?.id?:java.util.UUID.randomUUID().toString(),name,address,token);val version=remoteApi(candidate,"/version");current=candidate;instances=instances.filterNot{it.id==candidate.id}+candidate;vm.repo.saveRemotes(instances);tested=true;result="连接成功 · ${version.optString("version")}"}}
        SubpageTextButton(modifier=Modifier.fillMaxWidth(),onClick={current=null;name="";address="";token="";tested=false;content=JSONObject()}){UiText("新增实例")}
        if(result.isNotBlank())UiText(result)
        Choices(listOf("proxies" to "代理","connections" to "连接","rules" to "规则","logs" to "日志"),tab){tab=it;content=JSONObject()}
        if(tested)Row{listOf("rule","global","direct").forEach{mode->SubpageTextButton(onClick={vm.run{remoteApi(current!!,"/configs","PATCH",JSONObject().put("mode",mode));result="远程模式已更新：$mode"}}){UiText(mode)}}}
        if(tab=="logs") { SubpageTextButton(modifier=Modifier.fillMaxWidth(),onClick={logPaused=!logPaused}){UiText(if(logPaused)"继续" else "暂停")};UiText(logLines.joinToString("\n")) }
        else MainButton("刷新远程数据",tested){vm.run{content=remoteApi(current!!,"/$tab")}}
        if(tested && tab=="proxies") {
            val proxies=content.optJSONObject("proxies")?:JSONObject()
            proxies.keys().forEach{key->val group=proxies.optJSONObject(key)?:JSONObject();val nodes=group.optJSONArray("all");if(nodes!=null)Panel{UiText("$key · ${group.optString("now")}");for(i in 0 until nodes.length()){val node=nodes.getString(i);SubpageTextButton(enabled=group.optString("type")=="Selector",onClick={vm.run{remoteApi(current!!,"/proxies/${encode(key)}","PUT",JSONObject().put("name",node));content=remoteApi(current!!,"/proxies")}}){UiText(node)}}}}
        } else if(tested && tab=="connections") {
            val list=content.optJSONArray("connections");if(list!=null)for(i in 0 until list.length()){val connection=list.getJSONObject(i);Panel{UiText(connection.optJSONObject("metadata")?.optString("host").orEmpty());SubpageTextButton(onClick={vm.run{remoteApi(current!!,"/connections/${encode(connection.getString("id"))}","DELETE");content=remoteApi(current!!,"/connections")}}){UiText("关闭此远程连接")}}}
        } else if(tested && tab=="rules") {
            val list=content.optJSONArray("rules");if(list!=null)for(i in 0 until list.length()){val rule=list.getJSONObject(i);Panel{UiText("${rule.optString("type")} ${rule.optString("payload")} → ${rule.optString("proxy")}");val extra=rule.optJSONObject("extra");if(extra!=null)Switch(!extra.optBoolean("disabled"),onCheckedChange={on->vm.run{remoteApi(current!!,"/rules/disable","PATCH",JSONObject().put(rule.optInt("index",i).toString(),!on));content=remoteApi(current!!,"/rules")}})}}
        }
    }
}

@Composable fun AutomationPage(vm:MainViewModel){
    val context=LocalContext.current
    var enabled by rememberSaveable{mutableStateOf(vm.repo.draft("automation-enabled")=="true")}
    var minutes by rememberSaveable{mutableStateOf(vm.repo.draft("automation-minutes").ifBlank{"720"})}
    var last by remember{mutableStateOf(vm.repo.draft("automation-last"))}
    Page{
        ToggleRow("后台更新订阅",enabled){enabled=it}
        Field("更新间隔（分钟，至少15）",minutes){minutes=it}
        Info("系统按网络、电量与后台限制调度，不保证准点。当前运行配置下载后暂存，停止VPN后在订阅详情应用。GEO 自动更新在内核运行期间按 GEO 页面设置执行。")
        MainButton("保存更新计划"){vm.run{val interval=minutes.toLong();cn.nascp.clash.system.scheduleSubscriptions(context,interval,enabled);vm.repo.saveDraft("automation-enabled",enabled.toString());vm.repo.saveDraft("automation-minutes",minutes);vm.repo.message("更新计划已保存")}}
        MainButton("立即更新所有订阅"){vm.run{val result=vm.repo.updateSubscriptions();last="${java.util.Date()} · $result";vm.repo.saveDraft("automation-last",last)}}
        UiText(last.ifBlank{"尚无执行记录"})
        SubpageButton(outlined = true, modifier=Modifier.fillMaxWidth(), onClick={context.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))}){UiText("系统电池设置")}
        SubpageButton(outlined = true, modifier=Modifier.fillMaxWidth(), onClick={context.startActivity(android.content.Intent(android.provider.Settings.ACTION_VPN_SETTINGS))}){UiText("系统始终开启 VPN 设置")}
        SubpageButton(outlined = true, modifier=Modifier.fillMaxWidth(), onClick={context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName))}){UiText("通知设置")}
        SubpageButton(outlined = true, modifier=Modifier.fillMaxWidth(), onClick={
            cn.nascp.clash.system.requestVpnTile(context) { text -> vm.repo.message(text) }
        }){UiText("添加到快捷设置")}
        Info("添加 Clash VPN 后，可在下拉控制中心点击连接或断开。首次使用请先在应用内选择配置并授权 VPN。也可点击控制中心的编辑按钮，手动添加 Clash VPN。")
    }
}

@Composable fun ProfileInfoPage(vm:MainViewModel){
    val state by vm.repo.state.collectAsState()
    val p=state.selected
    var name by rememberSaveable(p?.id){mutableStateOf(p?.name.orEmpty())}
    var source by rememberSaveable(p?.id){mutableStateOf(p?.source.orEmpty())}
    var userAgent by rememberSaveable(p?.id){mutableStateOf(p?.userAgent ?: DEFAULT_SUBSCRIPTION_UA)}
    var interval by rememberSaveable(p?.id){mutableStateOf(vm.repo.draft("profile-interval.${p?.id}").ifBlank{"720"})}
    val info=runCatching{JSONObject(vm.repo.draft("subscription-info.${p?.source?.hashCode()}").ifBlank{"{}"})}.getOrDefault(JSONObject())
    Page{Field("名称",name){name=it};Field("HTTPS 订阅地址",source,true){source=it}
        SubscriptionUserAgentField(userAgent){userAgent=it}
        if(source.isNotBlank())SubscriptionConnectionDiagnostics(source,userAgent){userAgent=it}
        Panel{UiText("订阅信息");if(info.has("total")){UiText("${bytes(info.optLong("upload")+info.optLong("download"))} / ${bytes(info.getLong("total"))}")}else UiText("配额未提供")
        if(info.has("expire"))UiText("到期：${java.util.Date(info.getLong("expire")*1000)}")else UiText("到期时间未提供")}

        val diagnostic=vm.repo.draft("subscription-error.${p?.source?.hashCode()}")
        if(diagnostic.isNotBlank())Panel{UiText("最近下载失败");UiText(diagnostic)}
        Field("更新间隔（分钟，至少15）",interval){interval=it}
        MainButton("保存订阅信息",p!=null){vm.run{if(source.isNotBlank())require(java.net.URL(source).protocol=="https");require(interval.toLong()>=15);vm.repo.saveProfile(name,p!!.yaml,p.id,source,userAgent=userAgent);vm.repo.saveDraft("profile-interval.${p.id}",interval);vm.repo.message("订阅信息已保存")}}
    }
}

@Composable fun VpnSettingsPage(vm:MainViewModel,s:AppState,go:(String)->Unit,start:()->Unit,stop:()->Unit){
    val context=LocalContext.current
    val initial=remember{JSONObject(vm.repo.draft("vpn-options").ifBlank{"{}"})}
    var mtu by rememberSaveable{mutableStateOf(initial.optInt("mtu",1500).toString())}
    var ipv6 by rememberSaveable{mutableStateOf(initial.optBoolean("ipv6"))}
    var lan by rememberSaveable{mutableStateOf(initial.optBoolean("bypassLan"))}
    var excludes by rememberSaveable{mutableStateOf(initial.optString("exclude"))}
    @Composable fun RouteFields() {
        Field("MTU（1280–9000）", mtu) { mtu = it }
        if (LocalPlainSubpage.current) PlainDivider()
        ToggleRow("接管 IPv6", ipv6) { ipv6 = it }
        if (LocalPlainSubpage.current) PlainDivider()
        ToggleRow("绕过局域网 IPv4", lan) { lan = it }
        if (LocalPlainSubpage.current) PlainDivider()
        Field("排除 IPv4 网段（每行一个 CIDR）", excludes) { excludes = it }
    }
    val large = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f
    @Composable fun StatusButton() {
        SubpageTextButton(onClick = if (s.phase == VpnPhase.RUNNING) stop else start,
            enabled = s.selected != null && s.phase !in listOf(VpnPhase.STARTING, VpnPhase.STOPPING),
            modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            UiText(if (s.phase == VpnPhase.RUNNING) "停止 VPN" else "启动 VPN")
        }
    }
    Page {
        Panel {
            if (LocalPlainCompact.current) {
                Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlainIcon(Icons.Outlined.Shield, if (s.phase == VpnPhase.RUNNING) PlainGreen else androidx.compose.ui.graphics.Color(0xFF929AA5))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        UiText("VPN 状态", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        UiText(when (s.phase) {
                            VpnPhase.RUNNING -> "已连接"
                            VpnPhase.STARTING -> "正在启动…"
                            VpnPhase.STOPPING -> "正在停止…"
                            VpnPhase.FAILED -> "启动失败"
                            else -> "未连接"
                        }, style = MaterialTheme.typography.titleLarge)
                    }
                    if (!large) StatusButton()
                }
                if (large) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { StatusButton() }
            } else {
                UiText("VPN ${s.phase}")
                MainButton(if(s.phase==VpnPhase.RUNNING)"停止 VPN" else "启动 VPN",s.selected!=null&&s.phase !in listOf(VpnPhase.STARTING,VpnPhase.STOPPING),if(s.phase==VpnPhase.RUNNING)stop else start)
            }
        }
        if (LocalPlainSubpage.current) PlainSettingGroup { RouteFields() } else RouteFields()
        Info("保存后需重新启动 VPN。关闭 IPv6 接管时系统阻断本 VPN 的 IPv6。开启系统阻断时，排除的网段可能无法访问。")
        MainButton("保存 VPN 设置"){vm.run{require(mtu.toInt() in 1280..9000);cn.nascp.clash.vpn.Routes.included(excludes.lines());vm.repo.saveDraft("vpn-options",JSONObject().put("mtu",mtu.toInt()).put("ipv6",ipv6).put("bypassLan",lan).put("exclude",excludes).toString());vm.repo.message("已保存，重新启动VPN生效")}}
        if (LocalPlainSubpage.current) PlainGroup {
            Entry("分应用代理", icon = Icons.Outlined.Apps) { go("apps") }; PlainDivider()
            Entry("系统 VPN 设置", icon = Icons.Outlined.Shield) { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_VPN_SETTINGS)) }
        } else Panel{Entry("分应用代理"){go("apps")};Entry("系统 VPN 设置"){context.startActivity(android.content.Intent(android.provider.Settings.ACTION_VPN_SETTINGS))}}
    }
}

@Composable fun CorePage(vm:MainViewModel,s:AppState,go:(String)->Unit,start:()->Unit,stop:()->Unit){
    var level by remember(s.phase,s.activeId){mutableStateOf<String?>(null)}
    var loadingLevel by remember(s.phase,s.activeId){mutableStateOf(s.phase==VpnPhase.RUNNING)}
    LaunchedEffect(s.phase,s.activeId){
        if(s.phase==VpnPhase.RUNNING) {
            try { level=vm.repo.api("/configs").getString("log-level") }
            catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
            catch(error:Exception){vm.repo.message("读取日志等级失败",MessageKind.ERROR)}
            finally { loadingLevel=false }
        }
    }
    val busy by vm.busy.collectAsState()
    var testUrl by rememberSaveable{mutableStateOf(vm.repo.draft("test-url").ifBlank{"https://www.gstatic.com/generate_204"})}
    var timeout by rememberSaveable{mutableStateOf(vm.repo.draft("test-timeout").ifBlank{"5000"})}
    Page {
        Panel{UiText("Mihomo v1.19.30",style=MaterialTheme.typography.titleLarge);UiText("${s.phase} · arm64-v8a");UiText("内核随应用版本交付")}
        MainButton("校验当前配置",s.selected!=null){vm.run{vm.repo.validate(s.selected!!.yaml,s.selectedId);vm.repo.message("校验通过")}}
        MainButton("重启并应用配置",s.phase==VpnPhase.RUNNING){vm.run{stop();kotlinx.coroutines.withTimeout(15000){while(vm.repo.state.value.phase!=VpnPhase.STOPPED)kotlinx.coroutines.delay(100)};start()}}
        Info("重启会短暂断开现有连接。系统始终开启VPN时请通过系统设置管理。")
        RuntimeLogLevelSetting(level,s.phase==VpnPhase.RUNNING,loadingLevel,!busy) { value ->
            vm.run {
                check(vm.repo.state.value.phase==VpnPhase.RUNNING && vm.repo.state.value.activeId==s.activeId){"请先启动VPN"}
                withContext(Dispatchers.IO){cn.nascp.clash.core.NativeCore.invoke("log-level","value" to value)}
                level=value
            }
        }
        if (LocalPlainSubpage.current) PlainSettingGroup {
            Field("测速 HTTPS 地址",testUrl){testUrl=it}
            PlainDivider()
            Field("测速超时（1000–15000 ms）",timeout){timeout=it}
        } else {
            Field("测速 HTTPS 地址",testUrl){testUrl=it};Field("测速超时（1000–15000 ms）",timeout){timeout=it}
        }
        MainButton("保存测速参数"){vm.run{require(java.net.URL(testUrl).protocol=="https");require(timeout.toInt() in 1000..15000);vm.repo.saveDraft("test-url",testUrl);vm.repo.saveDraft("test-timeout",timeout);vm.repo.message("测速参数已保存")}}
        if (LocalPlainSubpage.current) PlainGroup {
            Entry("GEO 数据", icon = settingsScreenIcon("geo")) { go("geo") }; PlainDivider()
            Entry("诊断与导出", icon = settingsScreenIcon("diagnostics")) { go("diagnostics") }
        } else Panel{Entry("GEO 数据"){go("geo")};Entry("诊断与导出"){go("diagnostics")}}
    }
}
