package cn.nascp.clash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import cn.nascp.clash.MainViewModel
import cn.nascp.clash.data.*
import org.json.JSONObject

@Composable internal fun RemoteInstancesPage(vm:MainViewModel,go:(String)->Unit) {
    var instances by remember{mutableStateOf(vm.repo.remotes())}
    var deleting by remember{mutableStateOf<RemoteInstance?>(null)}
    Page {
        Info("选择实例后进入远程工作区，与本机 VPN 分开管理。")
        if(instances.isEmpty())Info("尚未添加远程实例")
        instances.forEach { instance -> Panel {
            UiText(instance.name)
            MainButton("打开工作区"){vm.repo.saveDraft("remote-selected",instance.id);go("remote-workspace")}
            Row {
                SubpageTextButton(modifier=Modifier.testTag("remote-edit:${instance.id}"),onClick={vm.repo.saveDraft("remote-selected",instance.id);go("remote-editor")}){UiText("编辑")}
                SubpageTextButton(onClick={deleting=instance}){UiText("删除")}
            }
        }}
        MainButton("新增实例"){vm.repo.saveDraft("remote-selected","");go("remote-editor")}
        deleting?.let { instance ->
            InlineConfirmation("删除远程实例？","只删除本机保存的连接信息，不会删除远程服务。","删除",destructive=true,onDismiss={deleting=null}) {
                instances=instances.filterNot{it.id==instance.id};vm.repo.saveRemotes(instances);deleting=null
            }
        }
    }
}

@Composable internal fun RemoteEditorPage(vm:MainViewModel,go:(String)->Unit) {
    val original=remember{vm.repo.remotes().firstOrNull{it.id==vm.repo.draft("remote-selected")}}
    val key="remote-form.${original?.id.orEmpty()}"
    val draft=remember{runCatching{JSONObject(vm.repo.draft(key))}.getOrNull()}
    var savedId by remember{mutableStateOf(original?.id.orEmpty())}
    var name by remember{mutableStateOf(draft?.optString("name")?:original?.name.orEmpty())}
    var address by remember{mutableStateOf(draft?.optString("address")?:original?.address.orEmpty())}
    var token by remember{mutableStateOf(draft?.optString("token")?:original?.token.orEmpty())}
    fun form()=JSONObject().put("name",name).put("address",address).put("token",token).toString()
    var baseline by remember{mutableStateOf(JSONObject().put("name",original?.name.orEmpty()).put("address",original?.address.orEmpty()).put("token",original?.token.orEmpty()).toString())}
    Page {
        Info("连接信息加密保存在本机，可先保存，再测试连接。")
        EditorDraft(vm.repo,key,form(),form()!=baseline)
        SectionHeading("连接信息")
        PlainSettingGroup {
            Field("实例名称",name){name=it};PlainDivider()
            Field("HTTPS 控制器地址",address){address=it};PlainDivider()
            Field("访问令牌",token,true){token=it}
        }
        MainButton("测试连接"){vm.run {
            require(name.isNotBlank()){"请输入实例名称"}
            remoteApi(RemoteInstance(savedId,name,address,token),"/version")
            vm.repo.message("连接测试成功")
        }}
        MainButton("保存实例"){vm.run {
            require(name.isNotBlank()){"请输入实例名称"}
            val candidate=RemoteInstance(savedId.ifBlank{java.util.UUID.randomUUID().toString()},name,address,token)
            remoteBase(candidate)
            vm.repo.saveRemotes(vm.repo.remotes().filterNot{it.id==candidate.id}+candidate)
            savedId=candidate.id;baseline=form();vm.repo.saveDraft(key,"");vm.repo.saveDraft("remote-selected",savedId);vm.repo.message("远程实例已保存")
        }}
        if(savedId.isNotBlank() && form()==baseline)SubpageButton(outlined=true,modifier=Modifier.fillMaxWidth(),onClick={vm.repo.saveDraft("remote-selected",savedId);go("remote-workspace")}){UiText("打开工作区")}
    }
}
