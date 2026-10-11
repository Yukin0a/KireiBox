package com.kireibox.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {
    @Test
    fun parsesSchemaV1ManifestAndNormalizesCertificateDigest() {
        val manifest =
            UpdateManifestCodec.parse(
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
        val selected =
            UpdateSelectionPolicy.select(
                installedVersionCode = 19,
                ignoredVersionCode = 20,
                releases = listOf(ignored, newer),
            )
        assertTrue(selected is UpdateCheckResult.Available)
        assertEquals(21, (selected as UpdateCheckResult.Available).release.versionCode)
    }

    @Test
    fun highestVersionCodeWinsRegardlessOfReleaseOrder() {
        val selected =
            UpdateSelectionPolicy.select(
                installedVersionCode = 19,
                ignoredVersionCode = 0,
                releases = listOf(release(21), release(20), release(23), release(22)),
            ) as UpdateCheckResult.Available

        assertEquals(23, selected.release.versionCode)
    }

    @Test
    fun semanticVersionOrdersAlphaBetaRcAndStable() {
        val values =
            listOf(
                "0.5.0",
                "0.5.0-rc.1",
                "0.5.0-beta.2",
                "0.5.0-alpha.15",
            ).map { requireNotNull(KireiVersion.parse(it)) }

        assertEquals(values, values.sortedDescending())
        assertEquals("0.5.0-alpha.13", KireiVersion.normalize("v0.5.0-alpha.13"))
    }

    @Test
    fun releaseCacheRoundTripsBareAndManifestReleases() {
        val bare =
            UpdateRelease(
                releaseId = 13,
                tagName = "v0.5.0-alpha.13",
                releaseName = "Alpha 13",
                notes = "notes",
                pageUrl = "https://github.com/Yukin0a/KireiBox/releases/tag/v0.5.0-alpha.13",
                apkUrl = "https://github.com/Yukin0a/KireiBox/releases/download/v0.5.0-alpha.13/app-release.apk",
                apkSize = 12,
                apkAssetName = "app-release.apk",
                fallbackVersionName = "0.5.0-alpha.13",
            )

        assertEquals(bare, UpdateReleaseCacheCodec.decode(UpdateReleaseCacheCodec.encode(bare)))
        val strict = release(22)
        assertEquals(strict, UpdateReleaseCacheCodec.decode(UpdateReleaseCacheCodec.encode(strict)))
    }

    private fun release(versionCode: Int) =
        UpdateRelease(
            releaseId = versionCode.toLong(),
            tagName = "v0.5.0-alpha.${versionCode - 7}",
            releaseName = "KireiBox alpha",
            notes = "notes",
            pageUrl = "https://github.com/Yukin0a/KireiBox/releases/tag/test-$versionCode",
            apkUrl = "https://github.com/Yukin0a/KireiBox/releases/download/test/update.apk",
            apkSize = 1024L,
            manifest =
                UpdateManifest(
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

    @Test
    fun versionParsingToleratesBuildSuffixes() {
        // A strict pattern left suffixed builds (e.g. the -debug APK) unparseable, which silently
        // disabled the update check for them.
        assertEquals(KireiVersion.parse("0.5.0-alpha.17"), KireiVersion.parse("0.5.0-alpha.17-debug"))
        assertEquals(KireiVersion.parse("0.5.0"), KireiVersion.parse("0.5.0-release"))
        assertEquals(KireiVersion.parse("0.5.0"), KireiVersion.parse("v0.5.0+build.7"))
        assertEquals("0.5.0-alpha.17", KireiVersion.normalize("0.5.0-alpha.17-debug"))
    }

    @Test
    fun ignoredVersionNameSuppressesManifestLessRelease() {
        // Releases published without the signed manifest carry no version code, so "稍后更新"
        // must be honoured by name or the prompt would return on the very next check.
        val bare =
            UpdateRelease(
                releaseId = 30,
                tagName = "v0.5.0-alpha.18",
                releaseName = "Alpha 18",
                notes = "",
                pageUrl = "https://example.com",
                apkUrl = "https://example.com/update.apk",
                apkSize = 1024,
                manifest = null,
                apkAssetName = "update.apk",
                fallbackVersionName = "0.5.0-alpha.18",
            )

        assertEquals(
            UpdateCheckResult.UpToDate,
            UpdateSelectionPolicy.select(
                installedVersionCode = 25,
                ignoredVersionCode = 0,
                releases = listOf(bare),
                installedVersionName = "0.5.0-alpha.17",
                ignoredVersionName = "0.5.0-alpha.18",
            ),
        )

        // A different (newer) release still surfaces.
        assertTrue(
            UpdateSelectionPolicy.select(
                installedVersionCode = 25,
                ignoredVersionCode = 0,
                releases = listOf(bare),
                installedVersionName = "0.5.0-alpha.17",
                ignoredVersionName = "0.5.0-alpha.17",
            ) is UpdateCheckResult.Available,
        )
    }
}
