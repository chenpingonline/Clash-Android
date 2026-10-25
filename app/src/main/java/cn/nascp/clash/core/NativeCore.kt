package cn.nascp.clash.core

import org.json.JSONObject

object NativeCore {
    val available = runCatching { System.loadLibrary("clashcore") }.isSuccess
    @Volatile private var vpnService: android.net.VpnService? = null
    fun attachVpn(service: android.net.VpnService) { vpnService = service }
    fun detachVpn(service: android.net.VpnService) { if (vpnService === service) vpnService = null }
    // Called by native dialers, including on Go-owned threads. Never acquire invoke's monitor.
    @JvmStatic fun protectSocket(fd: Int): Boolean = vpnService?.protect(fd) ?: false
    private external fun call(input: String): String
    @Synchronized fun invoke(op: String, vararg fields: Pair<String, Any>): Any? {
        check(available) { "未找到内核库，请先运行 scripts/build-core.sh" }
        val request = JSONObject().put("op", op)
        fields.forEach { request.put(it.first, it.second) }
        val result = JSONObject(call(request.toString()))
        if (result.has("error")) error(result.getString("error"))
        return result.opt("data")
    }
}
