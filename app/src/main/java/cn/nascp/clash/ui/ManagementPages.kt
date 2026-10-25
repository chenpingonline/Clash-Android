package cn.nascp.clash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cn.nascp.clash.MainViewModel
import cn.nascp.clash.core.NativeCore
import cn.nascp.clash.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

suspend fun decodeYaml(text:String):Any?=withContext(Dispatchers.IO){NativeCore.invoke("decode","value" to text)}
suspend fun encodeYaml(value:Any):String=withContext(Dispatchers.IO){NativeCore.invoke("encode","value" to value.toString()) as String}

@Composable fun SectionPage(route:String,vm:MainViewModel,go:(String)->Unit) {
    val section=when(route){"node-edit"->"proxies";"group","group-edit"->"groups";"rule-edit","rule-import"->"rules";"rule-set"->"rule-providers";"dns-servers","fakeip","fallback"->"dns";else->route}
    val state by vm.repo.state.collectAsState()
    val profile=state.selectedId
    if(state.selected==null){Page{Info("请先选择配置")};return}
    var text by rememberSaveable(route,profile){mutableStateOf("")}
    var loaded by remember(route,profile){mutableStateOf(false)}
    var preview by remember {mutableStateOf("")}
    var geoStatus by remember {mutableStateOf(JSONArray())}
    var advanced by rememberSaveable { mutableStateOf(false) }
    var formReady by remember {mutableStateOf(true)}
    var resetConfirm by remember {mutableStateOf(false)}
    var entries by remember {mutableStateOf(JSONArray())}
    var item by remember {mutableStateOf(-1)}
    var nodeName by rememberSaveable {mutableStateOf("")}
    var nodeType by rememberSaveable {mutableStateOf(if(section=="groups")"select" else "ss")}
    var nodeServer by rememberSaveable {mutableStateOf("")}
    var nodePort by rememberSaveable {mutableStateOf("443")}
    var nodePassword by rememberSaveable {mutableStateOf("")}
    var nodeCipher by rememberSaveable {mutableStateOf("chacha20-ietf-poly1305")}
    var groupMembers by rememberSaveable {mutableStateOf("DIRECT")}
    var ruleType by rememberSaveable {mutableStateOf("DOMAIN-SUFFIX")}
    var rulePayload by rememberSaveable {mutableStateOf("")}
    var ruleTarget by rememberSaveable {mutableStateOf("DIRECT")}
    var ruleNoResolve by rememberSaveable {mutableStateOf(false)}
    val sequence=section in listOf("proxies","groups","rules")
    LaunchedEffect(route,profile) { try { text=vm.repo.draft("editor.$profile.$section").ifBlank{vm.repo.section(section)};if(sequence)entries=decodeYaml(text) as JSONArray;loaded=true }catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}catch(e:Exception){vm.repo.message(e.message?:"读取失败",MessageKind.ERROR)} }
    LaunchedEffect(route){if(route=="geo")geoStatus=withContext(Dispatchers.IO){NativeCore.invoke("geo-status") as JSONArray}}
    LaunchedEffect(text,loaded){if(loaded){kotlinx.coroutines.delay(500);vm.repo.saveDraft("editor.$profile.$section",text)}}

    fun updateEntries(next:JSONArray){item=-1;entries=next;vm.run{text=encodeYaml(next)}}
    Page {
        UiText(state.selected?.name?:"未选择配置",style=if (LocalPlainSubpage.current) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
            color=if (LocalPlainSubpage.current) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        if (!LocalPlainSubpage.current) Info("校验后保存为订阅增强，保留原始订阅。后续覆写和脚本仍会参与合并；重新启动VPN生效。")
        if(resetConfirm)InlineConfirmation("恢复原始项","将清除此区段的自定义增删改，并恢复订阅原始项。","恢复",destructive=true,onDismiss={resetConfirm=false}) {
            resetConfirm=false;vm.run{vm.repo.resetSection(section);text=vm.repo.section(section);entries=decodeYaml(text) as JSONArray;preview="已恢复原始订阅区段"}
        }
        if(!loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(sequence) {
            val toggleEditor: () -> Unit = { vm.run { if(advanced)entries=decodeYaml(text) as JSONArray;item=-1;advanced=!advanced } }
            @Composable fun EditorActions() {
                Row(Modifier.fillMaxWidth(), verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    if (LocalPlainSubpage.current) SubpageTextButton(onClick=toggleEditor,modifier=Modifier.weight(1f)) {
                        UiText(if (advanced) "可视化编辑" else "YAML 编辑")
                    } else FilterChip(advanced,toggleEditor,label={UiText("YAML 编辑")})
                    SubpageTextButton(onClick={resetConfirm=true},modifier=if(LocalPlainSubpage.current)Modifier.weight(1f)else Modifier) { UiText("恢复原始项") }
                }
            }
            if (LocalPlainSubpage.current) PlainGroup { EditorActions() } else EditorActions()
            if(!advanced) {
                Panel {
                    if(entries.length()==0)UiText("暂无条目")
                    for(i in 0 until entries.length()) {
                        val value=entries.get(i)
                        val title=if(value is JSONObject)value.optString("name") else value.toString()
                        UiText(title)
                        Row {
                            SubpageTextButton(onClick={item=i;if(value is JSONObject){nodeName=value.optString("name");nodeType=value.optString("type");nodeServer=value.optString("server");nodePort=value.optString("port","443");nodePassword=value.optString("password");nodeCipher=value.optString("cipher","chacha20-ietf-poly1305");groupMembers=value.optJSONArray("proxies")?.let{a->(0 until a.length()).joinToString("\n"){a.getString(it)}}?:""}else{val parts=value.toString().split(',');ruleNoResolve=parts.last().trim()=="no-resolve";val body=if(ruleNoResolve)parts.dropLast(1)else parts;ruleType=body.first();ruleTarget=body.last();rulePayload=body.drop(1).dropLast(1).joinToString(",")}}){UiText("编辑")}
                            SubpageTextButton(onClick={val next=JSONArray();for(j in 0 until entries.length())if(j!=i)next.put(entries.get(j));updateEntries(next)}){UiText("移除", color = MaterialTheme.colorScheme.error)}
                            SubpageTextButton(enabled=i>0,onClick={val next=JSONArray();for(j in 0 until entries.length())next.put(entries.get(if(j==i)i-1 else if(j==i-1)i else j));updateEntries(next)}){UiText("上移")}
                        }
                        HorizontalDivider()
                    }
                }
                Panel {
                    UiText(if(item<0)"添加条目" else "编辑第 ${item+1} 项",style=MaterialTheme.typography.titleMedium)
                    if(section=="rules") {
                        Field("规则类型",ruleType){ruleType=it};Field("规则内容（MATCH 留空）",rulePayload){rulePayload=it};Field("目标策略",ruleTarget){ruleTarget=it};CheckRow("no-resolve",ruleNoResolve){ruleNoResolve=it}
                    } else {
                        Field("名称",nodeName){nodeName=it};Field("类型",nodeType){nodeType=it}
                        if(section=="proxies") {Field("服务器",nodeServer){nodeServer=it};Field("端口",nodePort){nodePort=it};Field("密码",nodePassword,true){nodePassword=it};Field("cipher（SS）",nodeCipher){nodeCipher=it};Info("可视化填写基本字段；UUID、TLS、传输等协议特有字段使用 YAML 编辑，既有字段会保留。")}
                        else Field("成员（每行一个）",groupMembers){groupMembers=it}
                    }
                    MainButton("写入待保存列表",loaded){vm.run{
                        val value:Any=if(section=="rules") {require(ruleType.isNotBlank()&&ruleTarget.isNotBlank());if(ruleType=="MATCH")"MATCH,$ruleTarget" else {require(rulePayload.isNotBlank());"$ruleType,$rulePayload,$ruleTarget"+if(ruleNoResolve)",no-resolve"else""}}
                        else {require(nodeName.isNotBlank());val o=if(item>=0)JSONObject(entries.getJSONObject(item).toString())else JSONObject();o.put("name",nodeName).put("type",nodeType);if(section=="proxies"){o.put("server",nodeServer).put("port",nodePort.toInt());if(nodePassword.isNotEmpty())o.put("password",nodePassword)else o.remove("password");if(nodeType=="ss")o.put("cipher",nodeCipher)}else{o.put("proxies",JSONArray(groupMembers.lines().filter{it.isNotBlank()}));if(nodeType in listOf("url-test","fallback","load-balance")){if(!o.has("url"))o.put("url","https://www.gstatic.com/generate_204");if(!o.has("interval"))o.put("interval",300)}};o}
                        val next=JSONArray(entries.toString());if(item>=0)next.put(item,value)else next.put(value);entries=next;text=encodeYaml(next);item=-1;preview="列表已修改，尚未保存"
                    }}
                }
            }
        }
        if(!sequence || advanced) {
            if(route=="rule-import")Info("粘贴 Clash YAML 规则列表；复杂规则可直接使用完整 YAML。保存前会校验引用和顺序。")
            ConfigFields(route,text,loaded,{text=it},{formReady=it})
            if(!sequence)SubpageTextButton(onClick={advanced=!advanced},modifier=Modifier.fillMaxWidth()){UiText(if(advanced)"收起 YAML" else "高级 YAML 编辑")}
            if(sequence || advanced || route in listOf("hosts","rule-set"))CodeEditor(text,{text=it},label="${screens[route]?.title ?: route} · YAML")
        }
        if (LocalPlainSubpage.current) Info("校验后保存为订阅增强，保留原始订阅。后续覆写和脚本仍会参与合并；重新启动VPN生效。")
        if(preview.isNotBlank())UiText(preview)
        MainButton("校验并保存",loaded && formReady){vm.run{vm.repo.saveSection(section,text);vm.repo.saveDraft("editor.$profile.$section","");preview="已保存，下次启动 VPN 生效";if(sequence)entries=decodeYaml(text) as JSONArray}}
        if (route == "dns") {
            val links = listOf("dns-servers", "fakeip", "fallback", "hosts")
            if (LocalPlainSubpage.current) PlainGroup {
                links.forEachIndexed { index, id ->
                    if (index > 0) PlainDivider()
                    Entry(screens[id]!!.title, icon = settingsScreenIcon(id)) { go(id) }
                }
            } else Panel { links.forEach { id -> Entry(screens[id]!!.title) { go(id) } } }
        }
        if(route=="rule-set")ProviderActions(vm)
        if(route=="geo")Panel{for(i in 0 until geoStatus.length()){val item=geoStatus.getJSONObject(i);UiText("${item.getString("name")} · ${if(item.optBoolean("exists"))bytes(item.optLong("bytes"))+" · "+java.util.Date(item.getLong("updated")) else "尚未下载"}")}}
        if(route=="geo")MainButton("更新 GEO 数据",state.phase==VpnPhase.RUNNING){vm.run{withContext(Dispatchers.IO){NativeCore.invoke("geo-update")};geoStatus=withContext(Dispatchers.IO){NativeCore.invoke("geo-status") as JSONArray};preview="GEO 更新请求完成，请核对文件时间"}}
    }
}

@Composable fun Field(label:String,value:String,secret:Boolean=false,onChange:(String)->Unit){
    if (LocalPlainSubpage.current) {
        PlainSettingField(label, value, secret, multiline = label.contains("每行") || label.contains("JSON"), onChange = onChange)
        return
    }
    AppTextField(value,onChange,Modifier.fillMaxWidth(),label={UiText(label)},visualTransformation=if(secret)androidx.compose.ui.text.input.PasswordVisualTransformation()else androidx.compose.ui.text.input.VisualTransformation.None)
}

@Composable private fun ConfigFields(route:String,text:String,loaded:Boolean,onChange:(String)->Unit,onReady:(Boolean)->Unit) {
    var obj by remember(route){mutableStateOf(JSONObject())}
    val values=remember(route){mutableStateMapOf<String,String>()}
    val changed=remember(route){mutableStateListOf<String>()}
    var revision by remember{mutableStateOf(0)}
    var generated by remember{mutableStateOf("")}
    var error by remember{mutableStateOf("")}
    val fields=when(route){
        "network"->listOf("mixed-port","port","socks-port","bind-address","allow-lan","authentication")
        "dns"->listOf("enable","enhanced-mode","fake-ip-range","ipv6")
        "dns-servers"->listOf("default-nameserver","nameserver","fallback","proxy-server-nameserver","direct-nameserver")
        "fakeip"->listOf("fake-ip-filter-mode","fake-ip-filter","nameserver-policy")
        "fallback"->listOf("fallback-filter")
        "geo"->listOf("geodata-mode","geo-auto-update","geo-update-interval","geox-url")
        else->emptyList()
    }
    val labels=mapOf("mixed-port" to "Mixed 端口","port" to "HTTP 端口","socks-port" to "SOCKS 端口","bind-address" to "监听地址","allow-lan" to "允许局域网连接","authentication" to "认证（每行 用户:密码）",
        "enable" to "启用 DNS","enhanced-mode" to "解析模式","fake-ip-range" to "Fake IP 范围","ipv6" to "解析 IPv6",
        "default-nameserver" to "默认 DNS（每行一个）","nameserver" to "主解析（每行一个）","fallback" to "回退 DNS（每行一个）","proxy-server-nameserver" to "代理节点 DNS（每行一个）","direct-nameserver" to "直连 DNS（每行一个）",
        "fake-ip-filter-mode" to "过滤模式","fake-ip-filter" to "过滤域名（每行一个）","nameserver-policy" to "域名策略（JSON 对象）","fallback-filter" to "回退过滤（JSON 对象）",
        "geodata-mode" to "使用 DAT 格式 GeoIP","geo-auto-update" to "内核运行时自动更新","geo-update-interval" to "GEO 更新间隔（小时）","geox-url" to "数据源（JSON 对象）")
    val booleans=setOf("allow-lan","enable","ipv6","geodata-mode","geo-auto-update")
    val arrays=setOf("authentication","default-nameserver","nameserver","fallback","proxy-server-nameserver","direct-nameserver","fake-ip-filter")
    LaunchedEffect(text,loaded){if(fields.isNotEmpty() && loaded && text!=generated)try{
        obj=decodeYaml(text) as JSONObject;changed.clear();fields.forEach{k->val v=obj.opt(k);values[k]=when(v){is JSONArray->(0 until v.length()).joinToString("\n"){v.getString(it)};is JSONObject->v.toString(2);null->"";else->v.toString()}};error="";onReady(true)
    }catch(_:Exception){error="YAML 语法未完成，修正后可使用字段快捷编辑"}}
    LaunchedEffect(revision){if(revision>0){kotlinx.coroutines.delay(200);try{
        val current=JSONObject(obj.toString())
        changed.forEach{k->val value=values[k].orEmpty();val parsed:Any=when{ k in booleans->value=="true";k in arrays->JSONArray(value.lines().filter{it.isNotBlank()});k in setOf("mixed-port","port","socks-port","geo-update-interval")->value.toInt();k in setOf("nameserver-policy","fallback-filter","geox-url")->JSONObject(value.ifBlank{"{}"});else->value};current.put(k,parsed)}
        val yaml=encodeYaml(current);obj=current;generated=yaml;onChange(yaml);error="";onReady(true)
    }catch(e:Exception){error="字段格式不正确，请修正数字或 JSON 对象后保存";onReady(false)}}}
    if(error.isNotBlank())Info(error)
    @Composable fun Fields() {
        fields.forEachIndexed { index, k ->
            if (LocalPlainSubpage.current && index > 0) PlainDivider()
            val change:(String)->Unit={value->values[k]=value;if(k !in changed)changed.add(k);onReady(false);revision++}
            if(k in booleans)ToggleRow(labels[k]?:k,values[k]=="true"){change(it.toString())}
            else Field(labels[k]?:k,values[k].orEmpty(),k=="authentication",change)
        }
    }
    if (fields.isNotEmpty()) {
        if (LocalPlainSubpage.current) PlainSettingGroup { Fields() } else Panel { Fields() }
    }
}
fun kotlinx.coroutines.CoroutineScope.launchSafely(onError:(String)->Unit,action:suspend ()->Unit)=this.launch{try{action()}catch(e:Exception){onError(e.message?:"操作失败")}}

@Composable fun ProviderActions(vm:MainViewModel){
    var providers by remember {mutableStateOf(JSONObject())}
    var result by remember {mutableStateOf("")}
    MainButton("读取运行中的规则集"){vm.run{providers=vm.repo.api("/providers/rules").optJSONObject("providers")?:JSONObject()}}
    providers.keys().forEach { name-> Panel{UiText(name);UiText(providers.optJSONObject(name)?.toString(2).orEmpty());MainButton("更新 $name"){vm.run{vm.repo.api("/providers/rules/${encode(name)}","PUT");result="$name 已更新"}}} }
    if(result.isNotBlank())UiText(result)
}

@Composable fun RuleImportPage(vm:MainViewModel){
    val context=androidx.compose.ui.platform.LocalContext.current
    var name by rememberSaveable{mutableStateOf("Imported")};var policy by rememberSaveable{mutableStateOf("DIRECT")};var address by rememberSaveable{mutableStateOf("")};var text by rememberSaveable{mutableStateOf("")};var encoded by remember{mutableStateOf("")};var placement by rememberSaveable{mutableStateOf("prepend")};var behavior by rememberSaveable{mutableStateOf("auto")}
    var result by remember{mutableStateOf<JSONObject?>(null)}
    val file=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()){uri->if(uri!=null)vm.run{val bytes=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{it.readBytesLimited(8*1024*1024)}?:error("无法读取文件")};encoded=android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP);address="";text="已载入文件（${bytes.size}字节）";result=null}}
    Page{
        Info("支持 Clash YAML、规则文本、INI、MRS。解析会列出警告与不支持项；有错误时禁止保存。")
        Field("名称",name){name=it;result=null};Field("目标策略",policy){policy=it;result=null};Field("HTTPS 规则来源（可留空）",address){address=it;result=null}
        Field("behavior：auto / domain / ipcidr / classical",behavior){behavior=it;result=null}
        Choices(listOf("prepend" to "前置","append" to "追加"),placement){placement=it;result=null}
        MainButton("选择规则文件"){file.launch(arrayOf("*/*"))}
        Field("规则内容",text){text=it;encoded="";result=null}
        MainButton("解析并预览"){vm.run{val p=vm.repo.state.value.selected?:error("请先选择配置");result=withContext(Dispatchers.IO){NativeCore.invoke("import-rules","yaml" to p.yaml,"name" to p.name,"global" to vm.repo.extensions("global"),"extensions" to vm.repo.extensions(p.id),"value" to text,"data" to encoded,"options" to JSONObject().put("name",name).put("policy",policy).put("url",address).put("behavior",behavior).put("placement",placement)) as JSONObject}}}
        result?.let{preview->UiText("格式 ${preview.optString("format")} · 规则 ${preview.optInt("ruleCount")} · 规则集条目 ${preview.optInt("providerRuleCount")}");UiText("警告：${preview.optJSONArray("warnings")}");UiText("错误：${preview.optJSONArray("errors")}");UiText(preview.optJSONObject("extensions")?.toString(2).orEmpty());MainButton("校验并应用导入",(preview.optJSONArray("errors")?.length()?:0)==0){vm.run{vm.repo.saveExtensions(vm.repo.state.value.selectedId,preview.getJSONObject("extensions"));result=null;vm.repo.message("规则导入已保存，重启VPN生效")}}}
    }
}
