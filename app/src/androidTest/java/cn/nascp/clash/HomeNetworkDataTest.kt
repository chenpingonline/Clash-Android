package cn.nascp.clash

import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.data.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

class HomeNetworkDataTest {
    @Test fun trafficTotalsSurviveReloadCoreResetAndNewProcessWithoutDoubleCounting() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="traffic-totals-qa-${System.nanoTime()}"
        try {
            val first=TrafficTotalsStore(context,"process-one",name)
            assertEquals(TrafficTotals(100,200),first.observe(100,200))
            assertEquals(TrafficTotals(125,250),first.observe(125,250))
            first.checkpoint()
            val same=TrafficTotalsStore(context,"process-one",name)
            assertEquals(TrafficTotals(125,250),same.observe(125,250))
            assertEquals(TrafficTotals(130,260),same.observe(5,10))
            same.checkpoint()
            val next=TrafficTotalsStore(context,"process-two",name)
            // A fresh core can surpass its predecessor before the first sample.
            assertEquals(TrafficTotals(230,460),next.observe(100,200))
            next.checkpoint()
            assertEquals(TrafficTotals(230,460),TrafficTotalsStore(context,"process-two",name).current())
        } finally { context.deleteSharedPreferences(name) }
    }
    @Test fun exitSchemasValidateLiteralIPAndNormalizeLocation() {
        val ipwho=parseExitLocation("""{"success":true,"ip":"203.0.113.1","country":"新加坡","country_code":"sg","region":"Region","city":"City","timezone":{"id":"Asia/Singapore","utc":"+08:00"}}""",12)
        assertEquals("SG",ipwho.countryCode);assertEquals("Asia/Singapore",ipwho.timezone);assertEquals("+08:00",ipwho.utcOffset)
        val nested=parseExitLocation("""{"ip":"2001:db8::1","location":{"country":"Japan","country_code":"jp","state":"Tokyo","city":"City","timezone":"Asia/Tokyo"}}""",3)
        assertEquals("Tokyo",nested.region);assertEquals("JP",nested.countryCode)
        val countryName=parseExitLocation("""{"ip":"203.0.113.2","country":"US","country_name":"United States","utc_offset":"-0500"}""",3)
        assertEquals("United States",countryName.country);assertEquals("US",countryName.countryCode)
        for(text in listOf("{\"ip\":\"private.example\"}","{\"ip\":\"999.1.1.1\"}","{\"success\":false,\"ip\":\"203.0.113.2\"}"))assertTrue(runCatching{parseExitLocation(text,0)}.isFailure)
    }
    private suspend fun tls(block:suspend(MockWebServer)->Unit) {
        val certificate=HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val server=MockWebServer();server.useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(),false);server.start()
        val previous=HttpsURLConnection.getDefaultSSLSocketFactory()
        HttpsURLConnection.setDefaultSSLSocketFactory(HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build().sslSocketFactory())
        try{block(server)}finally{HttpsURLConnection.setDefaultSSLSocketFactory(previous);server.shutdown()}
    }
    @Test fun exitQueriesFailOverBoundResponsesAndNeverExposeServerErrors()=runBlocking {
        tls { server ->
            server.enqueue(MockResponse().setResponseCode(403).setBody("secret=do-not-display"))
            server.enqueue(MockResponse().setBody("x".repeat(128*1024+1)))
            server.enqueue(MockResponse().setBody("{\"ip\":\"203.0.113.8\",\"country\":\"SG\"}"))
            val endpoints=listOf("/blocked","/oversized","/valid").map{server.url(it).toString()}
            assertEquals("203.0.113.8",queryExitLocation(endpoints).ip)
            assertEquals(3,server.requestCount)
            repeat(3){val request=server.takeRequest(2,TimeUnit.SECONDS)!!;assertEquals("application/json",request.getHeader("Accept"));assertEquals("close",request.getHeader("Connection"));assertEquals("no-cache",request.getHeader("Cache-Control"))}
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location","http://localhost/secret"))
            val failure=runCatching{queryExitLocation(listOf(server.url("/redirect").toString()))}.exceptionOrNull()!!
            assertEquals("出口信息更新失败，请重试",failure.message)
            assertEquals(4,server.requestCount)
        }
    }
    @Test fun cancellingExitProbeStopsRemainingServices()=runBlocking {
        tls { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val request=launch{queryExitLocation(listOf(server.url("/slow").toString(),server.url("/must-not-run").toString()))}
            withContext(Dispatchers.IO){assertNotNull(server.takeRequest(3,TimeUnit.SECONDS))}
            request.cancel();withTimeout(2000){request.join()}
            assertEquals(1,server.requestCount)
        }
    }
}
