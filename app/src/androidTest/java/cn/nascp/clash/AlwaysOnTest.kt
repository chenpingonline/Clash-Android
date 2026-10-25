package cn.nascp.clash

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.data.*
import cn.nascp.clash.vpn.ClashVpnService
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in persistent fixture for external OS reboot/lockdown testing on a dedicated emulator. */
class AlwaysOnTest {
 @Test fun preparePersistentFixture()=runBlocking {
  assumeTrue(InstrumentationRegistry.getArguments().getString("alwaysOnFixture")=="true")
  val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
  val repo=ClashRepository.get(c);repo.initialized()
  repo.saveDraft("vpn-options","{}");repo.saveDraft("app-mode","全部应用")
  repo.saveProfile("Always-on QA",ClashRepository.SAMPLE.replace("proxies: []","proxies:\n  - name: FixtureSocks\n    type: socks5\n    server: 10.0.2.2\n    port: 18082").replace("      - DIRECT","      - FixtureSocks\n      - DIRECT").replace("1.1.1.1","udp://10.0.2.2:18053").replace("    - 223.5.5.5\n","").replace("MATCH,默认策略","DST-PORT,18081,DIRECT\n  - MATCH,FixtureSocks"))
  assertTrue(c.getSharedPreferences("preferences",0).edit().commit())
  ParcelFileDescriptor.AutoCloseInputStream(i.uiAutomation.executeShellCommand("appops set ${c.packageName} ACTIVATE_VPN allow")).use{it.readBytes()}
  assertNull(android.net.VpnService.prepare(c))
  ContextCompat.startForegroundService(c,Intent(c,ClashVpnService::class.java))
  withTimeout(15000){while(repo.state.value.phase!=VpnPhase.RUNNING){check(repo.state.value.phase!=VpnPhase.FAILED){repo.state.value.error};delay(100)}}
  val response=withContext(Dispatchers.IO){java.net.Socket("10.0.2.2",18080).use{s->s.soTimeout=5000;s.getOutputStream().write("GET / HTTP/1.0\r\nHost: fixture\r\n\r\n".toByteArray());s.getInputStream().bufferedReader().readText()}}
  assertTrue(response.contains("CLASH_ANDROID_FIXTURE_OK"))
 }
}
