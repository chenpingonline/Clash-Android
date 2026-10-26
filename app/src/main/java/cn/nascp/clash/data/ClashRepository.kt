package cn.nascp.clash.data

import android.content.Context
import cn.nascp.clash.core.NativeCore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

enum class MessageKind { INFO, ERROR }

enum class VpnPhase { STOPPED, STARTING, RUNNING, STOPPING, FAILED }
data class AppState(
    val ready: Boolean = false, val profiles: List<Profile> = emptyList(), val selectedId: String = "",
    val activeId: String = "", val runtimeRevision: Long = 0, val phase: VpnPhase = VpnPhase.STOPPED, val error: String = "",
    val messageKind: MessageKind = MessageKind.INFO, val messageId: Long = 0,
    val busy: Boolean = false, val language: String = "zh", val fontScale:Float = 1f, val fontWeight:Int = 0, val theme: String = ThemePreference.DEFAULT, val up: Long = 0, val down: Long = 0,
    val totalUp: Long = 0, val totalDown: Long = 0, val mode: String = "rule",
    val exitLocation: ExitLocation? = null, val exitLoading: Boolean = false, val exitError: String = "",
    val preview: JSONObject = JSONObject(), val previewProfile: Profile? = null, val previewId: String = "", val previewRevision: Long = 0,
    val previewReady: Boolean = false, val previewError: Boolean = false, val pendingProviders: List<String> = emptyList(),
    val proxies: JSONObject = JSONObject(), val connections: JSONArray = JSONArray(), val logs: List<String> = emptyList()
) {
    val selected: Profile? get() = profiles.firstOrNull { it.id == selectedId }
    val visibleProxies: JSONObject get() = if (phase == VpnPhase.RUNNING) proxies else if (previewId == selectedId && previewProfile == selected && previewReady) preview else JSONObject()
}

class ClashRepository private constructor(context: Context) {
    private val context = context.applicationContext
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    private val store = ProfileStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val edits = Mutex()
    private val trafficSamples=Mutex()
    private val trafficTotals=TrafficTotalsStore(context)
    private val homeVisible=MutableStateFlow(false)
    private val foregroundVisible=MutableStateFlow(false)
    private val exitRefresh=MutableStateFlow(0L)
    internal var exitLocationFetcher:suspend ()->ExitLocation = { queryExitLocation() }
    private val selectionLock = Any()
    private val mutable = MutableStateFlow(AppState(totalUp=trafficTotals.current().upload,totalDown=trafficTotals.current().download,theme = ThemePreference.resolve(prefs.getString("theme", null)),language=prefs.getString("language","zh")!!,fontScale=prefs.getFloat("fontScale",1f),fontWeight=prefs.getInt("fontWeight",0).takeIf{it in setOf(-100,0,100)}?:0))
    val state = mutable.asStateFlow()
    val secret = UUID.randomUUID().toString()
    private val initialization = scope.async {
        try {
            recoverJournal()
            NativeCore.invoke("init", "home" to context.filesDir.resolve("core").absolutePath)
            val profiles = store.load()
            val selected = prefs.getString("selected", "").orEmpty().takeIf { id -> profiles.any { it.id == id } } ?: profiles.firstOrNull()?.id.orEmpty()
            cn.nascp.clash.system.scheduleSubscriptions(context,draft("automation-minutes").toLongOrNull()?.coerceAtLeast(15)?:720L,draft("automation-enabled")=="true")
            mutable.update { it.copy(ready = true, profiles = profiles, selectedId = selected, theme=ThemePreference.resolve(prefs.getString("theme",null)),language=prefs.getString("language","zh")!!,fontScale=prefs.getFloat("fontScale",1f),fontWeight=prefs.getInt("fontWeight",0).takeIf{it in setOf(-100,0,100)}?:0) }
        } catch (e: Exception) { mutable.update { it.copy(error = e.message ?: "初始化失败", messageKind = MessageKind.ERROR, messageId = it.messageId + 1) } }
    }
    init {
        scope.launch {
            initialization.await()
            state.map { Triple(it.selected, it.previewRevision, it.phase == VpnPhase.RUNNING) }
                .distinctUntilChanged().collectLatest { (profile, revision, running) ->
                    if (running) return@collectLatest
                    mutable.update { it.copy(previewReady=false, previewError=false, pendingProviders=emptyList()) }
                    if (profile == null) return@collectLatest
                    val result = runCatching { withContext(Dispatchers.IO) {
                        NativeCore.invoke("preview-proxies", "yaml" to profile.yaml, "name" to profile.name,
                            "global" to extensions("global"), "extensions" to extensions(profile.id)) as JSONObject
                    } }
                    mutable.update { current ->
                        if (current.selected != profile || current.previewRevision != revision || current.phase == VpnPhase.RUNNING) current
                        else current.copy(previewId=profile.id, previewProfile=profile, previewReady=true, previewError=result.isFailure,
                            preview=previewWithSelections(result.getOrNull()?.optJSONObject("proxies") ?: JSONObject(), profile.id),
                            pendingProviders=result.getOrNull()?.optJSONArray("pendingProviders")?.let { array -> (0 until array.length()).map(array::getString) } ?: emptyList())
                    }
                }
        }
        scope.launch {
            initialization.await()
            combine(state.map{it.exitRoutingKey()}.distinctUntilChanged(),homeVisible,exitRefresh) { key,visible,revision -> Triple(key,visible,revision) }
                .distinctUntilChanged().collectLatest { (key,visible,revision) ->
                    if(!visible || key.phase!=VpnPhase.RUNNING || key.groups.isEmpty()) {
                        mutable.update { it.copy(exitLocation=null,exitLoading=false,exitError="") }
                        return@collectLatest
                    }
                    mutable.update { it.copy(exitLocation=null,exitLoading=true,exitError="") }
                    try {
                        delay(150)
                        val result=exitLocationFetcher()
                        // Automatic groups can change while the query is in flight.
                        val verified=api("/proxies").optJSONObject("proxies") ?: error("无法确认当前节点状态")
                        val verifiedMode=api("/configs").getString("mode")
                        if(state.value.copy(proxies=verified,mode=verifiedMode).exitRoutingKey()!=key) {
                            mutable.update { if(it.exitRoutingKey()==key)it.copy(proxies=verified,mode=verifiedMode,exitLocation=null,exitLoading=false) else it }
                            return@collectLatest
                        }
                        mutable.update { if(it.exitRoutingKey()==key && exitRefresh.value==revision)it.copy(exitLocation=result,exitLoading=false,exitError="") else it }
                    } catch(error:Exception) {
                        if(error is CancellationException) {
                            currentCoroutineContext().ensureActive()
                            if(error !is TimeoutCancellationException)throw error
                        }
                        mutable.update { if(it.exitRoutingKey()==key && exitRefresh.value==revision)it.copy(exitLocation=null,exitLoading=false,exitError="出口信息更新失败，请重试") else it }
                    }
                }
        }
        scope.launch {
            var pollFailures=0
            initialization.await()
            while (isActive) {
                if(state.value.phase==VpnPhase.RUNNING)runCatching{sampleTraffic()}
                if (state.value.phase == VpnPhase.RUNNING && foregroundVisible.value) runCatching {
                    val proxies = api("/proxies").optJSONObject("proxies") ?: JSONObject()
                    val connections = api("/connections").optJSONArray("connections") ?: JSONArray()
                    val logs = NativeCore.invoke("logs") as JSONArray
                    val mode = api("/configs").optString("mode", "rule")
                    pollFailures=0
                    mutable.update { if(it.phase==VpnPhase.RUNNING)it.copy(mode=mode,proxies=proxies,connections=connections,logs=(0 until logs.length()).map(logs::getString)) else it }
                }.onFailure { pollFailures++;if(pollFailures==3)message("内核控制接口暂时不可用；VPN状态与实时数据可能不同步，请检查内核日志",MessageKind.ERROR) }
                delay(1500)
            }
        }
    }
    fun homeVisible(visible:Boolean) { homeVisible.value=visible }
    fun foregroundVisible(visible:Boolean) { foregroundVisible.value=visible }
    fun refreshExitLocation() { exitRefresh.update{it+1} }
    private suspend fun sampleTraffic()=trafficSamples.withLock {
        val stats=NativeCore.invoke("stats") as JSONObject
        if(stats.optBoolean("running")) {
            val totals=trafficTotals.observe(stats.optLong("totalUp"),stats.optLong("totalDown"))
            mutable.update { it.copy(totalUp=totals.upload,totalDown=totals.download,
                up=if(it.phase==VpnPhase.RUNNING)stats.optLong("up") else 0,
                down=if(it.phase==VpnPhase.RUNNING)stats.optLong("down") else 0) }
        }
    }
    suspend fun checkpointTraffic()=withContext(Dispatchers.IO) {
        runCatching{sampleTraffic()}
        trafficSamples.withLock{trafficTotals.checkpoint()}
    }
    suspend fun initialized() { initialization.await(); check(state.value.ready) { state.value.error.ifBlank { "初始化未完成" } } }
    fun message(text: String, kind: MessageKind = MessageKind.INFO) {
        mutable.update { it.copy(error = text, messageKind = kind, messageId = it.messageId + 1) }
    }
    fun clearMessage(expectedId: Long? = null) {
        mutable.update { if(expectedId == null || it.messageId == expectedId) it.copy(error = "", messageId = it.messageId + 1) else it }
    }
    fun phase(phase: VpnPhase, activeId: String = "", error: String = "") { synchronized(selectionLock) { mutable.update { it.copy(phase = phase, activeId = activeId, runtimeRevision=it.runtimeRevision+1, error = error, messageKind = if(error.isBlank())MessageKind.INFO else MessageKind.ERROR, messageId = it.messageId + 1, up=0, down=0, exitLocation=null,exitLoading=false,exitError="", proxies=JSONObject(), connections=JSONArray()) } } }
    fun language(value:String){require(value in listOf("zh","en"));prefs.edit().putString("language",value).apply();mutable.update{it.copy(language=value)}}
    fun fontWeight(value:Int){require(value in setOf(-100,0,100));prefs.edit().putInt("fontWeight",value).apply();mutable.update{it.copy(fontWeight=value)}}
    fun fontScale(value:Float){require(value in 0.8f..1.5f);prefs.edit().putFloat("fontScale",value).apply();mutable.update{it.copy(fontScale=value)}}
    fun theme(value: String) { val resolved = ThemePreference.resolve(value); prefs.edit().putString("theme", resolved).apply(); mutable.update { it.copy(theme = resolved) } }
    fun draft(key: String): String = prefs.getString("encrypted-draft.$key", null)?.let(store::decryptText).orEmpty()
    fun saveDraft(key: String, value: String) {
        prefs.edit().putString("encrypted-draft.$key", store.encryptText(value)).apply()
        if (key.startsWith("extension.")) invalidatePreview()
    }
    private fun invalidatePreview() { mutable.update { it.copy(previewRevision=it.previewRevision+1, previewReady=false) } }
    fun extensions(id: String): JSONObject = JSONObject().also { result ->
        listOf("override", "script", "proxies", "groups", "rules").forEach { kind -> draft("extension.$id.$kind").takeIf { it.isNotBlank() }?.let { result.put(kind, it) } }
    }
    suspend fun validate(yaml: String, id: String = ""): JSONObject = withContext(Dispatchers.IO) {
        initialized(); NativeCore.invoke("validate", "yaml" to yaml, "name" to (state.value.profiles.firstOrNull{it.id==id}?.name ?: ""), "global" to extensions("global"), "extensions" to extensions(id)) as JSONObject
    }
    suspend fun saveExtension(scopeId: String, kind: String, value: String) = edits.withLock {
        initialized()
        require(kind in listOf("override","script","proxies","groups","rules")) { "不支持的增强类型" }
        check(state.value.phase in listOf(VpnPhase.STOPPED,VpnPhase.FAILED)) { "请先停止VPN再修改增强" }
        val global=extensions("global")
        val affected=if(scopeId=="global")state.value.profiles else listOf(state.value.profiles.firstOrNull { it.id==scopeId } ?: error("请先选择配置"))
        if(scopeId=="global")global.put(kind,value)
        withContext(Dispatchers.IO) {
            if(affected.isEmpty()) NativeCore.invoke("validate","yaml" to SAMPLE,"global" to global)
            affected.forEach { p ->
                val local=extensions(p.id)
                if(scopeId!="global")local.put(kind,value)
                NativeCore.invoke("validate","yaml" to p.yaml,"name" to p.name,"global" to global,"extensions" to local)
            }
        }
        if(scopeId=="global")state.value.selected?.let { snapshot(it) } else affected.forEach { snapshot(it) }
        saveDraft("extension.$scopeId.$kind",value)
    }
    suspend fun previewExtension(scopeId:String,kind:String,value:String):String = withContext(Dispatchers.IO) {
        initialized()
        val profile=state.value.selected ?: error("请先选择配置")
        require(scopeId=="global" || scopeId==profile.id) { "配置已变化，请重新打开编辑页" }
        val global=extensions("global");val local=extensions(profile.id)
        (if(scopeId=="global")global else local).put(kind,value)
        NativeCore.invoke("render","yaml" to profile.yaml,"name" to profile.name,"global" to global,"extensions" to local) as String
    }
    private val journal get()=android.util.AtomicFile(context.filesDir.resolve("transaction.enc"))
    private fun beginJournal() {
        val profiles=JSONArray();state.value.profiles.forEach{p->profiles.put(JSONObject().put("id",p.id).put("name",p.name).put("yaml",p.yaml).put("source",p.source).put("userAgent",p.userAgent).put("updated",p.updated))}
        val text=store.encryptText(JSONObject().put("profiles",profiles).put("preferences",JSONObject(prefs.all)).toString())
        val file=journal;val stream=file.startWrite();try{stream.write(text.toByteArray());file.finishWrite(stream)}catch(e:Exception){file.failWrite(stream);throw e}
    }
    private fun recoverJournal(){
        val file=journal;if(!file.baseFile.exists())return
        val root=JSONObject(store.decryptText(String(file.readFully())))
        val items=root.getJSONArray("profiles");store.save((0 until items.length()).map{val p=items.getJSONObject(it);Profile(p.getString("id"),p.getString("name"),p.getString("yaml"),p.getLong("updated"),p.optString("source"),subscriptionUserAgent(p.optString("userAgent",DEFAULT_SUBSCRIPTION_UA)))})
        val previous=root.getJSONObject("preferences");val editor=prefs.edit().clear()
        previous.keys().forEach{k->if(k=="fontScale"){editor.putFloat(k,previous.getDouble(k).toFloat())}else when(val v=previous.get(k)){is Boolean->editor.putBoolean(k,v);is Int->editor.putInt(k,v);is Long->editor.putLong(k,v);is Number->editor.putFloat(k,v.toFloat());else->editor.putString(k,v.toString())}}
        check(editor.commit()){ "恢复事务失败" };file.delete()
    }
    suspend fun saveExtensions(id:String,next:JSONObject) = edits.withLock {
        check(state.value.phase in listOf(VpnPhase.STOPPED,VpnPhase.FAILED)) { "请先停止 VPN；保存后下次启动生效" }
        val p=state.value.profiles.first { it.id==id }
        withContext(Dispatchers.IO) { NativeCore.invoke("validate","yaml" to p.yaml,"name" to p.name,"global" to extensions("global"),"extensions" to next) }
        snapshot(p)
        writeExtensions(id,next)
    }
    private fun writeExtensions(id:String,next:JSONObject) {
        val editor=prefs.edit()
        listOf("override","script","proxies","groups","rules").forEach { key -> editor.putString("encrypted-draft.extension.$id.$key",store.encryptText(next.optString(key))) }
        check(editor.commit()) { "保存增强失败" }
        invalidatePreview()
    }
    suspend fun restoreProfile(p:Profile,next:JSONObject) = edits.withLock {
        check(state.value.phase in listOf(VpnPhase.STOPPED,VpnPhase.FAILED)) { "请先停止VPN" }
        withContext(Dispatchers.IO) { NativeCore.invoke("validate","yaml" to p.yaml,"name" to p.name,"global" to extensions("global"),"extensions" to next) }
        val old=state.value.profiles
        old.firstOrNull{it.id==p.id}?.let{snapshot(it)}
        val updated=old.filterNot{it.id==p.id}+p.copy(updated=System.currentTimeMillis())
        withContext(Dispatchers.IO){beginJournal()}
        try { withContext(Dispatchers.IO){store.save(updated)};writeExtensions(p.id,next);journal.delete() }
        catch(e:Exception){ withContext(Dispatchers.IO){recoverJournal()};throw e }
        mutable.update { it.copy(profiles=updated) }
    }
    fun backup(includeSettings:Boolean=true):String {
        val profiles=JSONArray();state.value.profiles.forEach { p -> profiles.put(JSONObject().put("id",p.id).put("name",p.name).put("yaml",p.yaml).put("source",p.source).put("userAgent",p.userAgent).put("updated",p.updated)) }
        val settings=JSONObject()
        prefs.all.keys.filter{it.startsWith("encrypted-draft.")}.forEach { key ->
            val name=key.removePrefix("encrypted-draft.")
            if(!name.startsWith("history.") && name!="connection" && (includeSettings || name.startsWith("extension."))) settings.put(name,draft(name))
        }
        val assets=JSONObject()
        context.filesDir.resolve("core/rules").listFiles()?.filter{it.name.matches(Regex("import-[a-f0-9]{64}\\.mrs"))}?.forEach{file->require(file.length()<=8*1024*1024);assets.put(file.name,android.util.Base64.encodeToString(file.readBytes(),android.util.Base64.NO_WRAP))}
        return JSONObject().put("assets",assets).put("format","clash-android").put("version",1).put("includesSettings",includeSettings).put("profiles",profiles).put("settings",settings).put("theme",state.value.theme).put("language",state.value.language).put("fontScale",state.value.fontScale).put("fontWeight",state.value.fontWeight).put("selected",state.value.selectedId).toString(2)
    }
    suspend fun validateBackup(text:String):JSONObject = withContext(Dispatchers.IO) {
        require(text.length<=32*1024*1024){"备份过大"}
        val root=JSONObject(text);require(root.optString("format")=="clash-android" && root.optInt("version")==1){"不支持的备份格式"}
        val assets=root.optJSONObject("assets")?:JSONObject()
        assets.keys().forEach { name->require(name.matches(Regex("import-[a-f0-9]{64}\\.mrs"))){"非法规则资源路径"};val data=android.util.Base64.decode(assets.getString(name),android.util.Base64.NO_WRAP);require(data.size<=8*1024*1024);val hash=java.security.MessageDigest.getInstance("SHA-256").digest(data).joinToString(""){"%02x".format(it)};require(name=="import-$hash.mrs"){"规则资源校验失败"};val dir=context.filesDir.resolve("core/rules");dir.mkdirs();dir.resolve(name).writeBytes(data)}
        val items=root.getJSONArray("profiles");require(items.length()<=100){"配置数量过多"}
        val settings=root.getJSONObject("settings");validateBackupSettings(settings);val ids=mutableSetOf<String>()
        fun ext(id:String)=JSONObject().also{v->listOf("override","script","proxies","groups","rules").forEach{k->v.put(k,settings.optString("extension.$id.$k"))}}
        for(i in 0 until items.length()) { val p=items.getJSONObject(i);subscriptionUserAgent(p.optString("userAgent",DEFAULT_SUBSCRIPTION_UA));require(ids.add(p.getString("id"))){"配置ID重复"};require(p.getString("name").isNotBlank());NativeCore.invoke("validate","yaml" to p.getString("yaml"),"name" to p.getString("name"),"global" to ext("global"),"extensions" to ext(p.getString("id"))) }
        root
    }
    suspend fun restoreBackup(text:String) = edits.withLock {
        check(state.value.phase in listOf(VpnPhase.STOPPED,VpnPhase.FAILED)){"请先停止VPN"}
        val root=validateBackup(text);val items=root.getJSONArray("profiles");val settings=root.getJSONObject("settings")
        val restored=(0 until items.length()).map { val p=items.getJSONObject(it);Profile(p.getString("id"),p.getString("name"),p.getString("yaml"),p.optLong("updated"),p.optString("source"),subscriptionUserAgent(p.optString("userAgent",DEFAULT_SUBSCRIPTION_UA))) }
        val includesSettings=root.optBoolean("includesSettings",true)
        val editor=prefs.edit();prefs.all.keys.filter{it.startsWith("encrypted-draft.") && (includesSettings || it.startsWith("encrypted-draft.extension.") || it.startsWith("encrypted-draft.history.") || it.startsWith("encrypted-draft.editor.") || it.startsWith("encrypted-draft.scratch.") || it.startsWith("encrypted-draft.pending-update."))}.forEach{editor.remove(it)}
        settings.keys().forEach{key->if(includesSettings || key.startsWith("extension."))editor.putString("encrypted-draft.$key",store.encryptText(settings.getString(key)))}
        val theme=ThemePreference.resolve(if(includesSettings)root.optString("theme",ThemePreference.DEFAULT)else state.value.theme)
        val selected=root.optString("selected").takeIf{id->restored.any{it.id==id}}?:restored.firstOrNull()?.id.orEmpty()
        val language=(if(includesSettings)root.optString("language","zh")else state.value.language).takeIf{it in listOf("zh","en")}?:"zh"
        val scale=(if(includesSettings)root.optDouble("fontScale",1.0).toFloat()else state.value.fontScale).takeIf{it.isFinite()}?.coerceIn(0.8f,1.5f)?:1f
        val weight=(if(includesSettings)root.optInt("fontWeight",0)else state.value.fontWeight).takeIf{it in setOf(-100,0,100)}?:0
        editor.putInt("fontWeight",weight).putString("theme",theme).putString("selected",selected).putString("language",language).putFloat("fontScale",scale)
        withContext(Dispatchers.IO){beginJournal()}
        try{withContext(Dispatchers.IO){store.save(restored)};check(editor.commit()){"恢复设置失败"};journal.delete()}catch(e:Exception){withContext(Dispatchers.IO){recoverJournal()};throw e}
        mutable.update{it.copy(profiles=restored,selectedId=selected,theme=theme,language=language,fontScale=scale,fontWeight=weight,previewRevision=it.previewRevision+1,previewReady=false)}
        cn.nascp.clash.system.scheduleSubscriptions(context,draft("automation-minutes").toLongOrNull()?.coerceAtLeast(15)?:720L,draft("automation-enabled")=="true")
    }
    suspend fun rendered():String = withContext(Dispatchers.IO) {
        val profile=state.value.selected ?: error("请先选择配置")
        NativeCore.invoke("render","yaml" to profile.yaml,"name" to profile.name,"global" to extensions("global"),"extensions" to extensions(profile.id)) as String
    }
    suspend fun saveProfile(name: String, yaml: String, id: String? = null, source: String = "", select: Boolean = true, userAgent: String? = null) = edits.withLock {
        initialized()
        check(state.value.phase !in listOf(VpnPhase.STARTING, VpnPhase.STOPPING)) { "VPN状态切换中，请稍后" }
        check(id == null || state.value.activeId != id) { "请先停止VPN再编辑当前运行配置" }
        require(name.isNotBlank()) { "请输入配置名称" }
        require(id==null || state.value.profiles.any{it.id==id}) { "配置已删除，请重新选择" }
        withContext(Dispatchers.IO){NativeCore.invoke("validate","yaml" to yaml,"name" to name,"global" to extensions("global"),"extensions" to extensions(id.orEmpty()))}
        val agent = subscriptionUserAgent(userAgent ?: state.value.profiles.firstOrNull { it.id == id }?.userAgent ?: DEFAULT_SUBSCRIPTION_UA)
        val profile = Profile(id ?: UUID.randomUUID().toString(), name.trim(), yaml, System.currentTimeMillis(), source, agent)
        state.value.profiles.firstOrNull { it.id == profile.id }?.let { snapshot(it) }
        val updated = state.value.profiles.filterNot { it.id == profile.id } + profile
        withContext(Dispatchers.IO) { store.save(updated) }
        val selected=if(select && state.value.phase in listOf(VpnPhase.STOPPED,VpnPhase.FAILED))profile.id else state.value.selectedId
        prefs.edit().putString("selected", selected).apply()
        mutable.update { it.copy(profiles = updated, selectedId = selected) }
    }
    fun selectProfile(id: String) {
        check(state.value.phase !in listOf(VpnPhase.STARTING, VpnPhase.RUNNING, VpnPhase.STOPPING)) { "请先停止VPN，再切换配置" }
        require(state.value.profiles.any { it.id == id })
        prefs.edit().putString("selected", id).apply(); mutable.update { it.copy(selectedId = id) }
    }
    suspend fun deleteProfile(id: String) = edits.withLock {
        check(state.value.phase == VpnPhase.STOPPED || state.value.phase == VpnPhase.FAILED) { "请先停止VPN" }
        val updated = state.value.profiles.filterNot { it.id == id }
        withContext(Dispatchers.IO) { store.save(updated) }
        val selected = if (state.value.selectedId == id) updated.firstOrNull()?.id.orEmpty() else state.value.selectedId
        val editor = prefs.edit().putString("selected", selected)
        prefs.all.keys.filter { key ->
            key.startsWith("encrypted-draft.extension.$id.") || key.startsWith("encrypted-draft.editor.$id.") ||
            key.startsWith("encrypted-draft.scratch.extension.$id.") ||
            key in listOf("proxy-selections.$id", "history.$id", "scratch.yaml.$id", "pending-update.$id", "auto-last.$id", "auto-attempt.$id", "profile-interval.$id").map { "encrypted-draft.$it" }
        }.forEach { editor.remove(it) }
        editor.apply()
        mutable.update { it.copy(profiles = updated, selectedId = selected) }
    }
    suspend fun download(address: String, userAgent: String = DEFAULT_SUBSCRIPTION_UA): String {
        val key = "subscription-error.${address.hashCode()}"
        val content = try { fetchSubscription(address, userAgent) }
        catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            if (error is SubscriptionHttpException || error is SubscriptionNetworkException || error is SubscriptionDownloadTimeoutException) {
                val diagnostic = "${java.util.Date()}\n${error.message}"
                saveDraft(key, diagnostic)
                saveDraft("subscription-last-error", diagnostic)
            }
            throw error
        }
        currentCoroutineContext().ensureActive()
        val info=JSONObject().put("fetchedAt",System.currentTimeMillis())
        content.userInfo?.split(';')?.forEach{item->val pair=item.trim().split('=',limit=2);if(pair.size==2&&pair[0] in listOf("upload","download","total","expire"))pair[1].toLongOrNull()?.takeIf{it>=0}?.let{info.put(pair[0],it)}}
        saveDraft("subscription-info.${address.hashCode()}",info.toString())
        saveDraft(key, "")
        return content.yaml
    }
    suspend fun api(path: String, method: String = "GET", data: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        check(state.value.phase == VpnPhase.RUNNING) { "请先启动VPN" }
        val connection = URL("http://127.0.0.1:19097$path").openConnection() as HttpURLConnection
        connection.requestMethod = method; connection.connectTimeout = 3000; connection.readTimeout = 20000
        connection.setRequestProperty("Authorization", "Bearer $secret")
        try {
            if (data != null) { connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json"); connection.outputStream.use { it.write(data.toString().toByteArray()) } }
            check(connection.responseCode in 200..299) { "内核请求失败：${connection.responseCode}" }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            if (body.isBlank()) JSONObject() else JSONObject(body)
        } finally { connection.disconnect() }
    }
    fun selections(id: String): JSONObject = runCatching { JSONObject(draft("proxy-selections.$id").ifBlank { "{}" }) }.getOrDefault(JSONObject())
    private fun saveSelections(id: String, choices: JSONObject) {
        check(prefs.edit().putString("encrypted-draft.proxy-selections.$id", store.encryptText(choices.toString())).commit()) { "保存节点选择失败" }
    }
    private fun previewWithSelections(proxies: JSONObject, id: String): JSONObject {
        val choices=selections(id)
        proxies.keys().forEach { name ->
            val group=proxies.optJSONObject(name) ?: return@forEach
            val selected=choices.optString(name)
            val members=group.optJSONArray("all")
            if (group.optString("type")=="Selector" && members!=null && (0 until members.length()).any { members.optString(it)==selected }) group.put("now",selected)
        }
        return proxies
    }
    suspend fun selectProxy(group: String, node: String) = withContext(Dispatchers.IO) {
        val runtimeProfile = synchronized(selectionLock) {
            val current=state.value
            check(current.phase in listOf(VpnPhase.STOPPED,VpnPhase.FAILED,VpnPhase.RUNNING)) { "请等待 VPN 状态切换完成" }
            val profile=current.selected ?: error("请先选择配置")
            val source=current.visibleProxies.optJSONObject(group) ?: error("代理组已变化，请重新选择")
            val members=source.optJSONArray("all")
            require(source.optString("type")=="Selector" && members!=null && (0 until members.length()).any { members.optString(it)==node }) { "此分组或节点不支持手动选择" }
            if (current.phase==VpnPhase.RUNNING) profile.id else {
                val choices=selections(profile.id).put(group,node)
                saveSelections(profile.id,choices)
                val preview=previewWithSelections(JSONObject(current.preview.toString()),profile.id)
                mutable.update { it.copy(preview=preview) }
                null
            }
        }
        if (runtimeProfile!=null) {
            api("/proxies/${java.net.URLEncoder.encode(group,"UTF-8").replace("+","%20")}","PUT",JSONObject().put("name",node))
            synchronized(selectionLock) {
                if (state.value.phase==VpnPhase.RUNNING && state.value.activeId==runtimeProfile) {
                    saveSelections(runtimeProfile,selections(runtimeProfile).put(group,node))
                    val proxies=JSONObject(state.value.proxies.toString())
                    proxies.optJSONObject(group)?.put("now",node)
                    mutable.update { it.copy(proxies=proxies,exitLocation=null,exitError="") }
                }
            }
        }
    }
    suspend fun delayTest(name:String):Long {
        val url=draft("test-url").ifBlank{"https://www.gstatic.com/generate_204"}
        val timeout=(draft("test-timeout").toIntOrNull()?:5000).coerceIn(1000,15000)
        return api("/proxies/${java.net.URLEncoder.encode(name,"UTF-8").replace("+","%20")}/delay?timeout=$timeout&url=${java.net.URLEncoder.encode(url,"UTF-8")}").getLong("delay")
    }
    suspend fun mode(mode: String) { withContext(Dispatchers.IO){NativeCore.invoke("mode","value" to mode)}; mutable.update { it.copy(mode = mode,exitLocation=null,exitError="") } }
    companion object {
        @Volatile private var instance: ClashRepository? = null
        fun get(context: Context): ClashRepository = instance ?: synchronized(this) { instance ?: ClashRepository(context).also { instance = it } }
        const val SAMPLE = """# 直连验证配置：用于验证VPN接管，不提供远程代理节点
mode: rule
log-level: info
dns:
  enable: true
  enhanced-mode: redir-host
  nameserver:
    - 1.1.1.1
    - 223.5.5.5
proxies: []
proxy-groups:
  - name: 默认策略
    type: select
    proxies:
      - DIRECT
      - REJECT
rules:
  - MATCH,默认策略
"""
    }
}
