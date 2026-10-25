package cn.nascp.clash.data

import java.net.HttpURLConnection
import java.util.Locale
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val SUBSCRIPTION_DOWNLOAD_TIMEOUT_MILLIS = 60_000L

// Same subscription identity as the pinned Clash-for-fnOS backend.
const val DEFAULT_SUBSCRIPTION_UA = "clash-verge/v2.4.3"
const val CFA_SUBSCRIPTION_UA = "ClashForAndroid/2.5.12"
const val CMFA_SUBSCRIPTION_UA = "ClashMetaForAndroid/2.11.36"

fun subscriptionUserAgent(value: String): String {
    require(value.length in 1..512 && value.all { it.code in 32..126 } && value.isNotBlank()) {
        "User-Agent 需为1–512个可打印英文字符，不能包含换行"
    }
    return value.trim()
}

internal class SubscriptionHttpException(val status: Int, val redirects: Int, val summary: String) :
    IllegalArgumentException("${subscriptionHttpError(status)}\n重定向：${redirects}次\n$summary")

/** Only fixed categories leave this function; never retain arbitrary server text or reflected secrets. */
internal fun subscriptionResponseSummary(body: String): String {
    val text = body.take(4096).lowercase(Locale.ROOT)
    val category = when {
        listOf("captcha", "cf-chl-", "challenge-platform", "人机验证").any { it in text } -> "人机验证或网关挑战"
        ("user-agent" in text || "user agent" in text || "客户端" in text) &&
            listOf("unsupported", "not allowed", "forbidden", "不支持", "禁止").any { it in text } -> "客户端身份受限"
        listOf("token expired", "subscription expired", "订阅已过期", "令牌已过期").any { it in text } -> "订阅或令牌过期"
        listOf("invalid token", "unauthorized", "无效令牌", "鉴权失败").any { it in text } -> "鉴权失败"
        listOf("rate limit", "too many requests", "请求过于频繁").any { it in text } -> "请求频率受限"
        listOf("access denied", "permission denied", "forbidden", "拒绝访问", "权限不足").any { it in text } -> "访问被拒绝"
        else -> null
    }
    return if (category != null) "服务端响应摘要：$category（按关键词识别，未核实）"
        else "服务端响应摘要：未识别到可安全展示的原因，原文已隐藏"
}

private fun errorSummary(connection: HttpURLConnection): String {
    val body = runCatching {
        connection.errorStream?.use { input ->
            val buffer = ByteArray(4096)
            var size = 0
            while (size < buffer.size) {
                val count = input.read(buffer, size, buffer.size - size)
                if (count < 0) break
                size += count
            }
            String(buffer, 0, size, Charsets.UTF_8)
        }.orEmpty()
    }.getOrDefault("")
    return subscriptionResponseSummary(body)
}

private data class SubscriptionResponse(val connection: HttpURLConnection, val redirects: Int)

internal class SubscriptionNetworkException(error: java.io.IOException) : IllegalArgumentException(subscriptionNetworkError(error)) {
    val retryable = error !is javax.net.ssl.SSLException && error !is java.net.ProtocolException
}

internal class SubscriptionDownloadTimeoutException : IllegalArgumentException("订阅下载超时（整体上限60秒），请检查网络后重试")

internal fun retryableSubscriptionFailure(error: Exception): Boolean =
    (error is SubscriptionHttpException && error.status in setOf(502, 503, 504)) ||
        (error is SubscriptionNetworkException && error.retryable)

private fun openSubscriptionResponse(
    address: String,
    userAgent: String,
    inspectOnly: Boolean = false,
    onConnection: (HttpURLConnection) -> Unit = {},
): SubscriptionResponse {
    val agent = subscriptionUserAgent(userAgent)
    var url = requireNotNull(address.trim().toHttpUrlOrNull()) { "订阅地址格式无效" }
    val visited = mutableSetOf<String>()
    var redirects = 0
    while (true) {
        require(url.isHttps) { "订阅地址及重定向目标需要使用HTTPS" }
        url = url.newBuilder().fragment(null).build()
        require(visited.add(url.toString())) { "订阅重定向出现循环" }
        val connection = url.toUrl().openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", agent)
        try {
            onConnection(connection)
            val status = connection.responseCode
            if (status !in setOf(301, 302, 303, 307, 308)) {
                if (!inspectOnly && status !in 200..299) throw SubscriptionHttpException(status, redirects, errorSummary(connection))
                return SubscriptionResponse(connection, redirects)
            }
            require(redirects < 5) { "订阅重定向次数超过上限（5次）" }
            val location = connection.getHeaderField("Location")?.trim()
            require(!location.isNullOrEmpty()) { "订阅重定向缺少目标地址" }
            url = requireNotNull(url.resolve(location)) { "订阅重定向目标地址无效" }
            redirects++
        } catch (error: Exception) {
            connection.disconnect()
            // Transport exceptions can embed URLs, query credentials and redirect destinations.
            if (error is java.io.IOException) throw SubscriptionNetworkException(error)
            throw error
        }
        connection.disconnect()
    }
}

internal fun subscriptionNetworkError(error: java.io.IOException): String = when (error) {
    is java.net.SocketTimeoutException -> "订阅下载超时，请检查网络后重试"
    is java.net.UnknownHostException -> "订阅服务器域名解析失败，请检查网络与DNS"
    is javax.net.ssl.SSLException -> "订阅HTTPS连接失败，请检查服务器证书与设备时间"
    else -> "订阅网络请求失败，请检查网络连接"
}

internal fun subscriptionHttpError(status: Int): String {
    val detail = when (status) {
        401 -> "订阅鉴权失败，请检查订阅链接是否完整或已失效"
        403 -> "服务器拒绝访问，请检查订阅权限、有效期或尝试切换User-Agent"
        404, 410 -> "订阅地址不存在或已失效，请从服务商重新获取链接"
        429 -> "请求过于频繁，请稍后重试"
        in 500..599 -> "订阅服务器暂时异常，请稍后重试"
        else -> "订阅服务器返回异常响应"
    }
    return "下载失败：HTTP $status；$detail"
}


data class SubscriptionProbeResult(
    val label: String,
    val userAgent: String,
    val status: Int?,
    val elapsedMillis: Long,
    val redirects: Int?,
    val error: String? = null,
) {
    val successful: Boolean get() = status != null && status in 200..299 && error == null
}

internal fun subscriptionProbeCandidates(current: String): List<Pair<String, String>> {
    val agent = subscriptionUserAgent(current)
    return listOf("当前" to agent, "默认" to DEFAULT_SUBSCRIPTION_UA)
        .groupBy({ it.second }, { it.first })
        .map { (value, labels) -> labels.joinToString(" / ") to value }
}

/** Own the connection until the entire operation completes; cancellation also closes a slow body. */
private suspend fun <T> subscriptionRequest(
    address: String,
    agent: String,
    inspectOnly: Boolean = false,
    block: (HttpURLConnection, Int) -> T,
): T = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
    val active = java.util.concurrent.atomic.AtomicReference<HttpURLConnection?>()
    continuation.invokeOnCancellation { active.getAndSet(null)?.disconnect() }
    kotlinx.coroutines.Dispatchers.IO.dispatch(continuation.context) {
        try {
            val response = openSubscriptionResponse(address, agent, inspectOnly) { connection ->
                active.set(connection)
                if (!continuation.isActive) {
                    active.getAndSet(null)?.disconnect()
                    throw kotlinx.coroutines.CancellationException("订阅请求已取消")
                }
            }
            val result = block(response.connection, response.redirects)
            continuation.resumeWith(Result.success(result))
        } catch (error: Exception) {
            val safe = if (error is java.io.IOException) SubscriptionNetworkException(error) else error
            continuation.resumeWith(Result.failure(safe))
        } finally {
            active.getAndSet(null)?.disconnect()
        }
    }
}

internal data class SubscriptionContent(val yaml: String, val userInfo: String?)

/** One 60-second budget covers all redirects, headers, body reads, retry and backoff. */
internal suspend fun fetchSubscription(address: String, userAgent: String): SubscriptionContent {
    try {
        return kotlinx.coroutines.withTimeout(SUBSCRIPTION_DOWNLOAD_TIMEOUT_MILLIS) {
            var attempt = 0
            while (true) {
                try {
                    return@withTimeout subscriptionRequest(address, userAgent) { connection, _ ->
                        val limit = 8 * 1024 * 1024
                        val expected = connection.contentLengthLong
                        require(expected <= limit) { "订阅超过8 MiB" }
                        val bytes = connection.inputStream.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                require(output.size() + n <= limit) { "订阅超过8 MiB" }
                                output.write(buffer, 0, n)
                            }
                            output.toByteArray()
                        }
                        if (expected >= 0 && bytes.size.toLong() != expected) throw java.io.EOFException("订阅响应不完整")
                        SubscriptionContent(String(bytes, Charsets.UTF_8), connection.getHeaderField("subscription-userinfo"))
                    }
                } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                catch (error: Exception) {
                    if (attempt >= 1 || !retryableSubscriptionFailure(error)) throw error
                    attempt++
                    kotlinx.coroutines.delay(500)
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("订阅请求未完成")
        }
    } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
        // A caller's cancellation/shorter timeout must retain its cancellation semantics.
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        throw SubscriptionDownloadTimeoutException()
    }
}

/** GET through the same transport, stopping at final headers; diagnostics never retry. */
private suspend fun probeSubscription(address: String, label: String, agent: String): SubscriptionProbeResult {
    val start = System.nanoTime()
    fun elapsed() = ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(0)
    return try {
        subscriptionRequest(address, agent, inspectOnly = true) { connection, redirects ->
            SubscriptionProbeResult(label, agent, connection.responseCode, elapsed(), redirects)
        }
    } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
    catch (error: Exception) {
        val detail = if (error is IllegalArgumentException) error.message else "订阅连接诊断失败"
        SubscriptionProbeResult(label, agent, null, elapsed(), null, detail)
    }
}

suspend fun diagnoseSubscription(
    address: String,
    currentUserAgent: String,
    onResult: (SubscriptionProbeResult) -> Unit,
) {
    val url = requireNotNull(address.trim().toHttpUrlOrNull()) { "订阅地址格式无效" }
    require(url.isHttps) { "订阅地址需要使用HTTPS" }
    for ((label, agent) in subscriptionProbeCandidates(currentUserAgent)) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val start = System.nanoTime()
        val result = try {
            kotlinx.coroutines.withTimeout(30_000) { probeSubscription(address, label, agent) }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            SubscriptionProbeResult(label, agent, null, (System.nanoTime() - start) / 1_000_000L, null, "诊断超时（30秒）")
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        onResult(result)
    }
}
