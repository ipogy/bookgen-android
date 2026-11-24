package com.bookgen.app.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException
import kotlin.random.Random

class LlmHttpException(
    val statusCode: Int,
    override val message: String,
    val retryAfterSeconds: Long? = null,
) : IOException(message) {
    val isTransient: Boolean
        get() = statusCode == 429 || statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504
}

class EmptyLlmResponseException(message: String) : IllegalStateException(message)

object HttpRetryPolicy {
    fun computeDelayMs(
        attempt: Int,
        retryAfterSeconds: Long? = null,
        baseDelayMs: Long = 1_500L,
        maxDelayMs: Long = 30_000L,
        jitterRatio: Double = 0.25,
        randomDouble: () -> Double = { Random.nextDouble() },
    ): Long {
        if (retryAfterSeconds != null && retryAfterSeconds > 0L) {
            return (retryAfterSeconds * 1_000L).coerceAtMost(maxDelayMs)
        }
        val expShift = attempt.coerceIn(0, 6)
        val raw = (baseDelayMs * (1L shl expShift)).coerceAtMost(maxDelayMs)
        val jitterWindow = (raw * jitterRatio).toLong()
        val jitter = if (jitterWindow > 0L) {
            ((randomDouble().coerceIn(0.0, 1.0) * 2.0 - 1.0) * jitterWindow).toLong()
        } else {
            0L
        }
        return (raw + jitter).coerceIn(250L, maxDelayMs)
    }

    fun parseRetryAfterSeconds(headerValue: String?): Long? {
        val trimmed = headerValue?.trim() ?: return null
        return trimmed.toLongOrNull()?.takeIf { it > 0L }
    }

    suspend fun <T> executeWithBackoff(
        maxRetries: Int = 3,
        delayFn: suspend (Long) -> Unit = { ms -> delay(ms) },
        block: suspend (attempt: Int) -> T,
    ): T {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt <= maxRetries) {
            try {
                return block(attempt)
            } catch (ce: CancellationException) {
                throw ce
            } catch (http: LlmHttpException) {
                lastError = http
                if (!http.isTransient || attempt >= maxRetries) {
                    throw http
                }
                val waitMs = computeDelayMs(
                    attempt = attempt,
                    retryAfterSeconds = http.retryAfterSeconds,
                )
                delayFn(waitMs)
            } catch (io: IOException) {
                lastError = io
                if (attempt >= maxRetries) {
                    throw io
                }
                delayFn(computeDelayMs(attempt = attempt))
            } catch (empty: EmptyLlmResponseException) {
                lastError = empty
                if (attempt >= maxRetries) {
                    throw empty
                }
                delayFn(computeDelayMs(attempt = attempt, baseDelayMs = 800L))
            }
            attempt += 1
        }
        throw lastError ?: IllegalStateException("LLM リクエストがリトライ上限に達しました")
    }
}
