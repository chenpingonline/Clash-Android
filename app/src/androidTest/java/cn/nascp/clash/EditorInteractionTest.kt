package cn.nascp.clash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.nascp.clash.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorInteractionTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private fun click(text:String){val node=ui.onAllNodesWithText(text).onLast();if(!node.isDisplayed())node.performScrollTo();node.performClick();ui.waitForIdle()}
 private fun fill(label:String,value:String){ui.onNodeWithText(label).performScrollTo().performTextReplacement(value);ui.waitForIdle()}
 private fun back(){ui.onNodeWithContentDescription("返回").performClick();ui.waitForIdle()}
 @Test fun editsThroughRealControlsPersistAndInvalidInputDoesNotOverwrite(){
  val repo=ClashRepository.get(ui.activity);runBlocking{repo.initialized()};val before=repo.backup()
  try{
   runBlocking{repo.saveProfile("Editor fixture",ClashRepository.SAMPLE.replace("  - MATCH,默认策略","  - IP-CIDR,203.0.113.0/24,DIRECT,no-resolve\n  - MATCH,默认策略"))};repo.language("zh");repo.fontScale(1f);repo.clearMessage()
   ui.onAllNodesWithText("设置").onLast().performClick();click("关于应用")
   ui.openSettingsPage("group-edit")
   click("YAML 编辑");ui.waitUntil(5000){ui.onAllNodesWithText("可视化编辑").fetchSemanticsNodes().isNotEmpty()};click("可视化编辑")
   fill("名称","Added group");ui.onNodeWithText("select").assertExists()
   click("添加到列表");click("保存全部更改")
   ui.waitUntil(5000){repo.extensions(repo.state.value.selectedId).toString().contains("Added group")};back()
   ui.openSettingsPage("rule-edit");ui.onAllNodesWithText("编辑").onFirst().performScrollTo().performClick()
   fill("规则内容（MATCH 留空）","203.0.114.0/24");click("更新列表中的条目");click("保存全部更改")
   ui.waitUntil(5000){repo.extensions(repo.state.value.selectedId).toString().contains("203.0.114")}
   assertTrue(runBlocking{repo.rendered()}.contains("IP-CIDR,203.0.114.0/24,DIRECT,no-resolve"));back()
   ui.openSettingsPage("network");fill("Mixed 端口","17890")
   ui.waitUntil(5000){ui.onAllNodesWithText("保存修改").fetchSemanticsNodes().isNotEmpty()};ui.waitForIdle()
   // Debounced form conversion must finish before Save becomes enabled.
   ui.waitUntil(5000){runCatching{ui.onNodeWithText("保存修改").assertIsEnabled()}.isSuccess}
   click("保存修改");ui.waitUntil(5000){repo.extensions(repo.state.value.selectedId).toString().contains("17890")}
   val saved=repo.extensions(repo.state.value.selectedId).toString()
   fill("Mixed 端口","not-a-port");ui.waitUntil(5000){runCatching{ui.onNodeWithText("保存修改").assertIsNotEnabled()}.isSuccess}
   assertEquals(saved,repo.extensions(repo.state.value.selectedId).toString());fill("Mixed 端口","17891")
   ui.waitUntil(5000){runCatching{ui.onNodeWithText("保存修改").assertIsEnabled()}.isSuccess};click("保存修改")
   ui.waitUntil(5000){repo.extensions(repo.state.value.selectedId).toString().contains("17891")};back()
   val vpnBefore=repo.draft("vpn-options")
   ui.openSettingsPage("vpn");fill("MTU（1280–9000）","1200");click("保存 VPN 设置")
   ui.waitUntil(5000){repo.state.value.error.isNotBlank()};assertEquals(vpnBefore,repo.draft("vpn-options"));ui.onAllNodes(isDialog()).assertCountEquals(0)
   val ipv6Before=org.json.JSONObject(vpnBefore.ifBlank{"{}"}).optBoolean("ipv6")
   click("接管 IPv6")
   fill("MTU（1280–9000）","1400");click("保存 VPN 设置");ui.waitUntil(5000){repo.draft("vpn-options").contains("1400")};assertEquals(!ipv6Before,org.json.JSONObject(repo.draft("vpn-options")).getBoolean("ipv6"));ui.onNodeWithContentDescription("关闭提示").performClick();back()
  }finally{repo.clearMessage();runBlocking{repo.restoreBackup(before)}}
 }
}
