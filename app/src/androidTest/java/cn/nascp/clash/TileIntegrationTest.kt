package cn.nascp.clash

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import cn.nascp.clash.data.*
import cn.nascp.clash.vpn.ClashVpnService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TileIntegrationTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 @Test fun addTileOpensSystemConfirmation(){
  val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  val repo=ClashRepository.get(ui.activity);runBlocking{repo.initialized()}
  val before=repo.backup()
  val original=device.executeShellCommand("settings get secure sysui_qs_tiles").trim()
  val component="${ui.activity.packageName}/cn.nascp.clash.system.VpnTileService"
  try {
   repo.language("zh");repo.fontScale(1f);repo.clearMessage()
   device.executeShellCommand("cmd statusbar remove-tile $component")
   ui.onAllNodesWithText("设置").onLast().performClick()
   ui.onNodeWithText("系统集成与后台").performScrollTo().performClick()
   ui.onNodeWithText("添加到快捷设置").performScrollTo().performClick()
   assertTrue("System add-tile confirmation should appear",device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text("Clash VPN")),5000))
   assertTrue(device.hasObject(androidx.test.uiautomator.By.pkg("com.android.systemui")))
   device.executeShellCommand("screencap -p /data/local/tmp/clash-quick-settings-add.png")
   device.pressBack()
  } finally {
   device.pressBack()
   device.executeShellCommand("cmd statusbar set-tiles $original")
   runBlocking{repo.restoreBackup(before)}
  }
 }
 @Test fun systemTileStartsAndStopsAndCoreLevelControlsRuntime(){
  val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  fun shell(cmd:String)=device.executeShellCommand(cmd)
  val repo=ClashRepository.get(ui.activity);runBlocking{repo.initialized()};val before=repo.backup()
  val component="${ui.activity.packageName}/cn.nascp.clash.system.VpnTileService"
  val original=shell("settings get secure sysui_qs_tiles").trim()
  try{
   repo.language("zh");repo.fontScale(1f);repo.clearMessage();runBlocking{repo.saveProfile("Tile fixture",ClashRepository.SAMPLE)}
   shell("appops set ${ui.activity.packageName} ACTIVATE_VPN allow")
   ui.waitForIdle()
   shell("cmd statusbar add-tile $component");shell("cmd statusbar expand-settings")
   // Establish the system binding before invoking the tile's actual click handler.
   device.waitForIdle()
   shell("cmd statusbar click-tile $component")
   ui.waitUntil(15000){repo.state.value.phase==VpnPhase.RUNNING}
   shell("cmd statusbar expand-settings");device.waitForIdle()
   shell("screencap -p /data/local/tmp/clash-quick-settings-connected.png")
   shell("cmd statusbar collapse");ui.waitForIdle()
   ui.onAllNodesWithText("设置").onLast().performClick()
   ui.onNodeWithText("内核与诊断").performScrollTo().performClick()
   ui.onNodeWithTag("log-level-setting").performScrollTo().performClick()
   ui.onNodeWithTag("log-level-option:debug").performClick()
   ui.waitUntil(5000){runBlocking{repo.api("/configs")}.optString("log-level")=="debug"}
   shell("cmd statusbar expand-settings");device.waitForIdle();shell("cmd statusbar click-tile $component")
   ui.waitUntil(15000){repo.state.value.phase==VpnPhase.STOPPED}
   shell("cmd statusbar expand-settings");device.waitForIdle()
   shell("screencap -p /data/local/tmp/clash-quick-settings-disconnected.png")
   shell("cmd statusbar collapse");ui.waitForIdle()
   ui.onNodeWithTag("log-level-setting").performScrollTo().assertIsNotEnabled()
  }finally{
   shell("cmd statusbar collapse");shell("cmd statusbar set-tiles $original")
   if(repo.state.value.phase!=VpnPhase.STOPPED){ui.activity.startService(Intent(ui.activity,ClashVpnService::class.java).setAction(ClashVpnService.STOP));ui.waitUntil(15000){repo.state.value.phase==VpnPhase.STOPPED}}
   repo.clearMessage();runBlocking{repo.restoreBackup(before)}
  }
 }
}
