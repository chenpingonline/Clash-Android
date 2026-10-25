package cn.nascp.clash

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.core.NativeCore
import cn.nascp.clash.data.*
import cn.nascp.clash.system.scheduleSubscriptions
import cn.nascp.clash.vpn.ClashVpnService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** Explicit opt-in only. Supply the private URL as files/live-subscription-url.txt, never a runner argument. */
@RunWith(AndroidJUnit4::class)
class LiveSubscriptionTest {
    @Test(timeout=600000) fun downloadLatencyAndRealInternetAccess()=runBlocking<Unit> {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSubscription")=="true")
        val context=instrumentation.targetContext
        val privateInput=File(context.filesDir,"live-subscription-url.txt")
        val address=privateInput.readText().trim()
        val repo=ClashRepository.get(context);repo.initialized();val backup=repo.backup()
        val report=JSONObject().put("environment","Android 16 arm64 emulator").put("userAgent","CMFA").put("geodataSeededFromLocalFixture",File(context.filesDir,"core/Country.mmdb").exists())
        val output=File(context.getExternalFilesDir(null),"live-subscription-report.json")
        fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use{it.readBytes()}
        fun publish(stage:String) {
            report.put("stage",stage)
            output.writeText(report.toString(2))
            shell("cp ${output.absolutePath} /data/local/tmp/clash-live-subscription-report.json")
        }
        fun failure(error:Exception):String {
            if(error is SubscriptionHttpException)return "HTTP_${error.status}"
            val text=error.message.orEmpty().lowercase()
            return when {
                "geo" in text -> "GEODATA_ERROR"
                "certificate" in text -> "TLS_CERTIFICATE_ERROR"
                "yaml" in text -> "YAML_ERROR"
                "timeout" in text || "超时" in text -> "TIMEOUT"
                else -> error.javaClass.simpleName
            }
        }
        suspend fun waitFor(phase:VpnPhase) = withTimeout(90000) {
            while(repo.state.value.phase!=phase){
                if(repo.state.value.phase==VpnPhase.FAILED)error("VPN_START_FAILED")
                delay(100)
            }
        }
        fun client(proxy:Proxy)=OkHttpClient.Builder().proxy(proxy).connectTimeout(15,TimeUnit.SECONDS)
            .readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
        val tunClient=client(Proxy.NO_PROXY)
        val explicitClient=client(Proxy(Proxy.Type.HTTP,InetSocketAddress("127.0.0.1",17890)))
        suspend fun access(client:OkHttpClient,url:String,node:String?=null):JSONObject=withContext(Dispatchers.IO) {
            val start=System.nanoTime()
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    val result=JSONObject().put("status",response.code)
                    if(node!=null) {
                        val connections=repo.api("/connections").optJSONArray("connections")?:JSONArray()
                        var matched=false
                        for(i in 0 until connections.length()) {
                            val item=connections.getJSONObject(i)
                            if(item.optJSONObject("metadata")?.optString("host")==java.net.URI(url).host) {
                                val chain=item.optJSONArray("chains")?:JSONArray()
                                for(j in 0 until chain.length())if(chain.optString(j)==node)matched=true
                            }
                        }
                        result.put("selectedNodeObservedInCoreConnection",matched)
                    }
                    val body=response.body?.byteStream()?.use{input->
                        val buffer=ByteArray(64*1024);var size=0
                        while(size<buffer.size){val n=input.read(buffer,size,buffer.size-size);if(n<0)break;size+=n}
                        String(buffer,0,size,Charsets.UTF_8)
                    }.orEmpty()
                    result.put("bytesRead",body.toByteArray().size).put("elapsedMs",(System.nanoTime()-start)/1_000_000)
                    if(url.contains("ipify"))result.put("privateExitAddress",runCatching{JSONObject(body).getString("ip")}.getOrDefault(""))
                    if(url.contains("wikipedia"))result.put("expectedContent",body.contains("wikipedia",true))
                    if(java.net.URI(url).host=="www.google.com" || java.net.URI(url).host=="www.youtube.com")
                        result.put("expectedContent",body.contains("<html",true)&&body.contains(if(url.contains("youtube"))"youtube" else "google",true))
                    if(url.contains("cdn-cgi/trace"))result.put("expectedContent",body.contains("ip=")&&body.contains("tls="))
                    result
                }
            }catch(error:Exception){JSONObject().put("error",failure(error)).put("elapsedMs",(System.nanoTime()-start)/1_000_000)}
        }
        var scenario:androidx.test.core.app.ActivityScenario<MainActivity>?=null
        try {
            publish("baseline")
            val baseline=access(tunClient,"https://api.ipify.org?format=json")
            val baselineIp=baseline.optString("privateExitAddress");baseline.remove("privateExitAddress")
            report.put("baseline",baseline)
            scheduleSubscriptions(context,720,false)
            repo.saveDraft("automation-enabled","false")
            publish("download")
            val start=System.nanoTime()
            val yaml=repo.download(address,CMFA_SUBSCRIPTION_UA)
            report.put("download",JSONObject().put("success",true).put("bytes",yaml.toByteArray().size).put("elapsedMs",(System.nanoTime()-start)/1_000_000))
            publish("validate")
            val validation=repo.validate(yaml)
            report.put("validation",JSONObject().put("success",true).put("proxiesAndGroups",validation.optInt("proxies")).put("rules",validation.optInt("rules")))
            repo.saveProfile("Live subscription validation",yaml,source=address,userAgent=CMFA_SUBSCRIPTION_UA)
            repo.saveExtension(repo.state.value.selectedId,"override","mixed-port: 17890\nport: 0\nsocks-port: 0\nallow-lan: false\nbind-address: 127.0.0.1\nauthentication: []\nmode: global\nlog-level: error\n")
            repo.saveDraft("vpn-options","{}");repo.saveDraft("app-mode","全部应用")
            repo.saveDraft("test-timeout","8000")
            scenario=androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
            shell("appops set ${context.packageName} ACTIVATE_VPN allow")
            check(VpnService.prepare(context)==null){"VPN_AUTHORIZATION_FAILED"}
            publish("start-vpn")
            ContextCompat.startForegroundService(context,Intent(context,ClashVpnService::class.java))
            waitFor(VpnPhase.RUNNING)
            report.put("vpnStarted",true)
            repo.mode("global")
            val proxies=repo.api("/proxies").getJSONObject("proxies")
            val source=(NativeCore.invoke("decode","value" to yaml) as JSONObject).getJSONArray("proxies")
            val nodes=(0 until source.length()).map{source.getJSONObject(it).getString("name")}.filter{proxies.has(it)}
            report.put("nodeCount",nodes.size)
            publish("latency")
            val gate=Semaphore(3)
            val incrementalLatency=JSONArray()
            var testedNodes=0
            val requested=InstrumentationRegistry.getArguments().getString("nodeIndexes")?.split(",")?.mapNotNull{it.toIntOrNull()?.minus(1)}?.toSet()
            val times=coroutineScope { nodes.mapIndexed{index,name->index to name}.filter{requested==null || it.first in requested}.map{(index,name)->async {
                gate.withPermit {
                    val result=runCatching{repo.delayTest(name)}
                    testedNodes++
                    incrementalLatency.put(JSONObject().put("node","Node-%03d".format(index+1)).put("delayMs",result.getOrNull()?:JSONObject.NULL))
                    report.put("testedNodes",testedNodes).put("latency",incrementalLatency);publish("latency")
                    Triple(index,name,result.getOrNull())
                }
            }}.awaitAll() }
            val latency=JSONArray()
            times.forEach{(index,_,ms)->latency.put(JSONObject().put("node","Node-%03d".format(index+1)).put("delayMs",ms?:JSONObject.NULL))}
            report.put("latency",latency).put("responsiveNodes",times.count{it.third!=null})
            val ranked=times.filter{it.third!=null}.sortedBy{it.third}.take(3).ifEmpty{times.take(3)}
            publish("real-access")
            val accesses=JSONArray()
            for((index,name,ms) in ranked) {
                tunClient.connectionPool.evictAll();explicitClient.connectionPool.evictAll()
                repo.api("/connections","DELETE")
                report.put("connectionsResetBeforeEachNode",true)
                repo.api("/proxies/GLOBAL","PUT",JSONObject().put("name",name))
                val selected=repo.api("/proxies/GLOBAL").optString("now")==name
                val entry=JSONObject().put("node","Node-%03d".format(index+1)).put("delayMs",ms).put("globalSelectionVerified",selected)
                val checks=JSONArray()
                for(url in listOf("https://www.gstatic.com/generate_204","https://www.google.com/","https://www.youtube.com/","https://www.cloudflare.com/cdn-cgi/trace","https://www.wikipedia.org/")) {
                    checks.put(access(tunClient,url,name).put("site",java.net.URI(url).host))
                }
                entry.put("tunAccess",checks)
                entry.put("explicitProxyAccess",access(explicitClient,"https://www.gstatic.com/generate_204",name))
                val exit=access(tunClient,"https://api.ipify.org?format=json")
                val exitIp=exit.optString("privateExitAddress");exit.remove("privateExitAddress")
                exit.put("exitAddressObtained",exitIp.isNotEmpty()).put("differsFromBaseline",if(baselineIp.isNotEmpty()&&exitIp.isNotEmpty())exitIp!=baselineIp else JSONObject.NULL)
                entry.put("exitCheck",exit)
                // Bounded 1 MiB throughput sample, using this node and a fresh connection.
                val throughput=withContext(Dispatchers.IO) {
                    val start=System.nanoTime()
                    try {
                        tunClient.newCall(Request.Builder().url("https://speed.cloudflare.com/__down?bytes=1048576").build()).execute().use{response->
                            val bytes=response.body?.byteStream()?.use{input->val buffer=ByteArray(8192);var total=0L;while(true){val n=input.read(buffer);if(n<0)break;total+=n;check(total<=1048576)};total}?:0L
                            val duration=(System.nanoTime()-start)/1_000_000L
                            JSONObject().put("status",response.code).put("bytes",bytes).put("elapsedMs",duration)
                                .put("sampleMbps",if(duration>0)bytes*8.0/(duration*1000.0)else 0.0)
                        }
                    }catch(error:Exception){JSONObject().put("error",failure(error))}
                }
                entry.put("throughputSample",throughput)
                accesses.put(entry);report.put("realAccess",accesses);publish("real-access")
            }
            report.put("completed",true)
        }catch(error:Exception){report.put("failureStage",report.optString("stage")).put("fatal",failure(error));if(repo.state.value.phase==VpnPhase.FAILED)report.put("vpnFailureCategory",failure(IllegalStateException(repo.state.value.error)))}
        finally {
            publish("cleanup")
            runCatching{
                if(repo.state.value.phase!=VpnPhase.STOPPED){context.startService(Intent(context,ClashVpnService::class.java).setAction(ClashVpnService.STOP));waitFor(VpnPhase.STOPPED)}
                repo.restoreBackup(backup)
            }.onFailure{report.put("cleanupError",it.javaClass.simpleName)}
            privateInput.delete()
            shell("appops set ${context.packageName} ACTIVATE_VPN default")
            tunClient.connectionPool.evictAll();explicitClient.connectionPool.evictAll()
            scenario?.close()
            publish("finished")
        }
        val accesses=report.optJSONArray("realAccess")?:JSONArray()
        var pageVerified=false
        for(i in 0 until accesses.length()) {
            val checks=accesses.getJSONObject(i).optJSONArray("tunAccess")?:JSONArray()
            for(j in 0 until checks.length()) {
                val check=checks.getJSONObject(j)
                if(check.optString("site") in listOf("www.google.com","www.youtube.com") && check.optInt("status")==200 && check.optBoolean("expectedContent"))pageVerified=true
            }
        }
        org.junit.Assert.assertTrue("Live access not verified; inspect the sanitized report",report.optBoolean("completed") && pageVerified)
    }
}
