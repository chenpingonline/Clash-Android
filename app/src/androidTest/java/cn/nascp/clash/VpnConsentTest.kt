package cn.nascp.clash

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import cn.nascp.clash.data.*
import cn.nascp.clash.vpn.ClashVpnService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class VpnConsentTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
 private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use{it.readBytes()}
 @Test fun denyConsentThenGrantAndSystemDisconnectKeepsProfile(){
  val repo=ClashRepository.get(ui.activity);runBlocking{repo.initialized()};val before=repo.backup()
  val device=UiDevice.getInstance(instrumentation)
  try{
   repo.language("zh");repo.fontScale(1f);repo.clearMessage()
   runBlocking{repo.saveProfile("Consent fixture",ClashRepository.SAMPLE)}
   shell("pm grant ${ui.activity.packageName} android.permission.POST_NOTIFICATIONS")
   shell("appops set ${ui.activity.packageName} ACTIVATE_VPN deny")
   assertNotNull(VpnService.prepare(ui.activity))
   ui.onNodeWithText("启动 VPN").performClick()
   assertTrue("VPN consent dialog missing",device.wait(Until.hasObject(By.res("android","button2")),5000))
   device.findObject(By.res("android","button2")).click()
   ui.waitUntil(5000){repo.state.value.error.contains("授权未通过")}
   assertEquals(VpnPhase.STOPPED,repo.state.value.phase);assertEquals("Consent fixture",repo.state.value.selected!!.name)
   ui.onNodeWithContentDescription("关闭提示").performClick();ui.onNodeWithText("启动 VPN").performClick()
   assertTrue(device.wait(Until.hasObject(By.res("android","button1")),5000));device.findObject(By.res("android","button1")).click()
   ui.waitUntil(15000){repo.state.value.phase in listOf(VpnPhase.RUNNING,VpnPhase.FAILED)}
   assertEquals(repo.state.value.error,VpnPhase.RUNNING,repo.state.value.phase)
   ui.activity.startActivity(Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
   assertTrue("VPN settings entry missing",device.wait(Until.hasObject(By.text("Clash Dev")),5000))
   device.findObject(By.text("Clash Dev")).click()
   val disconnect=By.text(java.util.regex.Pattern.compile("(?i)disconnect|断开连接|断开"))
   if(!device.wait(Until.hasObject(disconnect),5000)) {
       val dump=java.io.ByteArrayOutputStream();device.dumpWindowHierarchy(dump);error("Disconnect control missing: "+dump.toString("UTF-8"))
   }
   device.findObject(disconnect).click()
   ui.waitUntil(15000){repo.state.value.phase==VpnPhase.STOPPED}
   assertEquals("Consent fixture",repo.state.value.selected!!.name)
  }finally{
   if(repo.state.value.phase!=VpnPhase.STOPPED){ui.activity.startService(Intent(ui.activity,ClashVpnService::class.java).setAction(ClashVpnService.STOP));ui.waitUntil(15000){repo.state.value.phase==VpnPhase.STOPPED}}
   repo.clearMessage();runBlocking{repo.restoreBackup(before)}
  }
 }
}
