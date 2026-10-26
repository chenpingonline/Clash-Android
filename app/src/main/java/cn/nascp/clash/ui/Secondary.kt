@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.nascp.clash.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cn.nascp.clash.BuildConfig
import cn.nascp.clash.MainViewModel
import cn.nascp.clash.core.NativeCore
import cn.nascp.clash.data.*
import cn.nascp.clash.data.AppState
import cn.nascp.clash.data.ClashRepository
import cn.nascp.clash.data.VpnPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable fun Secondary(route: String, s: AppState, vm: MainViewModel, go: (String) -> Unit, back: () -> Unit, start: () -> Unit, stop: () -> Unit) {
    CompositionLocalProvider(LocalPlainCompact provides LocalPlain.current, LocalPlainSubpage provides LocalPlain.current) {
        SecondaryContent(route, s, vm, go, back, start, stop)
    }
}

@Composable private fun SecondaryContent(route: String, s: AppState, vm: MainViewModel, go: (String) -> Unit, back: () -> Unit, start: () -> Unit, stop: () -> Unit) {
    val context = LocalContext.current
    when(route) {
        "node-edit","group","group-edit","rule-edit","rule-set","network","dns","dns-servers","fakeip","fallback","hosts","geo" -> SectionPage(route,vm,go)
        "rule-provider" -> RuleProviderPage(vm,s,back)
        "system" -> SystemIntegrationPage(vm)
        "speed-test" -> SpeedTestSettingsPage(vm)
        "global-enhancements" -> GlobalEnhancementsPage(go)
        "profile-rules" -> ProfileRulesPage(s,go)
        "runtime-providers" -> Page { Info("仅作用于当前运行配置");ProviderActions(vm) }
        "geo-status" -> GeoStatusPage(vm,s)
        "rule-import" -> RuleImportPage(vm)
        "remote" -> RemoteInstancesPage(vm,go)
        "remote-editor" -> RemoteEditorPage(vm,go)
        "remote-workspace" -> RemoteWorkspacePage(vm)
        "automation" -> AutomationPage(vm)
        "profile-info" -> ProfileInfoPage(vm)
        "import" -> ImportPage(vm, back)
        "yaml" -> YamlPage(s, vm)
        "override", "script" -> ExtensionPage(route,s,vm)
        "global-override" -> ExtensionPage("override",s,vm,global=true)
        "global-script" -> ExtensionPage("script",s,vm,global=true)
        "appearance" -> PlainAppearance(s, vm)
        "profile" -> Page {
            if (s.selected == null) Info("请先选择配置") else {
                val pending=vm.repo.draft("pending-update.${s.selectedId}")
                if(pending.isNotBlank()) {
                    if(s.phase !in listOf(VpnPhase.STOPPED,VpnPhase.FAILED))Info("更新已下载，停止后可应用")
                    MainButton("应用已下载的订阅更新",s.phase in listOf(VpnPhase.STOPPED,VpnPhase.FAILED)){vm.run{vm.repo.applyPendingSubscription(s.selectedId);vm.repo.message("订阅更新已应用")}}
                }
                ProfileNavigation(s,vm,go)
                var confirm by remember(s.selectedId) { mutableStateOf(false) }
                SubpageButton(outlined = true, destructive = true, onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { UiText("删除配置", color = MaterialTheme.colorScheme.error) }
                if(confirm)InlineConfirmation("删除 ${s.selected!!.name}？","此操作会移除本地保存的配置。","删除",destructive=true,onDismiss={confirm=false}) {
                    confirm=false;vm.run{vm.repo.deleteProfile(s.selectedId);vm.repo.message("配置已删除");back()}
                }
            }
        }
        "vpn" -> VpnSettingsPage(vm,s,go,start,stop)
        "apps" -> AppsPage(vm, s)
        "core" -> CorePage(vm,s,go,start,stop)
        "connection" -> Page {
            val data = runCatching { JSONObject(vm.repo.draft("connection")) }.getOrDefault(JSONObject())
            val m = data.optJSONObject("metadata") ?: JSONObject()
            ConnectionApplicationDetails(data)
            Panel {
                UiText(m.optString("host").ifBlank { m.optString("destinationIP", "未选中连接") }, style = MaterialTheme.typography.titleLarge)
                listOf("目标IP" to m.optString("destinationIP"), "端口" to m.optString("destinationPort"), "协议" to m.optString("network"), "上传" to bytes(data.optLong("upload")), "下载" to bytes(data.optLong("download")), "规则" to data.optString("rule"), "规则内容" to data.optString("rulePayload"), "代理链" to data.optJSONArray("chains")?.toString().orEmpty()).forEach { (label,value) -> UiText("$label：$value") }
            }
            MainButton("关闭连接", s.phase == VpnPhase.RUNNING && data.has("id")) { vm.run { vm.repo.api("/connections/${encode(data.getString("id"))}", "DELETE"); back() } }
        }
        "rules" -> RulesPage(vm, s, go)
        "backup" -> BackupPage(vm)
        "diagnostics" -> DiagnosticsPage(vm)
        "about" -> Page {
            Panel {
                UiText("Clash Android", style = MaterialTheme.typography.headlineMedium)
                UiText(BuildConfig.VERSION_NAME, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AppUpdatePanel()
            var license by remember {mutableStateOf("")}
            MainButton("开源许可"){vm.run{license=withContext(Dispatchers.IO){context.assets.open("licenses/GPL-3.0.txt").bufferedReader().use{it.readText()}}}}
            if(license.isNotEmpty())Panel {
                UiText("GPL-3.0 · Mihomo / Clash-Manager",style=MaterialTheme.typography.titleMedium)
                Box(Modifier.heightIn(max=400.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())){UiText(license)}
                SubpageTextButton(onClick={license=""},modifier=Modifier.fillMaxWidth()){UiText("关闭")}
            }
        }
        "history" -> HistoryPage(vm)
        else -> Page { Info("页面不存在") }

    }
}

@Composable private fun ImportPage(vm: MainViewModel, back: () -> Unit) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf("url") }
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var showAddress by rememberSaveable { mutableStateOf(false) }
    var userAgent by rememberSaveable { mutableStateOf(DEFAULT_SUBSCRIPTION_UA) }
    var yaml by rememberSaveable { mutableStateOf("") }
    var fileYaml by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val busy by vm.busy.collectAsState()
    val defaultName = tr(if (mode == "url") "订阅配置" else "本地配置")
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.run {
            fileYaml = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { input ->
                String(input.readBytesLimited(8*1024*1024), Charsets.UTF_8)
            } ?: error("无法读取文件") }
            result = "配置文件已读取"
        }
    }
    Page(sectionMotion(mode)) {
        Choices(listOf("url" to "链接", "file" to "文件", "paste" to "粘贴"), mode) { mode = it; result = ""; advanced = false }
        AppTextField(name, { name=it }, Modifier.fillMaxWidth(),
            label = { UiText(if (mode == "url") "订阅名称（可选）" else "配置名称（可选）") }, singleLine = true)
        if (mode == "url") {
            AppTextField(address, { address=it }, Modifier.fillMaxWidth(), label = { UiText("HTTPS订阅地址") },
                visualTransformation = if(showAddress)VisualTransformation.None else PasswordVisualTransformation(), singleLine = true,
                trailingIcon={IconButton(onClick={showAddress=!showAddress}){Icon(if(showAddress)Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,tr(if(showAddress)"隐藏订阅地址" else "显示订阅地址"))}})
            MainButton("导入订阅", address.isNotBlank() && !busy) {
                val source=address.trim(); val agent=userAgent; val title=name.trim().ifBlank { defaultName }
                vm.run {
                    val downloaded=vm.repo.download(source,agent)
                    vm.repo.saveProfile(title,downloaded,source=source,userAgent=agent)
                    back()
                }
            }
            SubpageTextButton(modifier=Modifier.fillMaxWidth(),onClick={advanced=!advanced}) {
                UiText(if (advanced) "收起高级选项" else "高级选项")
                Icon(if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null,Modifier.size(20.dp))
            }
            androidx.compose.animation.AnimatedVisibility(advanced,
                enter=androidx.compose.animation.fadeIn()+androidx.compose.animation.expandVertically(),
                exit=androidx.compose.animation.fadeOut()+androidx.compose.animation.shrinkVertically()) {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    SubscriptionUserAgentField(userAgent) { userAgent=it }
                    SubscriptionConnectionDiagnostics(address,userAgent) { userAgent=it }
                }
            }
        } else {
            if (mode == "file") {
                MainButton("选择 YAML 文件",!busy) { picker.launch(arrayOf("*/*")) }
                if (result.isNotBlank()) Info(result)
            } else {
                AppTextField(yaml, { yaml=it }, Modifier.fillMaxWidth().heightIn(min=220.dp,max=420.dp),
                    label={UiText("配置内容（YAML）")}, textStyle=MaterialTheme.typography.bodyMedium.copy(fontFamily=FontFamily.Monospace))
            }
            MainButton("导入配置",(if (mode == "file") fileYaml else yaml).isNotBlank() && !busy) {
                val title=name.trim().ifBlank { defaultName }; val content=if (mode == "file") fileYaml else yaml
                vm.run { vm.repo.saveProfile(title,content); back() }
            }
        }
    }
}

@Composable private fun YamlPage(s: AppState, vm: MainViewModel) {
    val profile=s.selected
    var yaml by remember(profile?.id) { mutableStateOf(vm.repo.draft("scratch.yaml.${profile?.id}").ifBlank{profile?.yaml.orEmpty()}) }
    var result by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("raw") }
    var baseline by remember(profile?.id){mutableStateOf(profile?.yaml.orEmpty())}
    var effective by remember { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    Page(sectionMotion(mode)) {
        UiText("${tr("仅作用于配置")}：${profile?.name?:tr("未选择配置")}")
        EditorDraft(vm.repo,"scratch.yaml.${profile?.id}",yaml,yaml!=baseline)
        Choices(listOf("raw" to "原始编辑","effective" to "合并预览"),mode){mode=it;if(it=="effective")vm.run{effective=vm.repo.rendered()}}
        Info("YAML 是配置文件的文本格式，包含节点、分组、规则等设置。原始编辑会修改当前配置；合并预览包含覆写和脚本的处理结果。")
        Field("查找文本",query){query=it}
        val shown=if(mode=="raw")yaml else effective
        if(query.isNotBlank()) UiText(shown.lines().mapIndexedNotNull{index,line->if(line.contains(query,true))"${index+1}: $line"else null}.joinToString("\n").ifBlank{"无匹配"})
        UiText("共 ${shown.lines().size} 行")
        CodeEditor(shown,{yaml=it;result="未保存修改"},readOnly=mode!="raw")
        if(result.isNotBlank()) Info(result)
        if(mode=="raw") {
            SubpageButton(outlined = true, onClick={ vm.run { vm.repo.validate(yaml,profile?.id.orEmpty());result="校验通过" } },modifier=Modifier.fillMaxWidth()) { UiText("校验配置") }
            MainButton("校验并保存", profile != null) { vm.run { vm.repo.saveProfile(profile!!.name,yaml,profile.id,profile.source);vm.repo.saveDraft("scratch.yaml.${profile.id}","");baseline=yaml;result="已保存；尚未重新启动VPN" } }
        }
    }
}

@Composable private fun ExtensionPage(kind:String,s:AppState,vm:MainViewModel,global:Boolean=false) {
    val id=if(global) "global" else s.selectedId
    var code by remember(kind,id) { mutableStateOf(vm.repo.draft("scratch.extension.$id.$kind").ifBlank{vm.repo.draft("extension.$id.$kind")}.ifBlank { if(kind=="script") "function main(config) {\n  return config;\n}" else "# 仅填写需要覆写的配置项\n{}\n" }) }
    var result by rememberSaveable(kind,id) { mutableStateOf("") }
    var preview by remember(kind,id) { mutableStateOf("") }
    var baseline by remember(kind,id){mutableStateOf(vm.repo.draft("extension.$id.$kind").ifBlank{if(kind=="script")"function main(config) {\n  return config;\n}" else "# 仅填写需要覆写的配置项\n{}\n"})}
    Page {
        UiText(if(global) "作用于所有配置" else "${tr("仅作用于配置")}：${s.selected?.name?:tr("未选择配置")}",color=MaterialTheme.colorScheme.onSurfaceVariant)
        EditorDraft(vm.repo,"scratch.extension.$id.$kind",code,code!=baseline)
        Info(if(kind=="script") "在 main(config) 中修改并返回配置。保存后下次启动 VPN 生效。" else "只填写需要修改的配置项，不必粘贴整份配置。保存后下次启动 VPN 生效。")
        CodeEditor(code,{code=it;result="未保存修改";preview=""},label=if(kind=="script")"脚本内容（JavaScript）" else "覆写内容（YAML）")
        if(result.isNotBlank()) UiText(result)
        MainButton("校验并保存增强",(global || s.selected!=null) && s.phase in listOf(VpnPhase.STOPPED,VpnPhase.FAILED)) {
            vm.run{vm.repo.saveExtension(id,kind,code);vm.repo.saveDraft("scratch.extension.$id.$kind","");baseline=code;result="校验通过，增强已保存"}
        }
        SubpageButton(outlined=true,onClick={vm.run{preview=vm.repo.previewExtension(id,kind,code)}},enabled=s.selected!=null,modifier=Modifier.fillMaxWidth()){UiText("预览当前配置")}
        if(preview.isNotBlank())CodeEditor(preview,{},readOnly=true,label="合并后的配置（只读）")
    }
}

private data class InstalledApp(val label:String,val packageName:String,val icon:ImageBitmap)

@Composable private fun AppsPage(vm: MainViewModel, s: AppState) {
    val context=LocalContext.current
    var mode by rememberSaveable { mutableStateOf(vm.repo.draft("app-mode").ifBlank{"全部应用"}) }
    var query by rememberSaveable { mutableStateOf("") }
    val selected=remember { mutableStateListOf<String>().also { it.addAll(vm.repo.draft("app-packages").lines().filter(String::isNotBlank)) } }
    var apps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    LaunchedEffect(context) {
        apps=withContext(Dispatchers.IO) {
            val manager=context.packageManager
            val iconSize=(40 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
                .distinctBy{it.activityInfo.packageName}.filter{it.activityInfo.packageName!=context.packageName}
                .map { app ->
                    val icon=runCatching { app.loadIcon(manager).toBitmap(iconSize,iconSize) }
                        .getOrElse { manager.defaultActivityIcon.toBitmap(iconSize,iconSize) }
                    InstalledApp(app.loadLabel(manager).toString(),app.activityInfo.packageName,icon.asImageBitmap())
                }.sortedBy{it.label}
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp).padding(bottom=LocalDockClearance.current)) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(0.dp)) {
            item { Panel { SettingChoiceEntry("代理模式",listOf("全部应用","仅选中","排除选中").map{it to it},mode){mode=it} } }
            item {
                Surface(Modifier.fillMaxWidth().padding(vertical=10.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
                    Row(Modifier.padding(horizontal=14.dp).heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.Search,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                        androidx.compose.foundation.text.BasicTextField(query,{query=it},Modifier.weight(1f).testTag("app-search"),singleLine=true,
                            textStyle=MaterialTheme.typography.bodyLarge.copy(color=MaterialTheme.colorScheme.onSurface),
                            cursorBrush=androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox={input->Box {if(query.isEmpty())UiText("搜索应用",color=MaterialTheme.colorScheme.onSurfaceVariant);input()}})
                        if(query.isNotBlank())IconButton(onClick={query=""}){Icon(Icons.Outlined.Close,tr("清除搜索"))}
                    }
                }
            }
            item { Box(Modifier.padding(bottom=10.dp)){Info("显示系统允许查询的可启动应用。修改后下次启动VPN生效。开启系统阻断时，被排除的应用将无法联网。")} }
            item { SectionHeading("应用列表") }
            val filtered=apps.filter{it.label.contains(query,true)||it.packageName.contains(query,true)}
            if(filtered.isEmpty())item { Info("没有匹配的应用") }
            items(filtered.size,key={filtered[it].packageName}) { index ->
                val app=filtered[index]
                val selectable=mode!="全部应用"
                val chosen=app.packageName in selected
                val shape=androidx.compose.foundation.shape.RoundedCornerShape(
                    topStart=if(index==0)24.dp else 0.dp,topEnd=if(index==0)24.dp else 0.dp,
                    bottomStart=if(index==filtered.lastIndex)24.dp else 0.dp,bottomEnd=if(index==filtered.lastIndex)24.dp else 0.dp)
                Surface(shape=shape) {
                    Column {
                        Row(Modifier.fillMaxWidth().testTag("app-row:${app.packageName}")
                            .then(if(selectable)Modifier.toggleable(value=chosen,role=androidx.compose.ui.semantics.Role.Checkbox,
                                onValueChange={checked->if(checked)selected.add(app.packageName)else selected.remove(app.packageName)})else Modifier)
                            .padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Image(app.icon,contentDescription=null,modifier=Modifier.size(40.dp))
                            Column(Modifier.weight(1f)){UiText(app.label);UiText(app.packageName,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)}
                            if(selectable)Icon(if(chosen)Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,null,
                                Modifier.size(24.dp).testTag("app-choice:${app.packageName}"),tint=if(chosen)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        }
                        if(index<filtered.lastIndex)HorizontalDivider(Modifier.padding(start=68.dp,end=16.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))
                    }
                }
            }
        }
        Surface {
            Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                UiText(if(mode=="全部应用")tr("全部应用") else "${tr("已选应用")}：${selected.size}",style=MaterialTheme.typography.bodySmall)
                MainButton("保存应用选择", s.phase !in listOf(VpnPhase.STARTING,VpnPhase.STOPPING)) { vm.run { require(mode!="仅选中"||selected.isNotEmpty()){ "至少选择一个应用" };vm.repo.saveDraft("app-mode",mode);vm.repo.saveDraft("app-packages",selected.joinToString("\n"));vm.repo.message("已保存，下次启动VPN生效") } }
            }
        }
    }
}

@Composable private fun RulesPage(vm: MainViewModel,s:AppState,go:(String)->Unit) {
    var rules by remember { mutableStateOf(org.json.JSONArray()) }
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableStateOf(100) }
    var bulk by remember {mutableStateOf<Boolean?>(null)}
    LaunchedEffect(s.phase) { if(s.phase==VpnPhase.RUNNING) try {rules=vm.repo.api("/rules").optJSONArray("rules")?:org.json.JSONArray()} catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}catch(e:Exception){vm.repo.message(e.message?:"无法读取规则",MessageKind.ERROR)} }
    Page {
        AppTextField(query,{query=it},Modifier.fillMaxWidth(),label={UiText("搜索规则")},singleLine=true)
        AppTextField(type,{type=it},Modifier.fillMaxWidth(),label={UiText("类型过滤")},singleLine=true)
        if(s.phase!=VpnPhase.RUNNING) Info("启动VPN后显示实际生效规则。持久修改请使用下方编辑入口。")
        val filtered=(0 until rules.length()).map(rules::getJSONObject).filter{it.toString().contains(query,true)&&it.optString("type").contains(type,true)}
        UiText("匹配 ${filtered.size} 条")
        if(filtered.isNotEmpty())FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)){SubpageTextButton(onClick={bulk=false}){UiText("启用筛选规则")};SubpageTextButton(onClick={bulk=true}){UiText("禁用筛选规则")}}
        if(bulk!=null)InlineConfirmation("批量修改运行规则","将修改 ${filtered.size} 条匹配规则，重启后恢复。","确认",onDismiss={bulk=null}) {
            val disabled=bulk!!;val changes=JSONObject();filtered.forEach{changes.put(it.getInt("index").toString(),disabled)}
            bulk=null;vm.run{withContext(Dispatchers.IO){NativeCore.invoke("rule-disable","value" to changes.toString())};rules=vm.repo.api("/rules").getJSONArray("rules")}
        }
        Panel { filtered.take(limit).forEach { rule ->
            UiText("${rule.optString("type")}  ${rule.optString("payload")} → ${rule.optString("proxy")}")
            val extra=rule.optJSONObject("extra")
            if(extra!=null) Row{Switch(!extra.optBoolean("disabled"),onCheckedChange={on->vm.run{withContext(Dispatchers.IO){NativeCore.invoke("rule-disable","value" to JSONObject().put(rule.getInt("index").toString(),!on).toString())};rules=vm.repo.api("/rules").getJSONArray("rules")}});UiText("运行时启用（重启复原）")}
            HorizontalDivider()
        };if(filtered.isEmpty())UiText("暂无匹配规则") }
        if(filtered.size>limit)MainButton("加载更多"){limit+=100}
        Entry("运行中的规则集",icon=settingsScreenIcon("rule-set")){go("runtime-providers")}
        Info("规则开关仅本次运行生效；持久修改请前往配置详情。")
    }
}

fun java.io.InputStream.readBytesLimited(limit:Int):ByteArray {
    val out=java.io.ByteArrayOutputStream()
    val buffer=ByteArray(8192)
    while(true){val n=read(buffer);if(n<0)break;require(out.size()+n<=limit){"文件超过8 MiB"};out.write(buffer,0,n)}
    return out.toByteArray()
}
