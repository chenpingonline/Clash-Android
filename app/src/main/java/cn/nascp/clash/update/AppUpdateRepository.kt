package cn.nascp.clash.update

import android.content.Context
import android.content.pm.PackageManager
import cn.nascp.clash.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal const val UPDATE_REPOSITORY = "https://github.com/chenpingonline/Clash-Android"
internal data class AppRelease(val tag: String, val notes: String, val assetUrl: String, val size: Long, val sha256: String)

internal class AppUpdateRepository(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(10, TimeUnit.MINUTES).followSslRedirects(false).build(),
) {
    private val activeCall = AtomicReference<Call?>()
    fun cancel() { activeCall.get()?.cancel() }

    suspend fun check(): AppRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.github.com/repos/chenpingonline/Clash-Android/releases?per_page=100")
            .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Clash-Android/${BuildConfig.VERSION_NAME}").build()
        val call = client.newCall(request)
        activeCall.set(call)
        try {
            call.execute().use { response ->
                when (response.code) {
                    403, 429 -> error("更新检查受到 GitHub 限流，请稍后重试")
                    404 -> error("更新仓库暂不可用，请稍后重试")
                }
                check(response.isSuccessful) { "更新检查失败，请稍后重试" }
                val body = response.body ?: error("更新信息为空")
                val text = body.byteStream().use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        check(output.size() + count <= 2 * 1024 * 1024) { "更新信息过大" }
                        output.write(buffer, 0, count)
                    }
                    output.toString("UTF-8")
                }
                parseReleases(JSONArray(text), BuildConfig.VERSION_NAME)
            }
        } finally { activeCall.compareAndSet(call, null) }
    }

    suspend fun download(release: AppRelease, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        check(release.assetUrl.startsWith("$UPDATE_REPOSITORY/releases/download/")) { "更新下载地址无效" }
        check(release.sha256.matches(Regex("[0-9a-f]{64}"))) { "此版本缺少 SHA-256 校验信息" }
        check(release.size in 1..MAX_APK_SIZE) { "更新安装包大小无效" }
        val dir = File(context.cacheDir, "app-updates").apply { mkdirs() }
        val partial = File(dir, "update.apk.part")
        val target = File(dir, "update.apk")
        target.delete()
        val call = client.newCall(Request.Builder().url(release.assetUrl).build())
        activeCall.set(call)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            call.execute().use { response ->
                check(response.isSuccessful) { "安装包下载失败，请重试" }
                val body = response.body ?: error("安装包为空")
                check(body.contentLength() == -1L || body.contentLength() == release.size) { "安装包大小不符" }
                body.byteStream().use { input -> partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    var lastPercent = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        total += count
                        check(total <= release.size && total <= MAX_APK_SIZE) { "安装包大小不符" }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        val percent = (total * 100 / release.size).toInt()
                        if (percent != lastPercent) { progress(total.toFloat() / release.size); lastPercent = percent }
                    }
                    check(total == release.size) { "安装包下载不完整，请重试" }
                } }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            check(actual == release.sha256) { "安装包 SHA-256 校验失败，请重新下载" }
            validateApk(partial, release)
            check(partial.renameTo(target)) { "无法保存安装包，请检查存储空间" }
            target
        } finally { partial.delete(); activeCall.compareAndSet(call, null) }
    }

    suspend fun verifyForInstallation(file: File, release: AppRelease) = withContext(Dispatchers.IO) {
        check(file.length() == release.size) { "安装包大小不符" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        check(digest.digest().joinToString("") { "%02x".format(it) } == release.sha256) { "安装包 SHA-256 校验失败，请重新下载" }
        validateApk(file, release)
    }

    @Suppress("DEPRECATION")
    fun validateApk(file: File, release: AppRelease) {
        val pm = context.packageManager
        val candidate = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: error("无法读取更新安装包")
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        check(candidate.packageName == context.packageName) { "安装包包名不匹配" }
        check(candidate.longVersionCode > installed.longVersionCode) { "安装包版本号未递增，无法升级" }
        check(candidate.versionName == release.tag.removePrefix("v")) { "安装包版本与发布信息不符" }
        val candidateSigners = candidate.signingInfo?.apkContentsSigners.orEmpty().map { it.toCharsString() }.toSet()
        val installedSigners = installed.signingInfo?.apkContentsSigners.orEmpty().map { it.toCharsString() }.toSet()
        check(candidateSigners.isNotEmpty() && candidateSigners == installedSigners) { "安装包签名不匹配，无法覆盖升级" }
    }

    companion object {
        private const val MAX_APK_SIZE = 300L * 1024 * 1024
        internal fun parseReleases(releases: JSONArray, currentName: String): AppRelease? {
            val current = ReleaseVersion.parse(currentName) ?: error("当前版本格式无效")
            val candidates = (0 until releases.length()).mapNotNull { releases.optJSONObject(it) }
                .filter { !it.optBoolean("draft") && (current.suffix.isNotEmpty() || !it.optBoolean("prerelease")) }
                .mapNotNull { release -> ReleaseVersion.parse(release.optString("tag_name"))?.let { it to release } }
                .filter { (version, _) -> version > current && (current.suffix.isNotEmpty() || version.suffix.isEmpty()) }
                .sortedByDescending { it.first }
            if (candidates.isEmpty()) return null
            val release = candidates.first().second
            val assets = release.optJSONArray("assets") ?: JSONArray()
            val apks = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }.filter {
                val name = it.optString("name").lowercase()
                name.endsWith(".apk") && ("arm64" in name || "universal" in name) && it.optString("state") == "uploaded"
            }
            check(apks.size == 1) { "新版本尚未提供唯一的 ARM64 安装包，请稍后重试" }
            val asset = apks.single()
            val sha = asset.optString("digest").removePrefix("sha256:").lowercase()
            check(sha.matches(Regex("[0-9a-f]{64}"))) { "此版本缺少 SHA-256 校验信息" }
            val url = asset.optString("browser_download_url")
            check(url.startsWith("$UPDATE_REPOSITORY/releases/download/")) { "更新下载地址无效" }
            val size = asset.optLong("size")
            check(size in 1..MAX_APK_SIZE) { "更新安装包大小无效" }
            return AppRelease(release.getString("tag_name"), release.optString("body").take(12000), url, size, sha)
        }
    }
}
