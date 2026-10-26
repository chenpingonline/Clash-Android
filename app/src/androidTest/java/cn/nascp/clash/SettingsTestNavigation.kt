package cn.nascp.clash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import cn.nascp.clash.data.ClashRepository
import cn.nascp.clash.ui.localized
import cn.nascp.clash.ui.screens

/** Traverse user-facing entries instead of relying on the removed developer directory. */
internal val settingsPagePaths=buildMap<String,List<String>> {
    listOf("appearance","vpn","automation","system","backup","core","global-enhancements","remote","about").forEach{put(it,listOf("settings",it))}
    put("apps",listOf("settings","vpn","apps"))
    listOf("geo-status","speed-test","diagnostics").forEach{put(it,listOf("settings","core",it))}
    listOf("global-override","global-script").forEach{put(it,listOf("settings","global-enhancements",it))}
    put("import",listOf("profiles","import"));put("profile",listOf("profiles","profile"))
    listOf("profile-info","node-edit","group-edit","profile-rules","network","dns","geo","override","script","yaml","history").forEach{put(it,listOf("profiles","profile",it))}
    listOf("dns-servers","fakeip","fallback","hosts").forEach{put(it,listOf("profiles","profile","dns",it))}
    listOf("rule-edit","rule-import","rule-set").forEach{put(it,listOf("profiles","profile","profile-rules",it))}
    put("rule-provider",listOf("profiles","profile","profile-rules","rule-set","rule-provider"))
    put("rules",listOf("activity","rules"));put("runtime-providers",listOf("activity","rules","runtime-providers"))
    put("remote-editor",listOf("settings","remote","remote-editor"))
}

internal fun AndroidComposeTestRule<*,MainActivity>.openSettingsPage(id:String) {
    val repo=ClashRepository.get(activity)
    val language=repo.state.value.language
    val back=localized("返回",language)
    var count=0
    while(onAllNodesWithContentDescription(back).fetchSemanticsNodes().isNotEmpty() && count++<12) {
        onNodeWithContentDescription(back).performClick();waitForIdle()
    }
    val path=settingsPagePaths.getValue(id)
    val tab=mapOf("settings" to "设置","profiles" to "配置","activity" to "活动").getValue(path.first())
    onAllNodesWithText(localized(tab,language)).onLast().performClick();waitForIdle()
    path.drop(1).forEach { route ->
        val node=if(route=="profile")onNodeWithTag("profile-manage:${repo.state.value.selectedId}")
        else if(route=="import")onNodeWithContentDescription(localized("添加配置",language))
        else onAllNodes(hasText(localized(when(route){"rule-provider"->"添加规则集";"remote-editor"->"新增实例";else->screens.getValue(route).title},language)) and hasClickAction()).onLast()
        if(!node.isDisplayed())node.performScrollTo()
        node.performClick();waitForIdle()
        if(route=="profile"){onNodeWithTag("profile-details:${repo.state.value.selectedId}").performClick();waitForIdle()}
        try { waitUntil(7000){onAllNodesWithTag("route:$route").fetchSemanticsNodes().isNotEmpty()} }
        catch(error:Throwable){throw AssertionError("Opening $id at $route\n"+onRoot().printToString(),error)}
    }
}
