package cn.nascp.clash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import cn.nascp.clash.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.regex.Pattern

class DocumentFlowTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
 private fun click(text:String){val node=ui.onAllNodesWithText(text).onLast();if(!node.isDisplayed())node.performScrollTo();node.performClick();ui.waitForIdle()}
 private fun saveAs(name:String){
  val input=By.clazz("android.widget.EditText")
  assertTrue("System file picker missing",device.wait(Until.hasObject(input),5000))
  device.findObject(input).text=name
  val save=By.text(Pattern.compile("(?i)save|保存"))
  assertTrue("System save control missing",device.wait(Until.hasObject(save),5000));device.findObject(save).click()
 }
 @Test fun exportBackupAndYamlThroughSystemPicker(){
  val repo=ClashRepository.get(ui.activity);runBlocking{repo.initialized()};val before=repo.backup()
  val name="clash-qa-${System.currentTimeMillis()}"
  try{
   repo.language("zh");repo.fontScale(1f);repo.clearMessage();runBlocking{repo.saveProfile("Document fixture",ClashRepository.SAMPLE)}
   ui.onAllNodesWithText("设置").onLast().performClick();click("备份与恢复")
   click("导出完整备份");saveAs("$name.json")
   ui.waitUntil(10000){repo.state.value.error=="备份已导出"};ui.onNodeWithContentDescription("关闭提示").performClick()
   val exported=device.executeShellCommand("cat /sdcard/Download/$name.json")
   assertEquals("clash-android",JSONObject(exported).getString("format"));runBlocking{repo.validateBackup(exported)}
   click("导出当前 YAML");saveAs("$name.yaml")
   ui.waitUntil(10000){repo.state.value.error=="已导出原始配置"};ui.onNodeWithContentDescription("关闭提示").performClick()
   assertEquals(ClashRepository.SAMPLE,device.executeShellCommand("cat /sdcard/Download/$name.yaml"))
   // Restore from an actual SAF selection, including validation and confirmation.
   runBlocking{repo.saveProfile("Modified document fixture",ClashRepository.SAMPLE,repo.state.value.selectedId)}
   click("选择备份并校验")
   val file=By.text("$name.json")
   assertTrue("Exported backup not visible in picker",device.wait(Until.hasObject(file),5000));device.findObject(file).click()
   ui.waitUntil(5000){ui.onAllNodesWithText("确认恢复").fetchSemanticsNodes().isNotEmpty()};click("确认恢复")
   ui.waitUntil(5000){repo.state.value.error=="备份已恢复"};assertEquals("Document fixture",repo.state.value.selected!!.name)
   ui.onNodeWithContentDescription("关闭提示").performClick();ui.onNodeWithContentDescription("返回").performClick();click("关于与更新");click("诊断与导出")
   click("导出诊断 ZIP");saveAs("$name.zip")
   ui.waitUntil(10000){repo.state.value.error=="诊断包已导出"}
   val zip=android.util.Base64.decode(device.executeShellCommand("base64 /sdcard/Download/$name.zip"),android.util.Base64.DEFAULT)
   val entries=mutableMapOf<String,String>()
   java.util.zip.ZipInputStream(zip.inputStream()).use{stream->while(true){val entry=stream.nextEntry?:break;entries[entry.name]=stream.readBytes().toString(Charsets.UTF_8)}}
   assertEquals(setOf("diagnostics.json","README.txt"),entries.keys)
   assertFalse(entries.getValue("diagnostics.json").contains("Document fixture"));assertFalse(entries.getValue("diagnostics.json").contains("MATCH"))
  }finally{
   repo.clearMessage();runBlocking{repo.restoreBackup(before)}
   device.executeShellCommand("rm -f /sdcard/Download/$name.json /sdcard/Download/$name.yaml /sdcard/Download/$name.zip")
  }
 }
}
