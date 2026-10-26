package cn.nascp.clash

import androidx.test.platform.app.InstrumentationRegistry
import cn.nascp.clash.update.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import androidx.core.content.FileProvider
import android.content.Intent
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import java.security.MessageDigest

class AppUpdateIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val digest = "a".repeat(64)
    private fun release(tag: String = "v0.2.45-dev", prerelease: Boolean = true) = JSONObject()
        .put("tag_name", tag).put("prerelease", prerelease).put("draft", false).put("body", "Update notes")
        .put("assets", JSONArray().put(JSONObject().put("name", "Clash-arm64.apk").put("state", "uploaded")
            .put("size", 12).put("digest", "sha256:$digest")
            .put("browser_download_url", "$UPDATE_REPOSITORY/releases/download/$tag/Clash-arm64.apk")))

    @Test fun releaseSelectionRespectsChannelsDraftsAndNumericOrder() {
        val releases = JSONArray().put(release("v0.2.9", false)).put(release("v0.2.46-dev").put("draft", true))
            .put(release()).put(release("v0.2.44", false))
        assertEquals("v0.2.45-dev", AppUpdateRepository.parseReleases(releases, "0.2.43-dev")!!.tag)
        assertEquals("v0.2.44", AppUpdateRepository.parseReleases(releases, "0.2.43")!!.tag)
        assertNull(AppUpdateRepository.parseReleases(releases, "0.2.45"))
        assertNull(AppUpdateRepository.parseReleases(JSONArray(), "0.2.44-dev"))
    }

    @Test fun missingDigestAmbiguousAssetsAndForeignUrlsAreRejected() {
        val missing = release().apply { getJSONArray("assets").getJSONObject(0).remove("digest") }
        val ambiguous = release().apply { getJSONArray("assets").put(getJSONArray("assets").getJSONObject(0)) }
        val foreign = release().apply { getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://example.com/Clash.apk") }
        listOf(missing, ambiguous, foreign).forEach {
            assertTrue(runCatching { AppUpdateRepository.parseReleases(JSONArray().put(it), "0.2.44-dev") }.isFailure)
        }
    }

    private fun client(code: Int, bytes: ByteArray) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
            .body(bytes.toResponseBody(null)).build()
    }.build()

    @Test fun rateLimitAndServerFailuresAreNotReportedAsLatest() = runBlocking {
        for (code in listOf(403, 429, 500)) {
            val result = runCatching { AppUpdateRepository(context, client(code, "[]".toByteArray())).check() }
            assertTrue("HTTP $code", result.isFailure)
        }
        assertNull(AppUpdateRepository(context, client(200, "[]".toByteArray())).check())
    }

    @Test fun hashFailureAndIncompleteDownloadsRemovePartialFiles() = runBlocking {
        val bytes = "invalid-apk".toByteArray()
        val selected = AppRelease("v0.2.45-dev", "", "$UPDATE_REPOSITORY/releases/download/v0.2.45-dev/Clash-arm64.apk", bytes.size.toLong(), digest)
        val dir = File(context.cacheDir, "app-updates")
        for (candidate in listOf(selected, selected.copy(size = bytes.size + 1L))) {
            val result = runCatching { AppUpdateRepository(context, client(200, bytes)).download(candidate) {} }
            assertTrue(result.isFailure)
            assertFalse(File(dir, "update.apk.part").exists())
            assertFalse(File(dir, "update.apk").exists())
        }
    }

    @Test fun matchingHashStillRejectsNonApkAndNonIncreasingVersion() = runBlocking {
        val bytes = "not-an-apk".toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val selected = AppRelease("v0.2.45-dev", "", "$UPDATE_REPOSITORY/releases/download/v0.2.45-dev/Clash-arm64.apk", bytes.size.toLong(), hash)
        val result = runCatching { AppUpdateRepository(context, client(200, bytes)).download(selected) {} }
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("无法读取"))
        val self = File(context.applicationInfo.sourceDir)
        val sameVersion = runCatching { AppUpdateRepository(context).validateApk(self, selected.copy(tag = BuildConfig.VERSION_NAME)) }
        assertTrue(sameVersion.exceptionOrNull()?.message.orEmpty().contains("版本号未递增"))
        val testApk = File(InstrumentationRegistry.getInstrumentation().context.applicationInfo.sourceDir)
        val wrongPackage = runCatching { AppUpdateRepository(context).validateApk(testApk, selected) }
        assertTrue(wrongPackage.exceptionOrNull()?.message.orEmpty().contains("包名不匹配"))
    }
    @Test fun validSignedUpdateDownloadsAndProvidesInstallerUriWhileOtherSignerIsRejected() = runBlocking {
        val dir = context.getExternalFilesDir(null)!!
        val fixture = File(dir, "update-fixture.apk")
        val otherSigner = File(dir, "update-wrong-signer.apk")
        assumeTrue("Pass signed local update fixtures via external app files", fixture.exists() && otherSigner.exists())
        @Suppress("DEPRECATION")
        val archive = context.packageManager.getPackageArchiveInfo(fixture.absolutePath, 0)!!
        @Suppress("DEPRECATION")
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        assumeTrue("The fixture must be newer than the installed app", archive.longVersionCode > installed.longVersionCode)
        val fixtureTag = "v${archive.versionName}"
        val bytes = fixture.readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val selected = AppRelease(fixtureTag, "", "$UPDATE_REPOSITORY/releases/download/$fixtureTag/Clash-arm64.apk", bytes.size.toLong(), hash)
        val repo = AppUpdateRepository(context, client(200, bytes))
        var progress = 0f
        val apk = repo.download(selected) { progress = it }
        assertEquals(1f, progress)
        repo.verifyForInstallation(apk, selected)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Query visibility can hide installer handlers; resolve through the system rather than asserting a package name.
        assertEquals("content", uri.scheme)
        assertEquals("application/vnd.android.package-archive", intent.type)
        val mismatch = runCatching { repo.validateApk(otherSigner, selected) }
        assertTrue(mismatch.exceptionOrNull()?.message.orEmpty().contains("签名不匹配"))
        apk.appendText("changed after download")
        assertTrue(runCatching { repo.verifyForInstallation(apk, selected) }.isFailure)
        apk.delete()
        Unit
    }

}
