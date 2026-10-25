package cn.nascp.clash

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import cn.nascp.clash.data.*
import cn.nascp.clash.system.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

@RunWith(AndroidJUnit4::class)
class SubscriptionProtectionTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun fixture(block:suspend(ClashRepository,MockWebServer)->Unit) {
        val repo=ClashRepository.get(context);repo.initialized();val before=repo.backup()
        val certificate=HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server=MockWebServer()
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(),false)
        val previous=HttpsURLConnection.getDefaultSSLSocketFactory()
        server.start();HttpsURLConnection.setDefaultSSLSocketFactory(trust.sslSocketFactory())
        try {
            repo.restoreBackup(JSONObject(before).put("profiles",JSONArray()).put("selected","").toString())
            block(repo,server)
        } finally {
            repo.phase(VpnPhase.STOPPED);repo.restoreBackup(before)
            HttpsURLConnection.setDefaultSSLSocketFactory(previous);server.shutdown()
        }
    }
    @Test fun productionDeadlineIncludesRedirectsAndTricklingBodyAndPreservesProfile()=runBlocking {
        fixture { repo,server ->
            val address=server.url("/deadline?token=hidden-deadline").toString()
            repo.saveProfile("Deadline fixture",ClashRepository.SAMPLE,source=address)
            val original=repo.state.value.selected!!
            val history=repo.history(original.id).toString()
            repo.saveDraft("pending-update.${original.id}","previous staged content")
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location","/second").setHeadersDelay(12,TimeUnit.SECONDS))
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location","/slow-body").setHeadersDelay(12,TimeUnit.SECONDS))
            // Every byte arrives within the 20-second read timeout; only the overall deadline stops it.
            server.enqueue(MockResponse().setBody(ClashRepository.SAMPLE).throttleBody(1,5,TimeUnit.SECONDS))
            val start=System.nanoTime()
            assertTrue(repo.updateSubscriptions().contains("失败 1"))
            val elapsed=(System.nanoTime()-start)/1_000_000L
            assertTrue("elapsed=$elapsed",elapsed in 59_000..65_000)
            assertEquals(3,server.requestCount)
            assertEquals(original,repo.state.value.selected)
            assertEquals(original,ProfileStore(context).load().single())
            assertEquals(history,repo.history(original.id).toString())
            assertEquals("previous staged content",repo.draft("pending-update.${original.id}"))
            assertEquals("",repo.draft("auto-last.${original.id}"))
            assertEquals("",repo.draft("subscription-info.${address.hashCode()}"))
            val diagnostic=repo.draft("subscription-error.${address.hashCode()}")
            assertTrue(diagnostic.contains("整体上限60秒"));assertFalse(diagnostic.contains("hidden-deadline"))
        }
    }
    @Test fun forbiddenWorkerDoesNotRetryOrOverwriteAndHonorsUpdateInterval()=runBlocking {
        fixture { repo,server ->
            val address=server.url("/forbidden").toString()
            repo.saveProfile("Forbidden fixture",ClashRepository.SAMPLE,source=address)
            val id=repo.state.value.selectedId
            val due=JSONObject(repo.backup());due.getJSONArray("profiles").getJSONObject(0).put("updated",0)
            due.getJSONObject("settings").put("profile-interval.$id","720").put("auto-last.$id","0").put("auto-attempt.$id","0")
            repo.restoreBackup(due.toString());val original=repo.state.value.selected!!
            server.enqueue(MockResponse().setResponseCode(403).setBody("User-Agent forbidden token=secret"))
            val worker=TestListenableWorkerBuilder<SubscriptionWorker>(context).build()
            assertEquals(ListenableWorker.Result.success(),worker.doWork())
            assertEquals(1,server.requestCount);assertEquals(original,repo.state.value.selected)
            assertTrue(repo.draft("automation-last").contains("失败 1"))
            assertEquals(ListenableWorker.Result.success(),TestListenableWorkerBuilder<SubscriptionWorker>(context).build().doWork())
            assertEquals(1,server.requestCount)
            // An explicit user update is still allowed.
            server.enqueue(MockResponse().setBody(ClashRepository.SAMPLE+"\n# recovered"))
            assertTrue(repo.updateSubscriptions().contains("已保存 1"));assertEquals(2,server.requestCount)
            assertTrue(repo.state.value.selected!!.yaml.contains("# recovered"))
        }
    }
    @Test fun transientFailuresRetryOnceButAuthValidationAndStagedFailuresKeepOriginal()=runBlocking {
        fixture { repo,server ->
            val address=server.url("/retry").toString()
            repo.saveProfile("Retry fixture",ClashRepository.SAMPLE,source=address)
            val id=repo.state.value.selectedId
            server.enqueue(MockResponse().setResponseCode(503));server.enqueue(MockResponse().setBody(ClashRepository.SAMPLE+"\n# success"))
            assertTrue(repo.updateSubscriptions().contains("已保存 1"));assertEquals(2,server.requestCount)
            val original=repo.state.value.selected!!;val history=repo.history(id).toString();val last=repo.draft("auto-last.$id")
            server.enqueue(MockResponse().setResponseCode(502));server.enqueue(MockResponse().setResponseCode(504))
            assertTrue(repo.updateSubscriptions().contains("失败 1"));assertEquals(4,server.requestCount)
            for(status in listOf(401,403,404,429)) {
                val count=server.requestCount;server.enqueue(MockResponse().setResponseCode(status))
                assertTrue(repo.updateSubscriptions().contains("失败 1"));assertEquals(count+1,server.requestCount)
            }
            server.enqueue(MockResponse().setBody("invalid: ["))
            assertTrue(repo.updateSubscriptions().contains("失败 1"));assertEquals(original,repo.state.value.selected)
            assertEquals(history,repo.history(id).toString());assertEquals(last,repo.draft("auto-last.$id"))
            repo.saveDraft("pending-update.$id",ClashRepository.SAMPLE+"\n# earlier valid stage")
            repo.phase(VpnPhase.RUNNING,id)
            server.enqueue(MockResponse().setBody("invalid: ["))
            assertTrue(repo.updateSubscriptions().contains("失败 1"))
            assertTrue(repo.draft("pending-update.$id").endsWith("# earlier valid stage"))
            assertEquals(original,repo.state.value.selected)
        }
    }
    @Test fun truncatedResponsesNeverReplaceProfileOrExceedRetryLimit()=runBlocking {
        fixture { repo,server ->
            val address=server.url("/truncated").toString()
            repo.saveProfile("Truncated fixture",ClashRepository.SAMPLE,source=address)
            val original=repo.state.value.selected!!
            val history=repo.history(original.id).toString()
            repeat(2){server.enqueue(MockResponse().setBody(ClashRepository.SAMPLE).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))}
            assertTrue(repo.updateSubscriptions().contains("失败 1"))
            // Android may classify premature EOF as a non-retryable ProtocolException.
            assertTrue(server.requestCount in 1..2)
            assertEquals(original,repo.state.value.selected)
            assertEquals(original,ProfileStore(context).load().single())
            assertEquals(history,repo.history(original.id).toString())
            assertEquals("",repo.draft("subscription-info.${address.hashCode()}"))
        }
    }
    @Test fun gzipDownloadsValidateDecodedSizeWithoutMistakingCompressedLength()=runBlocking {
        fixture { repo,server ->
            fun gzip(text:String):okio.Buffer {
                val output=java.io.ByteArrayOutputStream()
                java.util.zip.GZIPOutputStream(output).use{it.write(text.toByteArray())}
                return okio.Buffer().write(output.toByteArray())
            }
            val address=server.url("/compressed").toString()
            server.enqueue(MockResponse().setHeader("Content-Encoding","gzip").setBody(gzip(ClashRepository.SAMPLE)))
            assertEquals(ClashRepository.SAMPLE,repo.download(address))
            server.enqueue(MockResponse().setHeader("Content-Encoding","gzip").setBody(gzip("x".repeat(8*1024*1024+1))))
            assertTrue(runCatching{repo.download(address)}.exceptionOrNull()!!.message!!.contains("8 MiB"))
            assertEquals(2,server.requestCount)
        }
    }
    @Test fun cancellingBodyReadDoesNotSaveMetadataOrStartAnotherAttempt()=runBlocking {
        fixture { repo,server ->
            val address=server.url("/cancel").toString()
            server.enqueue(MockResponse().setBody(ClashRepository.SAMPLE).throttleBody(1,5,TimeUnit.SECONDS))
            val download=launch { repo.download(address) }
            withContext(Dispatchers.IO){assertNotNull(server.takeRequest(2,TimeUnit.SECONDS))}
            delay(100)
            download.cancel();withTimeout(2000){download.join()}
            assertEquals(1,server.requestCount)
            assertEquals("",repo.draft("subscription-info.${address.hashCode()}"))
            assertEquals("",repo.draft("subscription-error.${address.hashCode()}"))
        }
    }
}
