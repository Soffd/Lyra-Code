package com.yukisoffd.lyracode.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

class AiRequestRetryTest {
    @Test
    fun classifiesTransientHttpAndNetworkFailures() {
        assertTrue(isRetryableModelHttpStatus(402))
        assertTrue(isRetryableModelHttpStatus(429))
        assertTrue(isRetryableModelHttpStatus(502))
        assertFalse(isRetryableModelHttpStatus(400))
        assertFalse(isRetryableModelHttpStatus(401))
        assertTrue(isRetryableModelFailure(SocketTimeoutException("timeout")))
        assertFalse(isRetryableModelFailure(SSLHandshakeException("bad certificate")))
    }

    @Test
    fun retriesFiveTimesThenThrowsTheLastError() = runBlocking {
        var requestCount = 0
        val retries = mutableListOf<Int>()

        val result = runCatching {
            executeModelRequestWithRetry(
                retryDelayMillis = 0L,
                onRetry = { retryNumber, maxRetries, _ ->
                    assertEquals(MODEL_REQUEST_MAX_RETRIES, maxRetries)
                    retries += retryNumber
                },
            ) {
                requestCount++
                throw RetryableModelHttpException(502, "failure $requestCount")
            }
        }

        assertTrue(result.isFailure)
        assertEquals(MODEL_REQUEST_MAX_RETRIES + 1, requestCount)
        assertEquals((1..MODEL_REQUEST_MAX_RETRIES).toList(), retries)
        assertEquals("failure 6", result.exceptionOrNull()?.message)
    }

    @Test
    fun returnsSuccessfulRetryResult() = runBlocking {
        var requestCount = 0
        val result = executeModelRequestWithRetry(retryDelayMillis = 0L) {
            requestCount++
            if (requestCount < 3) throw RetryableModelHttpException(429, "busy")
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(3, requestCount)
    }
}
