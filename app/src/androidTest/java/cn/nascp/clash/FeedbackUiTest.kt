package cn.nascp.clash

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import cn.nascp.clash.data.*
import cn.nascp.clash.vpn.ClashVpnService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class FeedbackUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use{it.readBytes()}
    private fun click(text:String){val node=ui.onAllNodesWithText(text).onLast();if(!node.isDisplayed())node.performScrollTo();node.performClick();ui.waitForIdle()}
    private fun screenshot(name:String){
        ui.waitForIdle()
        val file=File(ui.activity.getExternalFilesDir(null),"feedback-qa/$name.png");file.parentFile!!.mkdirs()
        instrumentation.uiAutomation.takeScreenshot().also{image->file.outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
        shell("mkdir -p /data/local/tmp/clash-feedback-qa")
        shell("cp ${file.absolutePath} /data/local/tmp/clash-feedback-qa/${file.name}")
    }
    private fun noDialogs(){ui.onAllNodes(isDialog()).assertCountEquals(0)}
    @Test fun noticesDoNotBlockNavigationAndOldTimersCannotClearNewErrors(){
        val repo=ClashRepository.get(ui.activity);runBlocking{repo.initialized()};val before=repo.backup()
        try{
            repo.language("zh");repo.fontScale(1f);repo.theme("light");repo.clearMessage()
            repo.message("设置已保存")
            ui.waitForIdle();noDialogs()
            click("设置");ui.onNodeWithText("设置已保存").assertExists()
            screenshot("info-light")
            ui.waitUntil(8000){repo.state.value.error.isBlank()}
            repo.message("较早的普通提示");val oldId=repo.state.value.messageId
            repo.message("订阅下载失败：HTTP 403；请切换订阅身份后重试",MessageKind.ERROR)
            repo.clearMessage(oldId)
            assertTrue(repo.state.value.error.contains("403"))
            click("关于与更新");noDialogs()
            screenshot("error-light")
            val start=android.os.SystemClock.uptimeMillis()
            ui.waitUntil(7000){android.os.SystemClock.uptimeMillis()-start>5200}
            assertTrue(repo.state.value.error.contains("403"))
            ui.onNodeWithContentDescription("关闭提示").performClick()
            ui.waitUntil(2000){repo.state.value.error.isBlank()}
            click("开源许可与来源")
            ui.onNodeWithText("GPL-3.0 · Mihomo / Clash-Manager").assertExists();noDialogs()
            click("关闭");ui.onNodeWithText("GPL-3.0 · Mihomo / Clash-Manager").assertDoesNotExist()
        }finally{repo.clearMessage();runBlocking{repo.restoreBackup(before)}}
    }
    @Test fun inlineDeleteCanBeCancelledAndOnlyDeletesAfterConfirmation(){
        val repo=ClashRepository.get(ui.activity);runBlocking{repo.initialized()};val before=repo.backup()
        try{
            repo.language("zh");repo.fontScale(1f);repo.clearMessage()
            runBlocking{repo.saveProfile("Feedback fixture",ClashRepository.SAMPLE)};val id=repo.state.value.selectedId
            click("配置");click("管理")
            for(theme in listOf("plain","light","dark")){
                repo.theme(theme);ui.waitForIdle()
                click("删除配置");ui.onNodeWithText("删除 Feedback fixture？").assertExists();noDialogs()
                assertTrue(repo.state.value.profiles.any{it.id==id});screenshot("delete-$theme")
                click("取消");assertTrue(repo.state.value.profiles.any{it.id==id})
                ui.onNodeWithText("删除 Feedback fixture？").assertDoesNotExist()
            }
            click("删除配置");click("删除")
            ui.waitUntil(5000){repo.state.value.profiles.none{it.id==id}}
            ui.onNodeWithText("配置已删除").assertExists();noDialogs()
        }finally{repo.clearMessage();runBlocking{repo.restoreBackup(before)}}
    }
    @Test fun startingVpnDoesNotAutomaticallyRequestNotificationPermission(){
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT>=33)
        val repo=ClashRepository.get(ui.activity);runBlocking{repo.initialized()};val before=repo.backup()
        val permission=android.Manifest.permission.POST_NOTIFICATIONS
        val granted=ui.activity.checkSelfPermission(permission)==android.content.pm.PackageManager.PERMISSION_GRANTED
        org.junit.Assume.assumeTrue("Deny notifications before starting instrumentation; revoking a granted permission kills its process",!granted)
        try{
            repo.language("zh");repo.fontScale(1f);repo.theme("light");repo.clearMessage()
            runBlocking{repo.saveProfile("Notification fixture",ClashRepository.SAMPLE)}
            shell("appops set ${ui.activity.packageName} ACTIVATE_VPN allow")
            click("启动 VPN")
            ui.waitUntil(15000){repo.state.value.phase in listOf(VpnPhase.RUNNING,VpnPhase.FAILED)}
            assertEquals("VPN should run without requesting notification permission",VpnPhase.RUNNING,repo.state.value.phase)
            assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,ui.activity.checkSelfPermission(permission))
            assertFalse(UiDevice.getInstance(instrumentation).hasObject(By.res(Pattern.compile(".*:id/permission_allow_button"))))
            noDialogs();screenshot("vpn-without-notification-prompt")
            click("停止");ui.waitUntil(15000){repo.state.value.phase==VpnPhase.STOPPED}
        }finally{
            if(repo.state.value.phase!=VpnPhase.STOPPED){ui.activity.startService(Intent(ui.activity,ClashVpnService::class.java).setAction(ClashVpnService.STOP));ui.waitUntil(15000){repo.state.value.phase==VpnPhase.STOPPED}}
            shell("appops set ${ui.activity.packageName} ACTIVATE_VPN default")
            repo.clearMessage();runBlocking{repo.restoreBackup(before)}
        }
    }
}
