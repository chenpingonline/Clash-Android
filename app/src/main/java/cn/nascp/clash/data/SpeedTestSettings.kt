package cn.nascp.clash.data

import java.net.URI

internal object SpeedTestSettings {
    const val CLOUDFLARE = "http://cp.cloudflare.com/generate_204"
    const val GSTATIC = "https://www.gstatic.com/generate_204"

    fun effectiveUrl(saved: String): String = saved.ifBlank { CLOUDFLARE }

    fun preset(url: String): String = when (url) {
        CLOUDFLARE -> "cloudflare"
        GSTATIC -> "gstatic"
        else -> "custom"
    }

    fun validatedUrl(input: String): String {
        val value = input.trim()
        val uri = runCatching { URI(value) }.getOrNull()
        require(uri != null && uri.scheme?.lowercase() in listOf("http", "https") && !uri.host.isNullOrBlank() &&
            (uri.port == -1 || uri.port in 1..65535)) { "请输入有效的 HTTP 或 HTTPS 测试地址" }
        return value
    }
}
