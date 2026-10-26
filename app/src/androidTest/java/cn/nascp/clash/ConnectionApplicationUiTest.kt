package cn.nascp.clash

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.data.*
import cn.nascp.clash.ui.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ConnectionApplicationUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun activityShowsApplicationIconNameAndSearchesPackages() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val resolved=ConnectionApplicationResolver(context).resolve(android.os.Process.myUid())
        assertTrue("Resolver packages=${resolved.packages}, uid=${resolved.uid}",resolved.packages.contains(context.packageName))
        assertNotNull("Resolver did not load icon: $resolved",resolved.icon)
        val vm=MainViewModel(context.applicationContext as Application)
        val connections=JSONArray().put(JSONObject().put("id","known").put("metadata",JSONObject().put("uid",android.os.Process.myUid()).put("host","known.fixture").put("network","tcp")))
            .put(JSONObject().put("id","unknown").put("metadata",JSONObject().put("host","unknown.fixture").put("network","udp")))
        ui.setContent { ClashTheme("light") { Surface { PlainActivityPage(AppState(phase=VpnPhase.RUNNING,connections=connections),vm){} } } }
        ui.waitUntil(5000){ui.onAllNodesWithTag("connection-app-icon:${context.packageName}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("connection-app-icon:${context.packageName}",useUnmergedTree=true).assertIsDisplayed()
        ui.onNodeWithText("Clash Dev").assertIsDisplayed()
        ui.onNodeWithText("未知应用").assertIsDisplayed()
        ui.onNode(hasSetTextAction()).performTextInput(context.packageName)
        ui.onNodeWithText("known.fixture").assertIsDisplayed()
        ui.onNodeWithText("unknown.fixture").assertDoesNotExist()
    }
    @Test fun sharedUidNeverShowsAnArbitraryApplicationIcon() {
        ui.setContent { ClashTheme("dark","en",1.5f) { Surface { Column {
            ActivityConnectionRow("shared.fixture","TCP",ConnectionApplication(1000,packages=listOf("first.fixture","second.fixture"))){}
        } } } }
        ui.onNodeWithText("Shared UID 1000").assertIsDisplayed()
        ui.onNodeWithTag("connection-app-icon:unknown",useUnmergedTree=true).assertIsDisplayed()
    }
    @Test fun inaccessibleUidFallsBackWithoutGuessingPackage() {
        val resolver=ConnectionApplicationResolver(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue(resolver.resolve(999999999).packages.isEmpty())
        assertNull(connectionUid(JSONObject().put("metadata",JSONObject().put("uid",0))))
        assertNull(connectionUid(JSONObject().put("metadata",JSONObject().put("uid",-1))))
    }
}
