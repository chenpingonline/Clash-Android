package cn.nascp.clash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cn.nascp.clash.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsWorkflowTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun click(text:String){val n=ui.onAllNodesWithText(text).onLast();if(!n.isDisplayed())n.performScrollTo();n.performClick();ui.waitForIdle()}
    private fun back(){ui.onNodeWithContentDescription("返回").performClick();ui.waitForIdle()}
    @Test fun providerDetailsEditOnlySelectedDefinitionAndRetainOtherFields() {
        val repo=ClashRepository.get(ui.activity);runBlocking{repo.initialized()};val backup=repo.backup()
        try {
            runBlocking{repo.saveProfile("Provider workflow","proxies: []\nproxy-groups: []\nrule-providers:\n  one: {type: inline, behavior: domain, format: text, payload: ['+.one.example'], x-note: retained}\n  two: {type: inline, behavior: domain, payload: ['+.two.example']}\nrules: ['RULE-SET,one,DIRECT', 'MATCH,DIRECT']\n")}
            repo.language("zh");repo.theme("light");repo.fontScale(1f)
            ui.openSettingsPage("rule-set");click("one")
            ui.onAllNodes(hasSetTextAction()).assertCountEquals(0)
            click("编辑规则集")
            ui.onAllNodes(hasSetTextAction()).onLast().performTextReplacement("type: inline\nbehavior: domain\nformat: text\npayload: ['+.updated.example']\nx-note: retained\n")
            click("保存修改")
            ui.waitUntil(5000){repo.state.value.previewRevision>0 && ui.onAllNodesWithText("已保存，下次启动 VPN 生效").fetchSemanticsNodes().isNotEmpty()}
            val providers=runBlocking{cn.nascp.clash.ui.decodeYaml(repo.section("rule-providers"))} as JSONObject
            assertEquals("retained",providers.getJSONObject("one").getString("x-note"))
            assertEquals("+.updated.example",providers.getJSONObject("one").getJSONArray("payload").getString(0))
            assertEquals("+.two.example",providers.getJSONObject("two").getJSONArray("payload").getString(0))
            back();click("two");ui.onAllNodes(hasSetTextAction()).assertCountEquals(0)
            ui.onNodeWithText("two").assertExists();ui.onNodeWithText("one").assertDoesNotExist()
        } finally {repo.clearMessage();runBlocking{repo.restoreBackup(backup)}}
    }
    @Test fun remoteEditorsKeepDraftsSeparateForEachInstance() {
        val repo=ClashRepository.get(ui.activity);runBlocking{repo.initialized()};val backup=repo.backup()
        try {
            repo.saveRemotes(listOf(RemoteInstance("qa-first","First remote","https://first.example.invalid","fixture-a"),RemoteInstance("qa-second","Second remote","https://second.example.invalid","fixture-b")))
            repo.language("zh");repo.theme("light");repo.fontScale(1f)
            ui.openSettingsPage("remote")
            ui.onNodeWithTag("remote-edit:qa-first").performScrollTo().performClick();ui.waitForIdle()
            ui.onNode(hasSetTextAction() and hasText("First remote")).performTextReplacement("Unsaved first")
            back();ui.onNodeWithTag("remote-edit:qa-second").performScrollTo().performClick();ui.waitForIdle()
            ui.onNode(hasSetTextAction() and hasText("Second remote")).assertExists()
            ui.onAllNodesWithText("Unsaved first").assertCountEquals(0)
            back();ui.onNodeWithTag("remote-edit:qa-first").performScrollTo().performClick();ui.waitForIdle()
            ui.onNode(hasSetTextAction() and hasText("Unsaved first")).assertExists()
            assertEquals("First remote",repo.remotes().first().name)
        } finally {repo.clearMessage();runBlocking{repo.restoreBackup(backup)}}
    }
    @Test fun restoringBackupRefreshesPreviouslyVisitedSettingsForms() {
        val repo=ClashRepository.get(ui.activity);runBlocking{repo.initialized()};val backup=repo.backup()
        try {
            repo.language("zh");repo.theme("light");repo.fontScale(1f)
            ui.openSettingsPage("vpn")
            ui.onNodeWithText("MTU（1280–9000）").performScrollTo().performTextReplacement("1400")
            back()
            val incoming=JSONObject(repo.backup())
            incoming.getJSONObject("settings").put("vpn-options",JSONObject().put("mtu",1600).toString())
            runBlocking{repo.restoreBackup(incoming.toString())}
            ui.openSettingsPage("vpn")
            ui.onNodeWithText("MTU（1280–9000）").assertTextContains("1600",substring=true)
            assertEquals(1600,JSONObject(repo.draft("vpn-options")).getInt("mtu"))
        } finally {repo.clearMessage();runBlocking{repo.restoreBackup(backup)}}
    }

}
