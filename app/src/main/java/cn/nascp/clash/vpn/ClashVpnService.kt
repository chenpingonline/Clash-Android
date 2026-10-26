package cn.nascp.clash.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import cn.nascp.clash.MainActivity
import cn.nascp.clash.core.NativeCore
import cn.nascp.clash.data.ClashRepository
import cn.nascp.clash.data.VpnPhase
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ClashVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transition = Mutex()
    private lateinit var repo: ClashRepository
    private var geoJob:Job?=null
    private var tun: ParcelFileDescriptor? = null
    private val networkCallback=object:android.net.ConnectivityManager.NetworkCallback(){
        override fun onAvailable(network:android.net.Network){scope.launch{if(repo.state.value.phase==VpnPhase.RUNNING)runCatching{NativeCore.invoke("network-changed");repo.refreshExitLocation()}}}
    }
    @Volatile private var stopping = false
    override fun onCreate() {
        super.onCreate()
        repo = ClashRepository.get(this)
        getSystemService(android.net.ConnectivityManager::class.java).registerNetworkCallback(android.net.NetworkRequest.Builder().addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),networkCallback)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("vpn", "VPN运行状态", NotificationManager.IMPORTANCE_LOW))
    }
    private fun notification(text: String) = NotificationCompat.Builder(this, "vpn")
        .setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle(applicationInfo.loadLabel(packageManager)).setContentText(text)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .addAction(0, "停止", PendingIntent.getService(this, 1, Intent(this, ClashVpnService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE))
        .setOngoing(true).build()
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stop(); return if(isAlwaysOn)START_STICKY else START_NOT_STICKY }
        startForeground(1, notification(if (repo.state.value.phase == VpnPhase.RUNNING) "VPN已启动" else "正在建立VPN…"))
        if (repo.state.value.phase == VpnPhase.RUNNING || repo.state.value.phase == VpnPhase.STARTING) return START_STICKY
        if(repo.state.value.phase==VpnPhase.STOPPING)return START_NOT_STICKY
        stopping = false
        repo.phase(VpnPhase.STARTING)
        scope.launch {
            transition.withLock {
                try {
                    repo.initialized()
                    check(!stopping) { "启动已取消" }
                    val profile = repo.state.value.selected ?: error("请先导入配置")
                    repo.validate(profile.yaml,profile.id)
                    val options=org.json.JSONObject(repo.draft("vpn-options").ifBlank{"{}"})
                    val mtu=options.optInt("mtu",1500);require(mtu in 1280..9000){"MTU需在1280–9000之间"}
                    val ipv6=options.optBoolean("ipv6",false)
                    val excluded=options.optString("exclude").lines().filter{it.isNotBlank()}.toMutableList()
                    if(options.optBoolean("bypassLan",false))excluded.addAll(listOf("10.0.0.0/8","172.16.0.0/12","192.168.0.0/16","169.254.0.0/16"))
                    fun baseBuilder():Builder {
                        val b=Builder().setSession("Clash · ${profile.name}").setMtu(mtu).addAddress("172.19.0.1",30).addDnsServer("172.19.0.2")
                        Routes.included(excluded).forEach{b.addRoute(it.text(),it.prefix)}
                        if(ipv6)b.addAddress("fdfe:dcba:9876::1",126).addRoute("::",0).addDnsServer("fdfe:dcba:9876::2")
                        return b
                    }
                    val mode=repo.draft("app-mode").ifBlank{"全部应用"}
                    val packages=repo.draft("app-packages").lines().filter{it.isNotBlank()&&it!=packageName}
                    val actualBuilder=baseBuilder()
                    if(mode=="仅选中") {require(packages.isNotEmpty()){ "至少选择一个应用" };actualBuilder.addAllowedApplication(packageName);packages.forEach{actualBuilder.addAllowedApplication(it)}}
                    else {if(mode=="排除选中")packages.forEach{actualBuilder.addDisallowedApplication(it)}}
                    NativeCore.attachVpn(this@ClashVpnService)
                    tun = actualBuilder.establish() ?: error("VPN授权已失效，请重新授权")
                    val runtime=NativeCore.invoke("start", "yaml" to profile.yaml, "name" to profile.name, "selections" to repo.selections(profile.id), "global" to repo.extensions("global"), "extensions" to repo.extensions(profile.id), "fd" to tun!!.fd, "secret" to repo.secret, "mtu" to mtu, "ipv6" to ipv6) as org.json.JSONObject
                    check(!stopping) { "启动已取消" }
                    repo.phase(VpnPhase.RUNNING, profile.id)
                    if(runtime.optInt("invalidSelections")>0)repo.message("部分预选节点已失效，已使用当前可用节点")
                    getSystemService(NotificationManager::class.java).notify(1, notification("VPN已启动 · ${profile.name}"))
                    geoJob?.cancel()
                    if(runtime.optBoolean("geoAuto"))geoJob=scope.launch{
                        val interval=runtime.optLong("geoInterval",24).coerceAtLeast(1)*3600000L
                        while(isActive){
                            val last=repo.draft("geo-last-attempt").toLongOrNull()?:0L
                            delay((interval-(System.currentTimeMillis()-last)).coerceAtLeast(60000))
                            if(repo.state.value.phase!=VpnPhase.RUNNING)break
                            repo.saveDraft("geo-last-attempt",System.currentTimeMillis().toString())
                            val outcome=runCatching{NativeCore.invoke("geo-update")}
                            repo.saveDraft("geo-last-result",if(outcome.isSuccess)"更新完成"else"更新失败，旧资源保留")
                        }
                    }
                } catch (e: Exception) {
                    runCatching { repo.checkpointTraffic() }
                    runCatching { NativeCore.invoke("stop") }
                    tun?.close(); tun = null
                    NativeCore.detachVpn(this@ClashVpnService)
                    withContext(Dispatchers.Main) {
                        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                        repo.phase(if (stopping) VpnPhase.STOPPED else VpnPhase.FAILED, error = if (stopping) "" else e.message ?: "VPN启动失败")
                    }
                }
            }
        }
        return START_STICKY
    }
    private fun stop(force:Boolean=false) {
        if(isAlwaysOn && !force){repo.message("系统已启用始终开启VPN，请先在系统VPN设置中关闭该选项",cn.nascp.clash.data.MessageKind.ERROR);return}
        stopping = true
        geoJob?.cancel();geoJob=null
        repo.phase(VpnPhase.STOPPING)
        scope.launch {
            transition.withLock {
                runCatching { repo.checkpointTraffic() }
                runCatching { NativeCore.invoke("stop") }
                tun?.close(); tun = null
                NativeCore.detachVpn(this@ClashVpnService)
                withContext(Dispatchers.Main) {
                    stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                    repo.phase(VpnPhase.STOPPED)
                }
            }
        }
    }
    override fun onRevoke() { stop(true); super.onRevoke() }
    override fun onDestroy() {
        runCatching{getSystemService(android.net.ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)}
        stopping = true
        scope.launch {
            transition.withLock {
                // A completed stop already released the core. Do not let destruction of
                // an older Service instance shut down a subsequent start.
                if (tun != null) {
                    runCatching { repo.checkpointTraffic() }
                    runCatching { NativeCore.invoke("stop") }
                    tun?.close(); tun = null
                    NativeCore.detachVpn(this@ClashVpnService)
                    if (repo.state.value.phase != VpnPhase.FAILED) repo.phase(VpnPhase.STOPPED)
                }
            }
            scope.cancel()
        }
        super.onDestroy()
    }
    companion object { const val STOP = "cn.nascp.clash.STOP" }
}
