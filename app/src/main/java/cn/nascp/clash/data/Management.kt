package cn.nascp.clash.data

import cn.nascp.clash.core.NativeCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

suspend fun ClashRepository.section(section:String):String = withContext(Dispatchers.IO) {
    val p=state.value.selected?:error("请先选择配置")
    NativeCore.invoke("section","section" to section,"yaml" to p.yaml,"name" to p.name,"global" to extensions("global"),"extensions" to extensions(p.id)) as String
}
suspend fun ClashRepository.saveSection(section:String,value:String) {
    val p=state.value.selected?:error("请先选择配置")
    val next=withContext(Dispatchers.IO) { NativeCore.invoke("section","section" to section,"action" to "save","value" to value,"yaml" to p.yaml,"name" to p.name,"global" to extensions("global"),"extensions" to extensions(p.id)) as JSONObject }
    saveExtensions(p.id,next)
}
suspend fun ClashRepository.resetSection(section:String) {
    val p=state.value.selected?:error("请先选择配置")
    require(section in listOf("proxies","groups","rules"))
    val next=extensions(p.id);next.remove(section);saveExtensions(p.id,next)
}
fun ClashRepository.history(id:String):JSONArray = JSONArray(draft("history.$id").ifBlank{"[]"})
fun ClashRepository.snapshot(p:Profile) {
    val items=history(p.id)
    val item=JSONObject().put("time",System.currentTimeMillis()).put("name",p.name).put("yaml",p.yaml).put("source",p.source).put("userAgent",p.userAgent).put("extensions",extensions(p.id))
    val kept=JSONArray();kept.put(item);for(i in 0 until minOf(items.length(),19))kept.put(items.get(i))
    saveDraft("history.${p.id}",kept.toString())
}
suspend fun ClashRepository.restoreHistory(index:Int) {
    val p=state.value.selected?:error("请先选择配置")
    val item=history(p.id).getJSONObject(index)
    restoreProfile(p.copy(name=item.getString("name"),yaml=item.getString("yaml"),source=item.optString("source"),userAgent=subscriptionUserAgent(item.optString("userAgent",DEFAULT_SUBSCRIPTION_UA))),item.optJSONObject("extensions")?:JSONObject())
}
fun ClashRepository.diagnostics():String = JSONObject()
    .put("app","Clash Android").put("version",cn.nascp.clash.BuildConfig.VERSION_NAME)
    .put("sdk",android.os.Build.VERSION.SDK_INT).put("abi",android.os.Build.SUPPORTED_ABIS.joinToString())
    .put("phase",state.value.phase.name).put("profileCount",state.value.profiles.size)
    .put("subscriptionLastError",draft("subscription-last-error"))
    .put("nativeAvailable",NativeCore.available).put("time",System.currentTimeMillis()).toString(2)
