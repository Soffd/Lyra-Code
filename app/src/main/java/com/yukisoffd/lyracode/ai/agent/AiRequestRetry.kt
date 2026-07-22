package com.yukisoffd.lyracode.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

internal const val MODEL_REQUEST_MAX_RETRIES = 5
internal const val MODEL_REQUEST_RETRY_DELAY_MS = 5_000L

internal class RetryableModelHttpException(
    val statusCode: Int,
    message: String,
) : IOException(message)

internal fun isRetryableModelHttpStatus(statusCode: Int): Boolean =
    statusCode in 500..599 || statusCode in setOf(402, 408, 409, 425, 429)

internal fun isRetryableModelFailure(error: Throwable): Boolean {
    val causes = generateSequence(error as Throwable?) { it.cause }.toList()
    if (causes.any { it is SSLHandshakeException || it is SSLPeerUnverifiedException }) return false
    return causes.any { it is RetryableModelHttpException || it is IOException }
}

internal suspend fun <T> executeModelRequestWithRetry(
    maxRetries: Int = MODEL_REQUEST_MAX_RETRIES,
    retryDelayMillis: Long = MODEL_REQUEST_RETRY_DELAY_MS,
    onRetry: suspend (retryNumber: Int, maxRetries: Int, error: Throwable) -> Unit = { _, _, _ -> },
    request: suspend () -> T,
): T {
    require(maxRetries >= 0)
    require(retryDelayMillis >= 0L)
    var retries = 0
    while (true) {
        try {
            return request()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (!isRetryableModelFailure(error) || retries >= maxRetries) throw error
            retries++
            onRetry(retries, maxRetries, error)
            delay(retryDelayMillis)
        }
    }
}
