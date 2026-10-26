package cn.nascp.clash

import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.data.ClashRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FontWeightSettingsTest {
    @Test fun backupRoundTripLegacyAndProfilesOnlyRestore() = runBlocking {
        val repo=ClashRepository.get(InstrumentationRegistry.getInstrumentation().targetContext)
        repo.initialized()
        val original=repo.backup()
        try {
            repo.fontWeight(100)
            val heavier=repo.backup()
            repo.fontWeight(-100)
            repo.restoreBackup(heavier)
            assertEquals(100,repo.state.value.fontWeight)
            val profilesOnly=repo.backup(includeSettings=false)
            repo.fontWeight(-100)
            repo.restoreBackup(profilesOnly)
            assertEquals(-100,repo.state.value.fontWeight)
            repo.restoreBackup(JSONObject(heavier).apply{remove("fontWeight")}.toString())
            assertEquals(0,repo.state.value.fontWeight)
            repo.restoreBackup(JSONObject(heavier).put("fontWeight",999).toString())
            assertEquals(0,repo.state.value.fontWeight)
            assertTrue(runCatching{repo.fontWeight(999)}.isFailure)
        } finally { repo.restoreBackup(original) }
    }
}
