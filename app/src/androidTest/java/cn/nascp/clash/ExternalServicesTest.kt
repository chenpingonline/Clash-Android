package cn.nascp.clash

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.ListenableWorker
import cn.nascp.clash.data.*
import cn.nascp.clash.system.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

@RunWith(AndroidJUnit4::class)
class ExternalServicesTest {
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 private suspend fun tls(block:suspend(MockWebServer,HandshakeCertificates)->Unit) {
  val cert=HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
  val server=MockWebServer();server.useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory(),false)
  val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
  val previous=HttpsURLConnection.getDefaultSSLSocketFactory()
  server.start();HttpsURLConnection.setDefaultSSLSocketFactory(trust.sslSocketFactory())
  try{block(server,trust)}finally{HttpsURLConnection.setDefaultSSLSocketFactory(previous);server.shutdown()}
 }
 @Test fun httpsSubscriptionUpdatesStageAndRejectFailures()=runBlocking {
  val repo=ClashRepository.get(context);repo.initialized();val before=repo.backup()
  try{tls{server,_->
   val address=server.url("/subscription?token=fixture-only").toString()
   server.enqueue(MockResponse().setBody(ClashRepository.SAMPLE).setHeader("Subscription-Userinfo","upload=10; download=20; total=1000; expire=2000000000"))
   val yaml=repo.download(address);assertEquals(ClashRepository.SAMPLE,yaml)
   assertEquals("mihomo",server.takeRequest(2,TimeUnit.SECONDS)!!.getHeader("User-Agent"))
   assertEquals(1000,JSONObject(repo.draft("subscription-info.${address.hashCode()}")).getLong("total"))
   repo.saveProfile("HTTPS fixture",yaml,source=address);val id=repo.state.value.selectedId
   val next=yaml+"\n# updated via TLS"
   server.enqueue(MockResponse().setBody(next));assertTrue(repo.updateSubscriptions().contains("已保存 1"));assertEquals(next,repo.state.value.selected!!.yaml)
   repo.phase(VpnPhase.RUNNING,id)
   server.enqueue(MockResponse().setBody(yaml+"\n# staged"));assertTrue(repo.updateSubscriptions().contains("待应用 1"))
   assertEquals(next,repo.state.value.selected!!.yaml);assertTrue(repo.draft("pending-update.$id").contains("# staged"))
   repo.phase(VpnPhase.STOPPED)
   server.enqueue(MockResponse().setBody("invalid: ["));assertTrue(repo.updateSubscriptions().contains("失败 1"));assertEquals(next,repo.state.value.selected!!.yaml)
   server.enqueue(MockResponse().setResponseCode(401));assertTrue(repo.updateSubscriptions().contains("失败 1"));assertEquals(next,repo.state.value.selected!!.yaml)
   server.enqueue(MockResponse().setResponseCode(403))
   val forbidden=runCatching{repo.download(address)}.exceptionOrNull()!!
   assertTrue(forbidden.message!!.contains("HTTP 403"));assertFalse(forbidden.message!!.contains("最终订阅地址"))
   server.enqueue(MockResponse().setResponseCode(302).setHeader("Location","/final"))
   server.enqueue(MockResponse().setBody(yaml))
   assertEquals(yaml,repo.download(address))
   server.enqueue(MockResponse().setBody("x".repeat(8*1024*1024+1)))
   assertTrue(runCatching{repo.download(address)}.isFailure)
   // Worker runs through its real entry point; scheduling timing remains OS-owned.
   repo.saveDraft("profile-interval.$id","15");repo.saveDraft("auto-last.$id","0")
   val changed=JSONObject(repo.backup());changed.getJSONObject("settings").put("auto-attempt.$id","0");changed.getJSONArray("profiles").let{a->for(i in 0 until a.length())a.getJSONObject(i).put("updated",0)}
   repo.restoreBackup(changed.toString());server.enqueue(MockResponse().setBody(yaml+"\n# worker"))
   val worker=TestListenableWorkerBuilder<SubscriptionWorker>(context).build()
   assertEquals(ListenableWorker.Result.success(),worker.doWork());assertTrue(repo.state.value.selected!!.yaml.contains("# worker"))
   assertTrue(repo.draft("automation-last").contains("已保存 1"))
   repo.deleteProfile(id);assertEquals("",repo.draft("profile-interval.$id"))
   assertTrue(runCatching{repo.saveProfile("Stale update",yaml,id,address)}.isFailure)
  }}finally{repo.phase(VpnPhase.STOPPED);repo.restoreBackup(before)}
 }
 @Test fun subscriptionRedirectsFollowHttpsAndRejectInvalidChains()=runBlocking {
  val repo=ClashRepository.get(context);repo.initialized();val before=repo.backup()
  try{tls{server,_->
   val address=server.url("/start?token=fixture-only").toString()
   val locations=listOf("/step1", "step2", "?step=3", server.url("/step4").toString(), "//${server.hostName}:${server.port}/final")
   listOf(301,302,303,307,308).zip(locations).forEach{(status,location)->
    server.enqueue(MockResponse().setResponseCode(status).setHeader("Location",location))
   }
   server.enqueue(MockResponse().setBody(ClashRepository.SAMPLE).setHeader("Subscription-Userinfo","total=1234"))
   assertEquals(ClashRepository.SAMPLE,repo.download(address))
   assertEquals(1234,JSONObject(repo.draft("subscription-info.${address.hashCode()}")).getLong("total"))
   val paths=listOf("/start?token=fixture-only","/step1","/step2","/step2?step=3","/step4","/final")
   paths.forEach{path->
    val request=server.takeRequest(2,TimeUnit.SECONDS)!!
    assertEquals(path,request.path);assertEquals("mihomo",request.getHeader("User-Agent"))
   }
   suspend fun rejected(response:MockResponse,text:String) {
    val requests=server.requestCount
    server.enqueue(response)
    val error=runCatching{repo.download(address)}.exceptionOrNull()!!
    assertTrue(error.message.orEmpty(),error.message.orEmpty().contains(text))
    assertFalse(error.message.orEmpty().contains("fixture-only"))
    assertEquals(requests+1,server.requestCount)
   }
   rejected(MockResponse().setResponseCode(302),"缺少目标地址")
   rejected(MockResponse().setResponseCode(302).setHeader("Location","http://localhost/unsafe"),"HTTPS")
   rejected(MockResponse().setResponseCode(302).setHeader("Location","https://[invalid"),"地址无效")
   rejected(MockResponse().setResponseCode(302).setHeader("Location",address+"#fragment"),"循环")
   val requests=server.requestCount
   repeat(6){server.enqueue(MockResponse().setResponseCode(302).setHeader("Location","/limit/$it"))}
   assertTrue(runCatching{repo.download(address)}.exceptionOrNull()!!.message!!.contains("超过上限"))
   assertEquals(requests+6,server.requestCount)
   server.enqueue(MockResponse().setResponseCode(302).setHeader("Location","/denied"))
   server.enqueue(MockResponse().setResponseCode(403))
   assertTrue(runCatching{repo.download(address)}.exceptionOrNull()!!.message!!.contains("HTTP 403"))
   assertEquals(1234,JSONObject(repo.draft("subscription-info.${address.hashCode()}")).getLong("total"))
  }}finally{repo.restoreBackup(before)}
 }
 @Test fun subscriptionIdentityPersistsAndErrorsStayPrivate()=runBlocking {
  val repo=ClashRepository.get(context);repo.initialized();val before=repo.backup()
  try{tls{server,_->
   val address=server.url("/identity?token=do-not-expose").toString()
   val agent="Clash.Meta" // A previously saved default is now a preserved custom identity.
   server.enqueue(MockResponse().setResponseCode(302).setHeader("Location","/final?token=do-not-expose"))
   server.enqueue(MockResponse().setBody(ClashRepository.SAMPLE))
   val yaml=repo.download(address,agent)
   repeat(2){assertEquals(agent,server.takeRequest(2,TimeUnit.SECONDS)!!.getHeader("User-Agent"))}
   repo.saveProfile("Identity fixture",yaml,source=address,userAgent=agent)
   val id=repo.state.value.selectedId
   assertEquals(agent,ProfileStore(context).load().first{it.id==id}.userAgent)
   repo.saveProfile("Identity fixture edited",yaml,id,address)
   assertEquals(agent,repo.state.value.selected!!.userAgent)
   val saved=repo.backup(false);repo.restoreBackup(saved)
   assertEquals(agent,repo.state.value.selected!!.userAgent)
   server.enqueue(MockResponse().setBody(yaml+"\n# identity update"))
   repo.updateSubscriptions();assertEquals(agent,server.takeRequest(2,TimeUnit.SECONDS)!!.getHeader("User-Agent"))
   assertEquals(agent,repo.state.value.selected!!.userAgent)
   // The real background worker must use the same persisted identity.
   repo.saveDraft("automation-enabled","true");repo.saveDraft("profile-interval.$id","15")
   val due=JSONObject(repo.backup());due.getJSONArray("profiles").let{items->for(i in 0 until items.length())items.getJSONObject(i).put("updated",0)}
   due.getJSONObject("settings").put("auto-last.$id","0").put("auto-attempt.$id","0")
   repo.restoreBackup(due.toString())
   server.enqueue(MockResponse().setBody(yaml))
   TestListenableWorkerBuilder<SubscriptionWorker>(context).build().doWork()
   assertEquals(agent,server.takeRequest(2,TimeUnit.SECONDS)!!.getHeader("User-Agent"))
   server.enqueue(MockResponse().setResponseCode(302).setHeader("Location","/denied?token=do-not-expose"))
   server.enqueue(MockResponse().setResponseCode(403).setBody("User-Agent forbidden token=do-not-expose https://private.example/secret Cookie: hidden-cookie"))
   val error=runCatching{repo.download(address,agent)}.exceptionOrNull()!!
   assertTrue(error.message!!.contains("HTTP 403"));assertTrue(error.message!!.contains("重定向：1次"));assertTrue(error.message!!.contains("客户端身份受限"))
   for(text in listOf(error.message!!,repo.draft("subscription-error.${address.hashCode()}"),repo.diagnostics())) {
    for(secret in listOf("do-not-expose","private.example","hidden-cookie",agent))assertFalse(text,text.contains(secret))
   }
   repeat(2){server.takeRequest(2,TimeUnit.SECONDS)}
   server.enqueue(MockResponse().setBody(yaml));repo.download(address,agent)
   assertEquals("",repo.draft("subscription-error.${address.hashCode()}"))
   server.takeRequest(2,TimeUnit.SECONDS)
   // Backups without an identity use the current shared default; explicit values stay intact.
   val legacy=JSONObject(saved);legacy.getJSONArray("profiles").let{items->for(i in 0 until items.length())items.getJSONObject(i).remove("userAgent")}
   repo.restoreBackup(legacy.toString());assertEquals(DEFAULT_SUBSCRIPTION_UA,repo.state.value.selected!!.userAgent)
  }}finally{repo.phase(VpnPhase.STOPPED);repo.restoreBackup(before)}
 }
 @Test fun manualDiagnosisComparesIdentitiesWithoutSavingOrRetrying()=runBlocking {
  val repo=ClashRepository.get(context);repo.initialized();val before=repo.backup()
  tls{server,_->
   val address=server.url("/diagnose?token=diagnosis-secret").toString()
   server.enqueue(MockResponse().setResponseCode(403).setHeadersDelay(120,TimeUnit.MILLISECONDS).setBody("token=diagnosis-secret"))
   server.enqueue(MockResponse().setResponseCode(302).setHeader("Location","/cfa"))
   server.enqueue(MockResponse().setResponseCode(200).setBody("not YAML; diagnostic must not save it"))
   val results=mutableListOf<SubscriptionProbeResult>()
   diagnoseSubscription(address,CFA_SUBSCRIPTION_UA){results.add(it)}
   assertEquals(listOf(403,200),results.map{it.status})
   assertEquals(listOf(0,1),results.map{it.redirects})
   assertTrue(results.first().elapsedMillis>=100)
   assertFalse(results.first().successful);assertTrue(results[1].successful)
   val agents=listOf(CFA_SUBSCRIPTION_UA,DEFAULT_SUBSCRIPTION_UA,DEFAULT_SUBSCRIPTION_UA)
   agents.forEach{assertEquals(it,server.takeRequest(2,TimeUnit.SECONDS)!!.getHeader("User-Agent"))}
   assertFalse(results.toString().contains("diagnosis-secret"))
   assertEquals(before,repo.backup())
   server.enqueue(MockResponse().setResponseCode(200))
   val deduplicated=mutableListOf<SubscriptionProbeResult>()
   diagnoseSubscription(address,DEFAULT_SUBSCRIPTION_UA){deduplicated.add(it)}
   assertEquals(1,deduplicated.size);assertEquals("当前 / 默认",deduplicated.first().label)
   server.takeRequest(2,TimeUnit.SECONDS)
   val count=server.requestCount
   assertTrue(runCatching{diagnoseSubscription("http://localhost/?token=secret",DEFAULT_SUBSCRIPTION_UA){}}.isFailure)
   assertTrue(runCatching{diagnoseSubscription(address,"bad\nUA"){}}.isFailure)
   assertEquals(count,server.requestCount)
  }
 }
 @Test fun manualDiagnosisCancellationStopsRemainingRequests()=runBlocking {
  tls{server,_->
   server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
   val results=mutableListOf<SubscriptionProbeResult>()
   val task=launch{diagnoseSubscription(server.url("/slow").toString(),DEFAULT_SUBSCRIPTION_UA){results.add(it)}}
   withContext(Dispatchers.IO){assertNotNull(server.takeRequest(2,TimeUnit.SECONDS))}
   task.cancel();withTimeout(2000){task.join()}
   assertTrue(results.isEmpty());assertEquals(1,server.requestCount)
  }
 }
 @Test fun manualDiagnosisRejectsUnsafeRedirectAndContinuesOtherIdentities()=runBlocking {
  tls{server,_->
   server.enqueue(MockResponse().setResponseCode(302).setHeader("Location","http://localhost/private?token=hidden"))
   server.enqueue(MockResponse().setResponseCode(429))
   val results=mutableListOf<SubscriptionProbeResult>()
   diagnoseSubscription(server.url("/safe").toString(),CFA_SUBSCRIPTION_UA){results.add(it)}
   assertNull(results.first().status);assertTrue(results.first().error!!.contains("HTTPS"))
   assertFalse(results.toString().contains("hidden"));assertEquals(listOf(null,429),results.map{it.status})
   assertEquals(2,server.requestCount)
  }
 }
 @Test fun remoteHttpsMutationsAuthenticationAndWebsocket()=runBlocking {
  tls{server,trust->
   val remote=RemoteInstance("test","fixture",server.url("/controller").toString(),"fixture-token")
   server.enqueue(MockResponse().setBody("{\"version\":\"fixture\"}"))
   assertEquals("fixture",remoteApi(remote,"/version").getString("version"))
   val request=server.takeRequest(2,TimeUnit.SECONDS)!!;assertEquals("/controller/version",request.path);assertEquals("Bearer fixture-token",request.getHeader("Authorization"))
   for((path,method,data) in listOf(Triple("/configs","PATCH",JSONObject().put("mode","direct")),Triple("/proxies/a%20b","PUT",JSONObject().put("name","DIRECT")),Triple("/rules/disable","PATCH",JSONObject().put("0",true)),Triple("/connections/fixture","DELETE",null))) {
    server.enqueue(MockResponse().setResponseCode(204));remoteApi(remote,path,method,data)
    val r=server.takeRequest(2,TimeUnit.SECONDS)!!;assertEquals(method,r.method);assertEquals("/controller$path",r.path);if(data!=null)assertEquals(data.toString(),r.body.readUtf8())
   }
   for(status in listOf(401,404,500,302)) { server.enqueue(MockResponse().setResponseCode(status).setHeader("Location",server.url("/redirect")));assertTrue(runCatching{remoteApi(remote,"/version")}.isFailure);server.takeRequest() }
   assertTrue(runCatching{remoteApi(remote.copy(address="http://localhost"),"/version")}.isFailure)
   server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener(){override fun onOpen(socket:WebSocket,response:Response){socket.send("{\"payload\":\"GET https://example.test/?token=hidden password=hidden uuid=hidden\"}")}}))
   val client=OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).followRedirects(false).build()
   val line=withTimeout(5000){remoteLogs(remote,client).first()}
   assertFalse(line.contains("hidden"));assertFalse(line.contains("example.test"))
   assertEquals("/controller/logs?level=info",server.takeRequest(2,TimeUnit.SECONDS)!!.path)
   assertTrue(runCatching{withTimeout(2000){remoteLogs(remote.copy(address="http://localhost")).first()}}.isFailure)
  }
 }
 @Test fun backupRejectsBrokenSettingsAndPreservesExcludedSettings()=runBlocking {
  val repo=ClashRepository.get(context);repo.initialized();val before=repo.backup()
  try{
   repo.saveProfile("Backup fixture",ClashRepository.SAMPLE)
   repo.theme("dark");repo.language("en");repo.fontScale(1.3f);repo.saveDraft("automation-minutes","60")
   val limited=repo.backup(false)
   repo.theme("light");repo.language("zh");repo.fontScale(1f);repo.saveDraft("automation-minutes","90")
   repo.restoreBackup(limited)
   assertEquals("light",repo.state.value.theme);assertEquals("zh",repo.state.value.language);assertEquals("90",repo.draft("automation-minutes"))
   for((key,value) in listOf("remote-instances" to "[bad", "vpn-options" to "{\"mtu\":10}","automation-minutes" to "-1")) {
    val invalid=JSONObject(repo.backup());invalid.getJSONObject("settings").put(key,value)
    val expected=repo.backup();assertTrue(runCatching{repo.restoreBackup(invalid.toString())}.isFailure);assertEquals(expected,repo.backup())
   }
  }finally{repo.restoreBackup(before)}
 }
}
