package cn.nascp.clash

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.update.AppUpdateRepository
import cn.nascp.clash.update.AppUpdateViewModel
import cn.nascp.clash.update.UPDATE_REPOSITORY
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AppUpdateBehaviorTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun withVm(client: OkHttpClient, test: (AppUpdateViewModel) -> Unit) {
        val app = instrumentation.targetContext.applicationContext as Application
        val store = ViewModelStore()
        lateinit var vm: AppUpdateViewModel
        instrumentation.runOnMainSync {
            vm = AppUpdateViewModel(app, AppUpdateRepository(app, client))
            store.put("updates", vm)
        }
        try { test(vm) } finally { instrumentation.runOnMainSync { store.clear() } }
    }
    private fun idle(vm: AppUpdateViewModel) {
        val deadline = SystemClock.uptimeMillis() + 6000
        while (vm.state.value.busy && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(10)
        assertFalse("Update operation did not finish", vm.state.value.busy)
    }
    private fun client(code: Int, body: String) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
            .body(body.toResponseBody()).build()
    }.build()

    @Test fun automaticAndManualCheckFailuresLeaveNoErrorMessage() {
        for ((code, body) in listOf(403 to "[]", 500 to "[]", 200 to "invalid-json")) {
            withVm(client(code, body)) { vm ->
                for (automatic in listOf(true, false)) {
                    instrumentation.runOnMainSync { vm.check(automatic) }
                    idle(vm)
                    assertEquals("HTTP $code, automatic=$automatic", "", vm.state.value.message)
                    assertNull(vm.state.value.release)
                }
            }
        }
    }

    @Test fun automaticNoUpdateIsSilentAndManualCheckStillReportsResult() {
        withVm(client(200, "[]")) { vm ->
            instrumentation.runOnMainSync { vm.check(automatic = true) }
            idle(vm)
            assertEquals("", vm.state.value.message)
            instrumentation.runOnMainSync { vm.check() }
            idle(vm)
            assertTrue(vm.state.value.message.contains("暂无可用更新"))
        }
    }

    @Test fun automaticCheckIsSilentWhileRunningAndAnotherEntryCanCheckAgain() {
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet(); started.countDown()
            check(finish.await(5, TimeUnit.SECONDS))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("[]".toResponseBody()).build()
        }.build()
        try {
            withVm(client) { vm ->
                instrumentation.runOnMainSync { vm.check(automatic = true) }
                assertTrue(started.await(3, TimeUnit.SECONDS))
                assertTrue(vm.state.value.automaticCheck)
                assertEquals("", vm.state.value.message)
                instrumentation.runOnMainSync { vm.check(automatic = true) }
                assertEquals(1, requests.get())
                finish.countDown(); idle(vm)
                instrumentation.runOnMainSync { vm.check(automatic = true) }
                idle(vm)
                assertEquals(2, requests.get())
                assertEquals("", vm.state.value.message)
            }
        } finally { finish.countDown() }
    }

    @Test fun discoveredReleaseAndDownloadFailuresRemainVisible() {
        val release = JSONObject().put("tag_name", "v99.0.0").put("prerelease", false).put("draft", false)
            .put("assets", JSONArray().put(JSONObject().put("name", "Clash-arm64.apk").put("state", "uploaded")
                .put("size", 3).put("digest", "sha256:" + "0".repeat(64))
                .put("browser_download_url", "$UPDATE_REPOSITORY/releases/download/v99.0.0/Clash-arm64.apk")))
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val body = if (chain.request().url.host == "api.github.com") JSONArray().put(release).toString() else "bad"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(body.toResponseBody()).build()
        }.build()
        withVm(client) { vm ->
            instrumentation.runOnMainSync { vm.check(automatic = true) }
            idle(vm)
            assertEquals("v99.0.0", vm.state.value.release?.tag)
            assertEquals("发现新版本", vm.state.value.message)
            instrumentation.runOnMainSync { vm.download() }
            idle(vm)
            assertTrue(vm.state.value.message.contains("SHA-256 校验失败"))
        }
    }
}
