package cn.nascp.clash

import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Run seed on 0.2.0, install -r 0.2.1, then run verify without clearing app data. */
class UpgradePersistenceTest {
 @Test fun upgradePreservesEncryptedProfileAndSettings()=runBlocking {
  val phase=InstrumentationRegistry.getArguments().getString("upgradePhase")
  assumeTrue(phase in listOf("seed","verify"))
  val repo=ClashRepository.get(InstrumentationRegistry.getInstrumentation().targetContext);repo.initialized()
  if(phase=="seed") {
   repo.saveDraft("upgrade-original",repo.backup())
   repo.saveProfile("Upgrade fixture",ClashRepository.SAMPLE)
   repo.saveExtension(repo.state.value.selectedId,"override","mixed-port: 17895")
   repo.theme("dark");repo.language("en");repo.fontScale(1.2f)
   // Instrumentation has no Activity pause boundary; flush the fixture before
   // its process exits so this tests upgrades, not an artificial abrupt kill.
   assertTrue(InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("preferences",0).edit().commit())
  }else{
   val original=repo.draft("upgrade-original")
   assertTrue("Upgrade seed preferences missing",original.isNotBlank())
   try{
    assertEquals("Upgrade fixture",repo.state.value.selected!!.name)
    assertEquals("dark",repo.state.value.theme);assertEquals("en",repo.state.value.language)
    assertEquals(1.2f,repo.state.value.fontScale,0.001f);assertTrue(repo.rendered().contains("17895"))
   }finally{repo.restoreBackup(original)}
  }
 }
}
