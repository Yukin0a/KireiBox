package com.kireibox.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInstallerTest {
    @Test
    fun validNewerPackageIsAccepted() {
        val result = UpdatePackagePolicy.validate(
            installedVersionCode = 19,
            currentSignerSha256 = SIGNER,
            deviceSdk = 35,
            deviceAbis = listOf("arm64-v8a"),
            archive = archive(),
            manifest = manifest(),
        )

        assertEquals(UpdatePackageValidation.Valid, result)
    }

    @Test
    fun wrongPackageNameIsRejected() {
        val result = validate(archive = archive().copy(packageName = "example.attacker"))

        assertTrue((result as UpdatePackageValidation.Invalid).message.contains("包名"))
    }

    @Test
    fun nonIncreasingVersionCodeIsRejected() {
        val result = validate(archive = archive().copy(versionCode = 19))

        assertTrue((result as UpdatePackageValidation.Invalid).message.contains("版本"))
    }

    @Test
    fun signerMustMatchManifestAndInstalledApp() {
        val result = validate(archive = archive().copy(signerSha256 = "0".repeat(64)))

        assertTrue((result as UpdatePackageValidation.Invalid).message.contains("签名"))
    }

    @Test
    fun incompatibleSdkOrAbiIsRejected() {
        val sdk = validate(deviceSdk = 28, archive = archive().copy(minSdk = 30))
        val abi = validate(deviceAbis = listOf("x86_64"))

        assertTrue((sdk as UpdatePackageValidation.Invalid).message.contains("Android"))
        assertTrue((abi as UpdatePackageValidation.Invalid).message.contains("ABI"))
    }

    @Test
    fun signedBareApkRequiresMatchingTagAndInstalledSigner() {
        val valid = UpdatePackagePolicy.validateSignedApkFallback(
            installedVersionCode = 19,
            currentSignerSha256 = SIGNER,
            deviceSdk = 35,
            deviceAbis = listOf("arm64-v8a"),
            archive = archive(),
            expectedVersionName = "v0.5.0-alpha.14",
        )
        val wrongTag = UpdatePackagePolicy.validateSignedApkFallback(
            installedVersionCode = 19,
            currentSignerSha256 = SIGNER,
            deviceSdk = 35,
            deviceAbis = listOf("arm64-v8a"),
            archive = archive(),
            expectedVersionName = "0.5.0-alpha.15",
        )

        assertEquals(UpdatePackageValidation.Valid, valid)
        assertTrue((wrongTag as UpdatePackageValidation.Invalid).message.contains("GitHub Release"))
    }

    private fun validate(
        deviceSdk: Int = 35,
        deviceAbis: List<String> = listOf("arm64-v8a"),
        archive: UpdatePackageMetadata = archive(),
    ) = UpdatePackagePolicy.validate(
        installedVersionCode = 19,
        currentSignerSha256 = SIGNER,
        deviceSdk = deviceSdk,
        deviceAbis = deviceAbis,
        archive = archive,
        manifest = manifest(),
    )

    private fun archive() = UpdatePackageMetadata(
        packageName = KIREIBOX_PACKAGE_NAME,
        versionCode = 20,
        versionName = "0.5.0-alpha.14",
        minSdk = 26,
        abis = listOf("arm64-v8a"),
        signerSha256 = SIGNER,
    )

    private fun manifest() = UpdateManifest(
        packageName = KIREIBOX_PACKAGE_NAME,
        versionCode = 20,
        versionName = "0.5.0-alpha.14",
        minSdk = 26,
        abis = listOf("arm64-v8a"),
        apkAsset = "update.apk",
        sha256 = "1".repeat(64),
        signingCertSha256 = SIGNER,
    )

    companion object {
        private const val SIGNER = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"
    }
}
