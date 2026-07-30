package com.apps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KireiDiagnosticLogsTest {
    @Test
    fun `export redacts bearer and token values`() {
        val redacted = KireiDiagnosticLogs.redact(
            "Authorization: Bearer secret-value\n\"access_token\":\"abcdef\"\ntoken=xyz",
        )
        assertFalse(redacted.contains("secret-value"))
        assertFalse(redacted.contains("abcdef"))
        assertFalse(redacted.contains("xyz"))
        assertTrue(redacted.contains("<redacted>"))
    }
}
