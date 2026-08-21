package com.kireibox.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {
    @Test
    fun parsesSchemaV1ManifestAndNormalizesCertificateDigest() {
        val manifest = UpdateManifestCodec.parse(
            """{
              "schemaVersion": 1,
              "packageName": "com.kireibox.app",
              "versionCode": 20,
              "versionName": "0.5.0-alpha.14",
              "minSdk": 26,
              "abis": ["arm64-v8a"],
              "apkAsset": "KireiBox-0.5.0-alpha.14-v20-release-arm64-v8a.apk",
              "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
              "signingCertSha256": "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"
            }""",
        )

        assertEquals(20, manifest.versionCode)
        assertEquals(listOf("arm64-v8a"), manifest.abis)
        assertEquals(
            "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899",
            manifest.signingCertSha256,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsManifestWithWrongPackageName() {
        UpdateManifestCodec.parse(
            """{
              "schemaVersion": 1,
              "packageName": "example.attacker",
              "versionCode": 20,
              "versionName": "0.5.0-alpha.14",
              "minSdk": 26,
              "abis": ["arm64-v8a"],
              "apkAsset": "update.apk",
              "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
              "signingCertSha256": "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"
            }""",
        )
    }

    @Test
    fun ignoredVersionIsSuppressedButHigherVersionIsAvailable() {
        val ignored = release(versionCode = 20)
        val newer = release(versionCode = 21)

        assertEquals(
            UpdateCheckResult.UpToDate,
            UpdateSelectionPolicy.select(
                installedVersionCode = 19,
                ignoredVersionCode = 20,
                releases = listOf(ignored),
            ),
        )
        val selected = UpdateSelectionPolicy.select(
            installedVersionCode = 19,
            ignoredVersionCode = 20,
            releases = listOf(ignored, newer),
        )
        assertTrue(selected is UpdateCheckResult.Available)
        assertEquals(21, (selected as UpdateCheckResult.Available).release.manifest.versionCode)
    }

    @Test
    fun highestVersionCodeWinsRegardlessOfReleaseOrder() {
        val selected = UpdateSelectionPolicy.select(
            installedVersionCode = 19,
            ignoredVersionCode = 0,
            releases = listOf(release(21), release(20), release(23), release(22)),
        ) as UpdateCheckResult.Available

        assertEquals(23, selected.release.manifest.versionCode)
    }

    private fun release(versionCode: Int) = UpdateRelease(
        releaseId = versionCode.toLong(),
        tagName = "v0.5.0-alpha.${versionCode - 7}",
        releaseName = "KireiBox alpha",
        notes = "notes",
        pageUrl = "https://github.com/Yukin0a/KireiBox/releases/tag/test-$versionCode",
        apkUrl = "https://github.com/Yukin0a/KireiBox/releases/download/test/update.apk",
        apkSize = 1024L,
        manifest = UpdateManifest(
            packageName = "com.kireibox.app",
            versionCode = versionCode,
            versionName = "0.5.0-alpha.${versionCode - 7}",
            minSdk = 26,
            abis = listOf("arm64-v8a"),
            apkAsset = "update.apk",
            sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            signingCertSha256 = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899",
        ),
    )
}
