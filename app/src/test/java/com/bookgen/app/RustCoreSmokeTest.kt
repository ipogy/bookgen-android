package com.bookgen.app

import com.bookgen.core.uniffi.BookGenException
import com.bookgen.core.uniffi.coreHealthCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RustCoreSmokeTest {
    @Test
    fun rustCoreHealthCheckRoundTripSucceeds() {
        val status = coreHealthCheck("kotlin-jvm-smoke")
        assertEquals("bookgen-core", status.engineName)
        assertEquals("0.1.0", status.crateVersion)
        assertTrue(status.uniffiReady)
        assertEquals("BookGen Rust Core ready: kotlin-jvm-smoke", status.echoNote)
    }

    @Test(expected = BookGenException.InvalidInput::class)
    fun rustCoreHealthCheckMapsInvalidInputError() {
        coreHealthCheck("   ")
    }
}
