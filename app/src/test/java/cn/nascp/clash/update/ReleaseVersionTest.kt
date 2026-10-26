package cn.nascp.clash.update

import org.junit.Assert.*
import org.junit.Test

class ReleaseVersionTest {
    @Test fun comparesNumericComponentsAndPrereleases() {
        val versions = listOf("0.2.9-dev", "0.2.10-dev", "0.2.10-rc.2", "0.2.10-rc.10", "0.2.10", "0.3.0-dev", "1.0.0")
        versions.zipWithNext().forEach { (a, b) -> assertTrue("$a < $b", ReleaseVersion.parse(a)!! < ReleaseVersion.parse(b)!!) }
    }
    @Test fun tagsAndBuildMetadataDoNotAlterOrder() {
        assertEquals(0, ReleaseVersion.parse("v1.2.3+build.7")!!.compareTo(ReleaseVersion.parse("1.2.3")!!))
    }
    @Test fun rejectsAmbiguousAndOverflowVersions() {
        listOf("latest", "0.2", "1.02.3", "1.0.0-01", "999999999999999999999999.0.0", "1.0.0/path", "1.0.0\n").forEach { assertNull(it, ReleaseVersion.parse(it)) }
    }
}
