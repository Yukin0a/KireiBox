package com.kireibox.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class LoopbackCallbackTest {
    private val redirect = "http://127.0.0.1:54321/callback"

    @Test fun acceptsOnlyMatchingSessionAndExactPath() {
        assertEquals("$redirect?state=expected&code=abc", LoopbackCallback.parse("GET /callback?state=expected&code=abc HTTP/1.1", redirect, "expected"))
        listOf(
            "GET /callback?state=wrong&code=abc HTTP/1.1",
            "GET /callback?state=expected&state=wrong&code=abc HTTP/1.1",
            "GET /favicon.ico HTTP/1.1",
            "POST /callback?state=expected&code=abc HTTP/1.1",
            "GET http://evil.example/callback?state=expected&code=abc HTTP/1.1",
            "GET /callback?state=expected&code=%XY HTTP/1.1",
            "GET /callback?state=expected HTTP/1.1",
        ).forEach { assertNull(it, LoopbackCallback.parse(it, redirect, "expected")) }
        assertNotNull(LoopbackCallback.parse("GET /callback?state=expected&error=access_denied HTTP/1.1", redirect, "expected"))
    }

    @Test fun readsTheRequestLineAndDrainsTheHead() {
        val input = ByteArrayInputStream("GET /callback?state=s&code=c HTTP/1.1\r\nHost: 127.0.0.1\r\nAccept: */*\r\n\r\nNEXT".toByteArray())
        assertEquals("GET /callback?state=s&code=c HTTP/1.1", LoopbackCallback.readRequestLine(input))
        // Everything up to the blank line was consumed, so closing the socket sends no reset.
        assertEquals("NEXT", input.readBytes().decodeToString())
    }

    @Test fun silentOrOversizedConnectionsYieldNoRequest() {
        // A browser preconnect that closes without writing anything.
        assertNull(LoopbackCallback.readRequestLine(ByteArrayInputStream(ByteArray(0))))
        // A line that never ends.
        assertNull(LoopbackCallback.readRequestLine(ByteArrayInputStream("GET /callback".toByteArray())))
        val huge = "GET /callback HTTP/1.1\r\nX: " + "a".repeat(LoopbackCallback.MAX_HEAD_BYTES) + "\r\n\r\n"
        assertNull(LoopbackCallback.readRequestLine(ByteArrayInputStream(huge.toByteArray())))
        // A head cut off after the request line still yields that line; parse() decides whether it is valid.
        assertEquals("GET / HTTP/1.1", LoopbackCallback.readRequestLine(ByteArrayInputStream("GET / HTTP/1.1\nHost: x\n".toByteArray())))
    }
}
