package cn.nascp.clash

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.data.*
import cn.nascp.clash.vpn.ClashVpnService
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.net.*
import java.io.File

class ConnectionOwnerIntegrationTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun tcpUdpAndFakeIpConnectionsCarryActualAppUidAndIcon()=runBlocking {
        assumeTrue("Run connection-owner-fixture.py and pass ownerFixture=true",InstrumentationRegistry.getArguments().getString("ownerFixture")=="true")
        val repo=ClashRepository.get(ui.activity);repo.initialized();val backup=repo.backup()
        val uid=android.os.Process.myUid()
        suspend fun stop() {if(repo.state.value.phase==VpnPhase.STOPPED)return;ui.activity.startService(Intent(ui.activity,ClashVpnService::class.java).setAction(ClashVpnService.STOP));withTimeout(10000){while(repo.state.value.phase!=VpnPhase.STOPPED)delay(50)}}
        suspend fun find(port:Int):JSONObject=withTimeout(10000) {
            while(true) {
                val all=(repo.api("/connections").optJSONArray("connections")?:org.json.JSONArray())
                for(i in 0 until all.length()) {
                    val c=all.getJSONObject(i);if(c.getJSONObject("metadata").optInt("sourcePort")==port)return@withTimeout c
                }
                delay(100)
            }
            error("unreachable")
        }
        try {
            repo.language("zh");repo.theme("light");repo.fontScale(1f)
            repo.saveDraft("vpn-options","{}");repo.saveDraft("app-mode","全部应用")
            ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("appops set ${ui.activity.packageName} ACTIVATE_VPN allow")).use{it.readBytes()}
            for(fake in listOf(false,true)) {
                val domain="owner-${System.nanoTime()}.fixture.test"
                repo.saveProfile("Connection owner fixture","proxies: []\nproxy-groups: []\nrules: ['MATCH,DIRECT']\nhosts: { '$domain': 10.0.2.2 }\ndns:\n  enhanced-mode: ${if(fake)"fake-ip" else "redir-host"}\n  use-hosts: false\n  fake-ip-filter: []\n  nameserver: ['udp://10.0.2.2:18088']\n")
                ContextCompat.startForegroundService(ui.activity,Intent(ui.activity,ClashVpnService::class.java))
                withTimeout(15000){while(repo.state.value.phase !in listOf(VpnPhase.RUNNING,VpnPhase.FAILED))delay(50)}
                assertEquals(repo.state.value.error,VpnPhase.RUNNING,repo.state.value.phase)
                withContext(Dispatchers.IO) {
                    val address=if(fake)InetAddress.getByName(domain)else InetAddress.getByName("10.0.2.2")
                    if(fake)assertTrue("Fixture must exercise Fake IP rewriting: $address",address.hostAddress!!.startsWith("198.18."))
                    Socket().use { socket ->
                        socket.soTimeout=5000;socket.connect(InetSocketAddress(address,18086),5000)
                        socket.getOutputStream().write("owner-tcp\n".toByteArray());assertEquals("owner-tcp",socket.getInputStream().bufferedReader().readLine())
                        val metadata=find(socket.localPort).getJSONObject("metadata")
                        assertEquals("TCP owner (fake=$fake)",uid,metadata.getInt("uid"))
                        if(fake)assertEquals(domain,metadata.getString("host"))
                        DatagramSocket().use { udp ->
                            udp.soTimeout=5000;val bytes="owner-udp".toByteArray();udp.send(DatagramPacket(bytes,bytes.size,address,18087))
                            val answer=DatagramPacket(ByteArray(128),128);udp.receive(answer)
                            val udpMetadata=find(udp.localPort).getJSONObject("metadata")
                            assertEquals("UDP owner (fake=$fake)",uid,udpMetadata.getInt("uid"))
                            if(fake)assertEquals(domain,udpMetadata.getString("host"))
                        }
                        if(!fake) {
                            val script=File(ui.activity.getExternalFilesDir(null),"connection-owner-shell.sh")
                            script.writeText("(printf shell-owner; sleep 15) | toybox nc -w 15 10.0.2.2 18086\n")
                            val shell=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("sh ${script.absolutePath}")
                            try {
                                withTimeout(10000) {
                                    while(true) {
                                        val all=(repo.api("/connections").optJSONArray("connections")?:org.json.JSONArray())
                                        val found=(0 until all.length()).map(all::getJSONObject).any {
                                            val m=it.getJSONObject("metadata");m.optInt("uid")==2000 && m.optInt("destinationPort")==18086
                                        }
                                        if(found)break
                                        delay(100)
                                    }
                                }
                            } finally {shell.close();script.delete()}
                        }
                        ui.onAllNodesWithText("活动").onLast().performClick()
                        ui.waitUntil(10000){ui.onAllNodesWithTag("connection-app-icon:${ui.activity.packageName}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
                        ui.onAllNodesWithText("Clash Dev").onFirst().assertIsDisplayed()
                        val screenshot=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                        val file=File(ui.activity.getExternalFilesDir(null),"connection-owner-${if(fake)"fake" else "direct"}.png")
                        file.outputStream().use{screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};screenshot.recycle()
                    }
                }
                stop()
            }
        } finally {stop();repo.clearMessage();repo.restoreBackup(backup)}
    }
}
