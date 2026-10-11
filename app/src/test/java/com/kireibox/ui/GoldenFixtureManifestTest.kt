package com.kireibox.ui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

class GoldenFixtureManifestTest {
    private fun resource(name: String): ByteArray {
        val stream: InputStream = requireNotNull(javaClass.getResourceAsStream(name)) { "missing $name" }
        return stream.use { input ->
            val out = ByteArrayOutputStream()
            input.copyTo(out)
            out.toByteArray()
        }
    }

    private fun pngDimensions(bytes: ByteArray): Pair<Int, Int> {
        assertTrue(bytes.size >= 24)
        assertTrue(bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte())
        val width =
            ((bytes[16].toInt() and 0xFF) shl 24) or ((bytes[17].toInt() and 0xFF) shl 16) or
                ((bytes[18].toInt() and 0xFF) shl 8) or (bytes[19].toInt() and 0xFF)
        val height =
            ((bytes[20].toInt() and 0xFF) shl 24) or ((bytes[21].toInt() and 0xFF) shl 16) or
                ((bytes[22].toInt() and 0xFF) shl 8) or (bytes[23].toInt() and 0xFF)
        return width to height
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun goldenPortraitMatchesManifestAndSpec() {
        val bytes = resource("/visual/home/reference-portrait.png")
        assertEquals("d7b5ff7ae3fca5af691b6e89074fc7bb5f8cd9eafedcc8bf397fa0fe3e5bd509", sha256(bytes))
        assertEquals(852 to 1846, pngDimensions(bytes))
    }

    @Test
    fun goldenLandscapeMatchesManifestAndSpec() {
        val bytes = resource("/visual/home/reference-landscape.png")
        assertEquals("b14067f13b0e1e0ede61d28f5b5b38e7b24f091cc1add97a94f0d847b82a00f8", sha256(bytes))
        assertEquals(1672 to 941, pngDimensions(bytes))
    }

    @Test
    fun manifestPolicyContractIsR1() {
        val raw = resource("/visual/home/reference-manifest.json").toString(Charsets.UTF_8)
        val root = JSONObject(raw)
        assertEquals(2, root.getInt("version"))
        val policy = root.getJSONObject("policy")
        assertTrue(policy.getBoolean("statusBarHiddenPortrait"))
        assertTrue(policy.getBoolean("statusBarHiddenLandscape"))
        assertTrue(policy.getBoolean("navigationBarTransparentEdgeToEdge"))
        assertTrue(policy.getBoolean("navigationBarNeverHidden"))
        assertTrue(policy.getBoolean("dockAboveNavigationBar"))
        assertTrue(policy.getBoolean("cutoutShortEdges"))
    }
}
