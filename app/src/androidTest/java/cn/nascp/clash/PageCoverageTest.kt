package cn.nascp.clash

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.data.*
import cn.nascp.clash.ui.screens
import cn.nascp.clash.ui.localized
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PageCoverageTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 @Test fun secondaryPagesRenderInBothThemes(){
  val repo=ClashRepository.get(ui.activity)
  runBlocking{repo.initialized()}
  val before=repo.backup()
  try{
   runBlocking{repo.saveProfile("UI validation",ClashRepository.SAMPLE)}
   repo.language("zh");repo.fontScale(1f)
   ui.waitForIdle()
   ui.onAllNodesWithText("设置").onLast().performClick()
   ui.onNodeWithText("关于与更新").performScrollTo().performClick()
   val cases = if (InstrumentationRegistry.getArguments().getString("plainOnly") == "true")
    listOf(Triple("plain", "zh", 1f), Triple("plain", "en", 1.5f))
   else listOf(Triple("light","zh",1f),Triple("dark","zh",1f),Triple("light","en",1.5f),Triple("dark","en",1.5f))
   val actionAudit = StringBuilder("theme\tlanguage\tscale\tpage\tactions\tdisabled\tlabels\n")
   for((theme,language,scale) in cases) {
    repo.theme(theme);repo.language(language);repo.fontScale(scale);ui.waitForIdle()
    val selectedScreens=InstrumentationRegistry.getArguments().getString("screenIds")?.split(",")
    for(spec in screens.values.filter{selectedScreens==null || it.id in selectedScreens}) {
     ui.mainClock.advanceTimeBy(300); ui.waitForIdle()
     ui.onNodeWithTag("route:about").assertExists()
     if(spec.id!="about")ui.onAllNodes(hasText(localized(spec.title,language)) and hasClickAction()).onLast().performScrollTo().performClick()
     ui.mainClock.advanceTimeBy(300); ui.waitForIdle()
     ui.onNodeWithTag("route:${spec.id}").assertExists()
     if (theme == "plain") {
      val actions = ui.onAllNodesWithTag("plain-subpage-action").fetchSemanticsNodes()
      val minHeight = 48f * ui.activity.resources.displayMetrics.density
      actions.forEach { node -> org.junit.Assert.assertTrue("${spec.id}: action touch height must be at least 48dp", node.size.height >= minHeight - 1f) }
      val disabled = actions.count { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
      val labels = actions.flatMap { if (it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) it.config[androidx.compose.ui.semantics.SemanticsProperties.Text] else emptyList() }.joinToString(" | ") { it.text }.replace("\t", " ").replace("\n", " ")
      actionAudit.append("$theme\t$language\t$scale\t${spec.id}\t${actions.size}\t$disabled\t$labels\n")
     }
     // Actual hierarchy must retain navigation after page composition and data loading.
     ui.onNodeWithContentDescription(localized("返回",language)).assertExists()
     val file=File(ui.activity.getExternalFilesDir(null),"uiqa/$theme-$language-${spec.id}.png")
     file.parentFile!!.mkdirs()
     InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().also { bitmap->file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle() }
     val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
     android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("mkdir -p /data/local/tmp/clash-uiqa")).use{it.readBytes()}
     android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/clash-uiqa/${file.name}")).use{it.readBytes()}
     if (theme == "plain" && ui.onAllNodesWithTag("plain-subpage-action").fetchSemanticsNodes().isNotEmpty()) {
      ui.onAllNodesWithTag("plain-subpage-action").onLast().performScrollTo();ui.waitForIdle()
      val actionFile=File(file.parentFile,"$theme-$language-${spec.id}-actions.png")
      InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().also { bitmap->actionFile.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle() }
      android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cp ${actionFile.absolutePath} /data/local/tmp/clash-uiqa/${actionFile.name}")).use{it.readBytes()}
     }
     if(spec.id!="about")ui.onNodeWithContentDescription(localized("返回",language)).performClick()
     ui.mainClock.advanceTimeBy(300); ui.waitForIdle()
     ui.onNodeWithTag("route:about").assertExists()
    }
   }
   val auditFile=File(ui.activity.getExternalFilesDir(null),"uiqa/actions.tsv")
   auditFile.writeText(actionAudit.toString())
   android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cp ${auditFile.absolutePath} /data/local/tmp/clash-uiqa/actions.tsv")).use{it.readBytes()}
  }finally{runBlocking{repo.restoreBackup(before)}}
 }
 @Test fun emptyProfilePagesHaveRecoverableStates(){
  val repo=ClashRepository.get(ui.activity);runBlocking{repo.initialized()};val before=repo.backup()
  try{
   runBlocking{repo.restoreBackup(org.json.JSONObject(before).put("profiles",org.json.JSONArray()).put("settings",org.json.JSONObject()).put("selected","").toString())}
   repo.language("zh");repo.fontScale(1f);repo.clearMessage();ui.waitForIdle()
   ui.onAllNodesWithText("设置").onLast().performClick()
   ui.onNodeWithText("关于与更新").performScrollTo().performClick()
   for(spec in screens.values.filter{it.id!="about"}) {
    ui.mainClock.advanceTimeBy(300);ui.waitForIdle();ui.onNodeWithTag("route:about").assertExists()
    ui.onAllNodes(hasText(spec.title) and hasClickAction()).onLast().performScrollTo().performClick()
    ui.mainClock.advanceTimeBy(300);ui.waitForIdle();ui.onNodeWithTag("route:${spec.id}").assertExists()
    ui.onNodeWithContentDescription("返回").assertExists()
    if(repo.state.value.error.isNotBlank())error("Empty ${spec.id} raised dialog: "+repo.state.value.error)
    ui.onNodeWithContentDescription("返回").performClick();ui.mainClock.advanceTimeBy(300);ui.waitForIdle()
   }
  }finally{repo.clearMessage();runBlocking{repo.restoreBackup(before)}}
 }

}
