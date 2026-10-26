package cn.nascp.clash

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.data.*
import cn.nascp.clash.ui.*
import org.junit.Rule
import org.junit.Test

class HomeSummaryUiTest {
    @get:Rule val ui=createComposeRule()
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    @Test fun homeShowsLiveAndCumulativeTrafficWithExitCardAndNoFooter() {
        val vm=MainViewModel(app)
        val state=AppState(ready=true,phase=VpnPhase.RUNNING,up=1024,down=2048,totalUp=32L*1024*1024,totalDown=64L*1024*1024,
            exitLocation=ExitLocation("203.0.113.8","新加坡","SG","Central","Singapore","Asia/Singapore","+08:00",organization="Fixture ISP",asn=13335,asnOrganization="Fixture Network",source="fixture.example"))
        ui.setContent { ClashTheme("light") { Surface { PlainHome(state,{},{},{},vm) } } }
        ui.onNodeWithText("2.0 KB/s").assertIsDisplayed()
        ui.onNodeWithText("1.0 KB/s").assertIsDisplayed()
        ui.onNodeWithText("累计 64.0 MB").assertIsDisplayed()
        ui.onNodeWithText("累计 32.0 MB").assertIsDisplayed()
        ui.onNodeWithTag("home-exit-ip").assertTextEquals("203.0.113.8")
        ui.onNodeWithText("新加坡").assertExists()
        ui.onNodeWithText("Fixture ISP").assertExists()
        ui.onNodeWithText("AS13335").assertExists()
        ui.onNodeWithText("Fixture Network").assertExists()
        ui.onNodeWithText("fixture.example").assertExists()
        ui.onNodeWithText("Asia/Singapore · +08:00").assertExists()
        ui.onNodeWithText("本机真实内核。VPN运行与外网连通性分别显示。").assertDoesNotExist()
    }
    @Test fun failureShowsRetryExplanationAndRefreshRemainsAvailable() {
        var refreshed=false
        ui.setContent { ClashTheme("light") { Surface {
            HomeExitCard(AppState(phase=VpnPhase.RUNNING,exitError="failure")){refreshed=true}
        } } }
        ui.onNodeWithText("出口信息更新失败，请重试").assertIsDisplayed()
        ui.onNodeWithTag("refresh-exit-location").assertIsEnabled().performClick()
        org.junit.Assert.assertTrue(refreshed)
    }
    @Test fun ipv6AndLargeEnglishTextWrapAndDisconnectHidesOldLocation() {
        val vm=MainViewModel(app)
        var state by mutableStateOf(AppState(phase=VpnPhase.RUNNING,exitLocation=ExitLocation("2001:db8:1234:5678:abcd:1234:5678:abcd","United States","US","California","San Francisco","America/Los_Angeles","-07:00")))
        ui.setContent { ClashTheme("dark","en",1.5f) { Surface { PlainHome(state,{},{},{},vm) } } }
        ui.onNodeWithTag("home-exit-ip").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("America/Los_Angeles · -07:00").performScrollTo().assertIsDisplayed()
        ui.runOnIdle { state=state.copy(phase=VpnPhase.STOPPED,exitLocation=null) }
        ui.onNodeWithTag("home-exit-ip").performScrollTo().assertTextEquals("Disconnected")
        ui.onNodeWithTag("refresh-exit-location").assertIsNotEnabled()
    }
}
