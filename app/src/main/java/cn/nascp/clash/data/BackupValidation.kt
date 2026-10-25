package cn.nascp.clash.data

import cn.nascp.clash.vpn.Routes
import org.json.JSONArray
import org.json.JSONObject

/** Validate structured preferences before they can be consumed by a page or service. */
internal fun validateBackupSettings(settings:JSONObject) {
    settings.keys().forEach { key -> require(settings.get(key) is String){"备份设置必须为文本：$key"} }
    fun value(key:String)=settings.optString(key)
    settings.keys().forEach { key -> if (key.startsWith("proxy-selections.") && value(key).isNotBlank()) {
        val choices=JSONObject(value(key))
        choices.keys().forEach { name -> require(choices.get(name) is String && choices.getString(name).isNotBlank()) { "备份节点选择无效" } }
    } }
    if(value("remote-instances").isNotBlank()) {
        val remotes=JSONArray(value("remote-instances"));val ids=mutableSetOf<String>()
        for(i in 0 until remotes.length()) {
            val item=remotes.getJSONObject(i)
            require(ids.add(item.getString("id")) && item.getString("name").isNotBlank()){"远程实例无效或重复"}
            remoteBase(RemoteInstance(item.getString("id"),item.getString("name"),item.getString("address"),item.getString("token")))
        }
    }
    if(value("vpn-options").isNotBlank()) {
        val vpn=JSONObject(value("vpn-options"));require(vpn.optInt("mtu",1500) in 1280..9000){"备份 MTU 无效"}
        Routes.included(vpn.optString("exclude").lines())
    }
    if(value("app-mode").isNotBlank())require(value("app-mode") in listOf("全部应用","仅选中","排除选中")){"备份应用分流模式无效"}
    settings.keys().forEach{key->if(key=="automation-minutes" || key.startsWith("profile-interval.")) {
        require(value(key).toLongOrNull()?.let{it in 15..525600}==true){"备份更新间隔无效"}
    }}
}
