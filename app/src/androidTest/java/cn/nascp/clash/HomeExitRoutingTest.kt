package cn.nascp.clash

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.data.*
import cn.nascp.clash.vpn.ClashVpnService
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

class HomeExitRoutingTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun nodeModeAndDisconnectInvalidateExitAndSlowQueryDoesNotBlockStop() {
        val repo=ClashRepository.get(ui.activity)
        runBlocking{repo.initialized()};val backup=repo.backup();val previous=repo.exitLocationFetcher
        val calls=AtomicInteger();val cancelled=AtomicInteger();val blocked=AtomicInteger();val slow=AtomicBoolean(false)
        try {
            repo.exitLocationFetcher={
                calls.incrementAndGet()
                if(slow.get()) { blocked.incrementAndGet();try { delay(25_000) } catch(e:CancellationException){cancelled.incrementAndGet();throw e} }
                val state=repo.state.value
                val suffix=if(state.mode=="direct")3 else if(state.proxies.optJSONObject("Manual")?.optString("now")=="B")2 else 1
                ExitLocation("203.0.113.$suffix","Fixture","SG",timezone="Asia/Singapore")
            }
            repo.theme("light");repo.language("zh");repo.clearMessage()
            runBlocking{repo.saveProfile("Exit routing fixture","proxies:\n - {name: A, type: direct}\n - {name: B, type: direct}\nproxy-groups:\n - {name: Manual, type: select, proxies: [A,B]}\nrules: ['MATCH,Manual']\n")}
            ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("appops set ${ui.activity.packageName} ACTIVATE_VPN allow")).use{it.readBytes()}
            ContextCompat.startForegroundService(ui.activity,Intent(ui.activity,ClashVpnService::class.java))
            ui.waitUntil(15000){repo.state.value.phase in listOf(VpnPhase.RUNNING,VpnPhase.FAILED)}
            assertEquals(repo.state.value.error,VpnPhase.RUNNING,repo.state.value.phase)
            ui.waitUntil(10000){repo.state.value.exitLocation?.ip=="203.0.113.1"}
            runBlocking{repo.selectProxy("Manual","B")}
            ui.waitUntil(10000){repo.state.value.exitLocation?.ip=="203.0.113.2"}
            runBlocking{repo.mode("direct")}
            ui.waitUntil(10000){repo.state.value.exitLocation?.ip=="203.0.113.3"}
            slow.set(true);repo.refreshExitLocation()
            ui.waitUntil(10000){blocked.get()>0 && repo.state.value.exitLoading}
            assertNull(repo.state.value.exitLocation)
            ui.onAllNodesWithText("代理").onLast().performClick()
            ui.waitUntil(5000){!repo.state.value.exitLoading && cancelled.get()>0}
            ui.onAllNodesWithText("首页").onLast().performClick()
            ui.waitUntil(10000){blocked.get()>=2 && repo.state.value.exitLoading}
            val started=System.nanoTime()
            ui.activity.startService(Intent(ui.activity,ClashVpnService::class.java).setAction(ClashVpnService.STOP))
            ui.waitUntil(5000){repo.state.value.phase==VpnPhase.STOPPED && !repo.state.value.exitLoading}
            assertTrue((System.nanoTime()-started)/1_000_000<5000)
            assertNull(repo.state.value.exitLocation)
            ui.waitUntil(5000){cancelled.get()>0}
        } finally {
            ui.activity.startService(Intent(ui.activity,ClashVpnService::class.java).setAction(ClashVpnService.STOP))
            ui.waitUntil(15000){repo.state.value.phase==VpnPhase.STOPPED}
            repo.exitLocationFetcher=previous;runBlocking{repo.restoreBackup(backup)}
        }
    }
}
