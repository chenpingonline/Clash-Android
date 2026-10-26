package cn.nascp.clash.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpeedTestSettingsTest {
    @Test fun defaultsToCloudflareAndPreservesExistingUrls() {
        assertEquals(SpeedTestSettings.CLOUDFLARE, SpeedTestSettings.effectiveUrl(""))
        listOf(SpeedTestSettings.GSTATIC, "https://example.org/check").forEach {
            assertEquals(it, SpeedTestSettings.effectiveUrl(it))
        }
        assertEquals("cloudflare", SpeedTestSettings.preset(SpeedTestSettings.CLOUDFLARE))
        assertEquals("gstatic", SpeedTestSettings.preset(SpeedTestSettings.GSTATIC))
        assertEquals("custom", SpeedTestSettings.preset("http://example.org/check"))
    }

    @Test fun acceptsHttpAndHttpsIncludingCustomAddresses() {
        listOf(SpeedTestSettings.CLOUDFLARE, SpeedTestSettings.GSTATIC,
            "http://127.0.0.1:8080/check?test=1", "https://example.org/").forEach {
            assertEquals(it, SpeedTestSettings.validatedUrl(" $it "))
        }
    }

    @Test fun rejectsMalformedOrUnsupportedAddresses() {
        listOf("", "example.org", "https://", "https:///check", "ftp://example.org/check",
            "https://example.org/a b", "http://example.org:99999/check").forEach { value ->
            assertThrows(value, IllegalArgumentException::class.java) { SpeedTestSettings.validatedUrl(value) }
        }
    }
}
