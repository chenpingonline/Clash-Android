package cn.nascp.clash.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePreferenceTest {
    @Test fun absentAndUnknownThemesFollowSystem() {
        listOf(null, "", "removed-theme").forEach {
            assertEquals("system", ThemePreference.resolve(it))
        }
    }
    @Test fun supportedModesSurvive() {
        listOf("system", "light", "dark").forEach {
            assertEquals(it, ThemePreference.resolve(it))
        }
    }
    @Test fun retiredPalettesMigrateToLight() {
        listOf("plain", "sage", "pearl").forEach {
            assertEquals("light", ThemePreference.resolve(it))
        }
    }
}
