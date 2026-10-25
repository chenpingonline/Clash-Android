package cn.nascp.clash

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.core.NativeCore
import cn.nascp.clash.data.*
import cn.nascp.clash.vpn.ClashVpnService
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkIntegrationTest {
 @Test(timeout=120000) fun vpnRoutesTcpUdpDnsAndExplicitProxy()=runBlocking {
    val instrumentation=InstrumentationRegistry.getInstrumentation()
    assumeTrue("Run scripts/qa/network-fixture.py and pass networkFixture=true",InstrumentationRegistry.getArguments().getString("networkFixture")=="true")
    val context=instrumentation.targetContext
    val scenario=androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
    // Deterministic emulator fixture authorization; UI consent is verified separately.
    ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("appops set ${context.packageName} ACTIVATE_VPN allow")).use{it.readBytes()}
    assertNull("Emulator VPN fixture authorization failed",VpnService.prepare(context))
    val repo=ClashRepository.get(context);repo.initialized();val backup=repo.backup()
    suspend fun waitFor(phase:VpnPhase){withTimeout(15000){while(repo.state.value.phase!=phase){delay(100)}}}
    fun shell(command:String):ByteArray {
        val script=java.io.File(context.getExternalFilesDir(null),"network-fixture.sh")
        script.writeText(command.replace("toybox nc","toybox timeout 4 toybox nc")+"\n")
        return ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("sh ${script.absolutePath}")).use{it.readBytes()}
    }
    try {
        repo.saveDraft("vpn-options","{}");repo.saveDraft("app-mode","全部应用")
        repo.saveProfile("Network fixture",ClashRepository.SAMPLE.replace("proxies: []","proxies:\n  - name: FixtureSocks\n    type: socks5\n    server: 10.0.2.2\n    port: 18082").replace("      - DIRECT","      - FixtureSocks\n      - DIRECT").replace("1.1.1.1","udp://10.0.2.2:18053").replace("    - 223.5.5.5\n","")+"\nmixed-port: 17890\n")
        ContextCompat.startForegroundService(context,Intent(context,ClashVpnService::class.java));waitFor(VpnPhase.RUNNING)
        repo.api("/proxies/"+java.net.URLEncoder.encode("默认策略","UTF-8"),"PUT",JSONObject().put("name","DIRECT"))
        val tcpResponse=String(shell("printf 'GET / HTTP/1.0\\r\\nHost: fixture\\r\\n\\r\\n' | toybox nc -w 3 10.0.2.2 18080"))
        assertTrue("TCP fixture request failed: $tcpResponse",tcpResponse.contains("CLASH_ANDROID_FIXTURE_OK"))
        assertTrue(String(shell("printf 'PING' | toybox nc -u -w 2 10.0.2.2 18081")).contains("ECHO:PING"))
        val dns=byteArrayOf(0x12,0x34,1,0,0,1,0,0,0,0,0,0,7)+"fixture".toByteArray()+byteArrayOf(4)+"test".toByteArray()+byteArrayOf(0,0,1,0,1)
        val encoded=android.util.Base64.encodeToString(dns,android.util.Base64.NO_WRAP)
        val answer=shell("printf '$encoded' | base64 -d | toybox nc -u -w 2 172.19.0.2 53")
        assertTrue("DNS reply was empty",answer.size>=16);assertArrayEquals(byteArrayOf(203.toByte(),0,113,7),answer.takeLast(4).toByteArray())
        val explicitResponse=withContext(Dispatchers.IO){java.net.Socket("127.0.0.1",17890).use{socket->socket.soTimeout=5000;socket.getOutputStream().write("GET http://10.0.2.2:18080/ HTTP/1.0\r\nHost: 10.0.2.2:18080\r\n\r\n".toByteArray());socket.getInputStream().bufferedReader().readText()}}
        assertTrue("Explicit proxy failed: $explicitResponse",explicitResponse.contains("CLASH_ANDROID_FIXTURE_OK"))
        repo.api("/proxies/"+java.net.URLEncoder.encode("默认策略","UTF-8"),"PUT",JSONObject().put("name","FixtureSocks"))
        assertTrue(String(shell("printf 'GET / HTTP/1.0\\r\\nHost: fixture\\r\\n\\r\\n' | toybox nc -w 3 10.0.2.2 18080")).contains("CLASH_ANDROID_FIXTURE_OK"))
        val count=withContext(Dispatchers.IO){java.net.Socket("10.0.2.2",18080).use{socket->socket.soTimeout=5000;socket.getOutputStream().write("GET /socks-count HTTP/1.0\r\nHost: fixture\r\n\r\n".toByteArray());socket.getInputStream().bufferedReader().readText().substringAfter("\r\n\r\n").trim().toInt()}}
        assertTrue("SOCKS5 upstream was not used",count>0)
        repo.mode("direct");assertEquals("direct",repo.api("/configs").getString("mode"));repo.mode("rule")
        NativeCore.invoke("rule-disable","value" to "{\"0\":true}")
        assertTrue(repo.api("/rules").getJSONArray("rules").getJSONObject(0).getJSONObject("extra").getBoolean("disabled"))
        NativeCore.invoke("rule-disable","value" to "{\"0\":false}")
        repo.api("/proxies/"+java.net.URLEncoder.encode("默认策略","UTF-8"),"PUT",JSONObject().put("name","REJECT"))
        assertFalse(String(shell("printf 'GET / HTTP/1.0\\r\\nHost: fixture\\r\\n\\r\\n' | toybox nc -w 2 10.0.2.2 18080")).contains("CLASH_ANDROID_FIXTURE_OK"))
        repo.api("/proxies/"+java.net.URLEncoder.encode("默认策略","UTF-8"),"PUT",JSONObject().put("name","DIRECT"))
        context.startService(Intent(context,ClashVpnService::class.java).setAction(ClashVpnService.STOP));waitFor(VpnPhase.STOPPED)
        repo.saveDraft("vpn-options","{\"mtu\":100}")
        ContextCompat.startForegroundService(context,Intent(context,ClashVpnService::class.java));waitFor(VpnPhase.FAILED)
        repo.saveDraft("vpn-options","{\"ipv6\":true,\"mtu\":1400}")
        ContextCompat.startForegroundService(context,Intent(context,ClashVpnService::class.java));waitFor(VpnPhase.RUNNING)
        assertTrue(String(shell("ip -6 addr")).contains("fdfe:dcba:9876::1"))
        context.startService(Intent(context,ClashVpnService::class.java).setAction(ClashVpnService.STOP));waitFor(VpnPhase.STOPPED)
        repo.saveDraft("app-mode","排除选中");repo.saveDraft("app-packages","com.android.shell")
        ContextCompat.startForegroundService(context,Intent(context,ClashVpnService::class.java));waitFor(VpnPhase.RUNNING)
        repo.mode("global");repo.api("/proxies/GLOBAL","PUT",JSONObject().put("name","REJECT"))
        assertTrue("Excluded shell should bypass REJECT",String(shell("printf 'GET / HTTP/1.0\\r\\nHost: fixture\\r\\n\\r\\n' | toybox nc -w 3 10.0.2.2 18080")).contains("CLASH_ANDROID_FIXTURE_OK"))
        context.startService(Intent(context,ClashVpnService::class.java).setAction(ClashVpnService.STOP));waitFor(VpnPhase.STOPPED)
        repo.saveDraft("app-mode","仅选中")
        ContextCompat.startForegroundService(context,Intent(context,ClashVpnService::class.java));waitFor(VpnPhase.RUNNING)
        repo.mode("global");repo.api("/proxies/GLOBAL","PUT",JSONObject().put("name","REJECT"))
        assertFalse("Included shell should be rejected",String(shell("printf 'GET / HTTP/1.0\\r\\nHost: fixture\\r\\n\\r\\n' | toybox nc -w 2 10.0.2.2 18080")).contains("CLASH_ANDROID_FIXTURE_OK"))
    } finally {
        context.startService(Intent(context,ClashVpnService::class.java).setAction(ClashVpnService.STOP));waitFor(VpnPhase.STOPPED);repo.restoreBackup(backup);scenario.close()
    }
 }
}
