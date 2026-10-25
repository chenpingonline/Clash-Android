package cn.nascp.clash

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManagementIntegrationTest {
 @Test fun editsValidatePreserveSourcesAndRestoreBackup()=runBlocking {
  val repo=ClashRepository.get(InstrumentationRegistry.getInstrumentation().targetContext)
  repo.initialized();assertEquals(VpnPhase.STOPPED,repo.state.value.phase)
  val before=repo.backup()
  try {
   repo.saveProfile("Integration",ClashRepository.SAMPLE)
   val id=repo.state.value.selectedId
   repo.saveSection("rules","- DOMAIN-SUFFIX,example.org,REJECT\n- MATCH,DIRECT\n")
   assertTrue(repo.rendered().contains("example.org"))
   assertEquals(ClashRepository.SAMPLE,repo.state.value.selected!!.yaml)
   val extensionBefore=repo.extensions(id).toString()
   var rejected=false
   try{repo.saveSection("rules","- MATCH,missing-policy")}catch(_:Exception){rejected=true}
   assertTrue(rejected);assertEquals(extensionBefore,repo.extensions(id).toString())
   repo.saveProfile("Integration",ClashRepository.SAMPLE+"\n# update",id)
   assertTrue(repo.rendered().contains("example.org"))
   assertTrue(repo.history(id).length()>0)
   repo.saveSection("network","mixed-port: 17890\nallow-lan: false\nbind-address: 127.0.0.1")
   assertTrue(repo.rendered().contains("17890"))
   val backup=repo.backup();repo.validateBackup(backup)
   repo.resetSection("rules");assertFalse(repo.rendered().contains("example.org"))
   repo.restoreBackup(backup);assertTrue(repo.rendered().contains("example.org"))
   val count=repo.state.value.profiles.size
   try{repo.restoreBackup(JSONObject(backup).put("version",99).toString());fail("accepted unknown backup")}catch(_:IllegalArgumentException){}
   assertEquals(count,repo.state.value.profiles.size)
  } finally {repo.restoreBackup(before)}
 }
}
