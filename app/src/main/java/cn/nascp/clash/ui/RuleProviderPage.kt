package cn.nascp.clash.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import cn.nascp.clash.MainViewModel
import cn.nascp.clash.data.*
import org.json.JSONObject

@Composable internal fun RuleProviderPage(vm:MainViewModel,s:AppState,back:()->Unit) {
    val profile=s.selectedId
    val originalName=remember(profile){vm.repo.draft("rule-provider.selected.$profile")}
    var savedName by remember(profile){mutableStateOf(originalName)}
    var all by remember(profile){mutableStateOf(JSONObject())}
    var original by remember(profile){mutableStateOf(JSONObject())}
    var name by rememberSaveable(profile){mutableStateOf(originalName)}
    var text by remember(profile,originalName){mutableStateOf("")}
    var baseline by remember(profile){mutableStateOf("")}
    var loaded by remember(profile){mutableStateOf(false)}
    var confirmDelete by remember{mutableStateOf(false)}
    var result by remember{mutableStateOf("")}
    var editing by rememberSaveable(profile,originalName){mutableStateOf(originalName.isBlank())}
    var loadRevision by remember{mutableStateOf(0)}
    LaunchedEffect(profile,loadRevision) {
        try {
            all=decodeYaml(vm.repo.section("rule-providers")) as JSONObject
            original=all.optJSONObject(originalName)?:JSONObject().put("type","http").put("behavior","domain").put("interval",86400)
            baseline=encodeYaml(original)
            val draft=vm.repo.draft("provider-edit.$profile.$originalName")
            val saved=runCatching{JSONObject(draft)}.getOrNull()
            name=saved?.optString("name")?:originalName
            text=saved?.optString("yaml")?:baseline
            if(saved!=null)editing=true
            loaded=true
        }catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
        catch(error:Exception){vm.repo.message("读取规则集失败，请返回后重试",MessageKind.ERROR)}
    }
    Page {
        ProfileScope(s)
        if(s.selected==null)return@Page
        EditorDraft(vm.repo,"provider-edit.$profile.$originalName",JSONObject().put("name",name).put("yaml",text).toString(),loaded && (text!=baseline || name!=savedName))
        if(!loaded){Info("读取中…");MainButton("重试"){loadRevision++};return@Page}
        if(!editing) {
            Panel {
                UiText(savedName)
                UiText("${original.optString("type")} · ${original.optString("behavior")}")
                val host=runCatching{java.net.URL(original.optString("url")).host}.getOrDefault("")
                if(host.isNotBlank())UiText("${tr("来源")}：$host")
                if(original.has("interval"))UiText("${tr("更新间隔（秒）")}：${original.optLong("interval")}")
            }
            MainButton("编辑规则集"){editing=true}
        } else {
        Field("名称",name){name=it}
        Info("编辑此规则集的 YAML 定义；支持 http、file、inline，保存前校验整个配置。")
        CodeEditor(text,{text=it},label="规则集定义（YAML）")
        MainButton("保存修改",s.phase in listOf(VpnPhase.STOPPED,VpnPhase.FAILED)) {vm.run {
            require(name.isNotBlank()){"请输入规则集名称"}
            require(name==savedName || !all.has(name)){"规则集名称已存在"}
            val value=decodeYaml(text) as? JSONObject?:error("规则集需要 YAML 对象")
            val next=JSONObject(all.toString());if(savedName.isNotBlank())next.remove(savedName);next.put(name,value)
            vm.repo.saveSection("rule-providers",encodeYaml(next));all=next;original=value;baseline=text;savedName=name;editing=false
            vm.repo.saveDraft("provider-edit.$profile.$originalName","")
            result="已保存，下次启动 VPN 生效"
        }}
        }
        if(result.isNotBlank())Info(result)
        if(savedName.isNotBlank()) {
            SubpageTextButton(onClick={confirmDelete=true}){UiText("删除规则集")}
            if(confirmDelete)InlineConfirmation("删除规则集？","仍被规则引用的规则集无法删除，校验失败会保留原数据。","删除",destructive=true,onDismiss={confirmDelete=false}) {
                confirmDelete=false;vm.run {
                    val next=JSONObject(all.toString());next.remove(savedName)
                    vm.repo.saveSection("rule-providers",encodeYaml(next));all=next;vm.repo.saveDraft("provider-edit.$profile.$originalName","");vm.repo.message("已删除，下次启动 VPN 生效");back()
                }
            }
        }
    }
}
