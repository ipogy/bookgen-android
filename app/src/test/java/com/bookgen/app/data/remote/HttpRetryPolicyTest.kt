package com.bookgen.app.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class HttpRetryPolicyTest {

    @Test
    fun respectsRetryAfterHeaderAndBounds() {
        val wait429 = HttpRetryPolicy.computeDelayMs(
            attempt = 0,
            retryAfterSeconds = 7L,
        )
        assertEquals(7_000L, wait429)

        val capped = HttpRetryPolicy.computeDelayMs(
            attempt = 4,
            retryAfterSeconds = 120L,
            maxDelayMs = 30_000L,
        )
        assertEquals(30_000L, capped)
    }

    @Test
    fun retriesTransientHttpAndIoErrorsWithBackoff() = runTest {
        val observedDelays = mutableListOf<Long>()
        var calls = 0

        val result = HttpRetryPolicy.executeWithBackoff(
            maxRetries = 3,
            delayFn = { observedDelays += it },
        ) { attempt ->
            calls += 1
            when (attempt) {
                0 -> throw LlmHttpException(429, "Rate limited", retryAfterSeconds = 2L)
                1 -> throw IOException("Temporary socket reset")
                2 -> throw LlmHttpException(503, "Service unavailable")
                else -> "recovered"
            }
        }

        assertEquals("recovered", result)
        assertEquals(4, calls)
        assertEquals(3, observedDelays.size)
        assertEquals(2_000L, observedDelays[0])
        assertTrue(observedDelays[1] > 0L && observedDelays[2] > 0L)
    }

    @Test
    fun failsFastOn401AndPropagatesCancellation() = runTest {
        var calls = 0
        try {
            HttpRetryPolicy.executeWithBackoff(
                maxRetries = 3,
                delayFn = { fail("Should not delay on HTTP 401") },
            ) {
                calls += 1
                throw LlmHttpException(401, "Invalid API key")
            }
            fail("Expected LlmHttpException")
        } catch (ex: LlmHttpException) {
            assertEquals(401, ex.statusCode)
            assertEquals(1, calls)
        }

        try {
            HttpRetryPolicy.executeWithBackoff(
                maxRetries = 3,
                delayFn = { fail("Should not delay on CancellationException") },
            ) {
                throw CancellationException("Stopped")
            }
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }
    }
}
