package cn.nascp.clash.data

import org.junit.Assert.*
import org.junit.Test

class SubscriptionHttpTest {
    @Test fun forbiddenDoesNotBlameRedirects() {
        val message = subscriptionHttpError(403)
        assertTrue(message.contains("HTTP 403"))
        assertTrue(message.contains("服务器拒绝访问"))
        assertFalse(message.contains("最终"))
    }

    @Test fun errorsNeverAskUsersToResolveRedirects() {
        for (status in 300..599) {
            assertFalse("HTTP $status", subscriptionHttpError(status).contains("最终"))
        }
    }

    @Test fun actionableHttpFailuresHaveDistinctMessages() {
        for ((status, text) in mapOf(401 to "鉴权失败", 404 to "不存在", 410 to "已失效", 429 to "过于频繁", 503 to "暂时异常")) {
            assertTrue(subscriptionHttpError(status).contains(text))
        }
    }

    @Test fun userAgentsRejectHeaderInjectionAndInvalidInput() {
        for (agent in listOf("", " ", "test\r\nAuthorization: secret", "客户端", "x".repeat(513), "abc\tdef")) {
            assertTrue(agent, runCatching { subscriptionUserAgent(agent) }.isFailure)
        }
        assertEquals("MyClient/1.0", subscriptionUserAgent(" MyClient/1.0 "))
        for(agent in listOf(DEFAULT_SUBSCRIPTION_UA,CFA_SUBSCRIPTION_UA,CMFA_SUBSCRIPTION_UA))
            assertEquals(agent,subscriptionUserAgent(agent))
    }

    @Test fun responseSummariesNeverReflectCredentialsOrArbitraryServerContent() {
        val body = "<html>User-Agent forbidden https://user:pass@private.example/path/secret?token=secret Authorization: Bearer secret Cookie: secret</html>"
        val summary = subscriptionResponseSummary(body)
        assertTrue(summary.contains("客户端身份受限"))
        for (secret in listOf("secret", "private.example", "Bearer", "Cookie", "user:pass", "<html>")) assertFalse(summary.contains(secret))
        assertTrue(subscriptionResponseSummary("unknown sensitive payload").contains("原文已隐藏"))
        assertTrue(subscriptionResponseSummary("x".repeat(4096)+"token expired").contains("原文已隐藏"))
        assertTrue(subscriptionResponseSummary("token expired secret").contains("订阅或令牌过期"))
        assertTrue(subscriptionResponseSummary("cf-chl- secret").contains("网关挑战"))
        assertTrue(subscriptionResponseSummary("too many requests secret").contains("频率受限"))
    }

    @Test fun networkErrorsNeverReflectUrls() {
        val message=subscriptionNetworkError(java.net.UnknownHostException("private.example?token=secret"))
        assertTrue(message.contains("域名解析失败"))
        assertFalse(message.contains("secret"));assertFalse(message.contains("private.example"))
    }

    @Test fun diagnosticCandidatesDeduplicateWithoutLosingCurrentLabel() {
        val candidates=subscriptionProbeCandidates(CFA_SUBSCRIPTION_UA)
        assertEquals(2,candidates.size)
        assertEquals("当前 / CFA" to CFA_SUBSCRIPTION_UA,candidates.first())
        assertEquals(listOf("Custom/1",CFA_SUBSCRIPTION_UA,CMFA_SUBSCRIPTION_UA),subscriptionProbeCandidates(" Custom/1 ").map{it.second})
        assertTrue(runCatching{subscriptionProbeCandidates("bad\nagent")}.isFailure)
    }

    @Test fun onlyTransientFailuresAreEligibleForRetry() {
        for (status in listOf(401,403,404,410,429,500)) assertFalse(retryableSubscriptionFailure(SubscriptionHttpException(status,0,"")))
        for (status in listOf(502,503,504)) assertTrue(retryableSubscriptionFailure(SubscriptionHttpException(status,0,"")))
        assertTrue(retryableSubscriptionFailure(SubscriptionNetworkException(java.net.SocketTimeoutException())))
        assertFalse(retryableSubscriptionFailure(SubscriptionNetworkException(javax.net.ssl.SSLException("certificate"))))
        assertFalse(retryableSubscriptionFailure(IllegalArgumentException("invalid YAML")))
        assertFalse(retryableSubscriptionFailure(SubscriptionDownloadTimeoutException()))
        assertEquals(60_000L,SUBSCRIPTION_DOWNLOAD_TIMEOUT_MILLIS)
    }
}
