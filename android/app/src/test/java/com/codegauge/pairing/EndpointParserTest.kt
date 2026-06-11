package com.codegauge.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EndpointParserTest {
    @Test
    fun parsesHostAndPort() {
        val endpoint = parseManualEndpoint("192.168.1.20:18768")

        assertEquals(
            CompanionEndpoint(
                name = "192.168.1.20:18768",
                host = "192.168.1.20",
                port = 18768,
            ),
            endpoint,
        )
    }

    @Test
    fun defaultsToPort8765() {
        val endpoint = parseManualEndpoint("192.168.1.20")

        assertEquals("192.168.1.20", endpoint?.host)
        assertEquals(8765, endpoint?.port)
    }

    @Test
    fun acceptsHttpPrefix() {
        val endpoint = parseManualEndpoint("http://192.168.1.20:18768")

        assertEquals("192.168.1.20", endpoint?.host)
        assertEquals(18768, endpoint?.port)
    }

    @Test
    fun rejectsBadPort() {
        assertNull(parseManualEndpoint("192.168.1.20:nope"))
    }
}
