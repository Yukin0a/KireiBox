package com.kireibox.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

internal data class UpdatePackageMetadata(
    val packageName: String,
    val versionCode: Int,
    val versionName: String = "",
    val minSdk: Int,
    val abis: List<String>,
    val signerSha256: String,
)

internal sealed interface UpdatePackageValidation {
    data object Valid : UpdatePackageValidation
    data class Invalid(val message: String) : UpdatePackageValidation
}

internal object UpdatePackagePolicy {
    fun validate(
        installedVersionCode: Int,
        currentSignerSha256: String,
        deviceSdk: Int,
        deviceAbis: List<String>,
        archive: UpdatePackageMetadata,
        manifest: UpdateManifest,
    ): UpdatePackageValidation {
        fun invalid(message: String) = UpdatePackageValidation.Invalid(message)
        if (archive.packageName != KIREIBOX_PACKAGE_NAME || archive.packageName != manifest.packageName) {
            return invalid("更新 APK 包名不匹配")
        }
        if (archive.versionCode != manifest.versionCode || archive.versionCode <= installedVersionCode) {
            return invalid("更新 APK 版本无效")
        }
        if (KireiVersion.normalize(archive.versionName) != KireiVersion.normalize(manifest.versionName)) {
            return invalid("更新 APK 版本名称不匹配")
        }
        if (archive.minSdk != manifest.minSdk || archive.minSdk > deviceSdk) {
            return invalid("更新 APK 不支持当前 Android 版本")
        }
        val archiveAbis = archive.abis.toSet()
        if (archiveAbis != manifest.abis.toSet() || archiveAbis.intersect(deviceAbis.toSet()).isEmpty()) {
            return invalid("更新 APK ABI 不兼容")
        }
        val archiveSigner = UpdateManifestCodec.normalizeDigest(archive.signerSha256)
        val manifestSigner = UpdateManifestCodec.normalizeDigest(manifest.signingCertSha256)
        val installedSigner = UpdateManifestCodec.normalizeDigest(currentSignerSha256)
        if (archiveSigner != manifestSigner || archiveSigner != installedSigner) {
            return invalid("更新 APK 签名不匹配")
        }
        return UpdatePackageValidation.Valid
    }

    fun validateSignedApkFallback(
        installedVersionCode: Int,
        currentSignerSha256: String,
        deviceSdk: Int,
        deviceAbis: List<String>,
        archive: UpdatePackageMetadata,
        expectedVersionName: String,
    ): UpdatePackageValidation {
        fun invalid(message: String) = UpdatePackageValidation.Invalid(message)
        if (archive.packageName != KIREIBOX_PACKAGE_NAME) return invalid("更新 APK 包名不匹配")
        if (archive.versionCode <= installedVersionCode) return invalid("更新 APK 版本无效")
        if (KireiVersion.normalize(archive.versionName) != KireiVersion.normalize(expectedVersionName)) {
            return invalid("更新 APK 版本名称与 GitHub Release 不匹配")
        }
        if (archive.minSdk > deviceSdk) return invalid("更新 APK 不支持当前 Android 版本")
        if (archive.abis.toSet().intersect(deviceAbis.toSet()).isEmpty()) {
            return invalid("更新 APK ABI 不兼容")
        }
        val archiveSigner = UpdateManifestCodec.normalizeDigest(archive.signerSha256)
        val installedSigner = UpdateManifestCodec.normalizeDigest(currentSignerSha256)
        if (archiveSigner != installedSigner) return invalid("更新 APK 签名不匹配")
        return UpdatePackageValidation.Valid
    }
}

internal class UpdateInstaller(private val context: Context) {
    private val packageManager: PackageManager get() = context.packageManager

    fun validate(file: File, release: UpdateRelease): UpdatePackageValidation {
        val archive = inspectArchive(file)
            ?: return UpdatePackageValidation.Invalid("无法读取更新 APK")
        val installed = installedPackageMetadata()
            ?: return UpdatePackageValidation.Invalid("无法读取当前应用签名")
        return release.manifest?.let { manifest ->
            UpdatePackagePolicy.validate(
                installedVersionCode = installed.versionCode,
                currentSignerSha256 = installed.signerSha256,
                deviceSdk = Build.VERSION.SDK_INT,
                deviceAbis = Build.SUPPORTED_ABIS.toList(),
                archive = archive,
                manifest = manifest,
            )
        } ?: UpdatePackagePolicy.validateSignedApkFallback(
            installedVersionCode = installed.versionCode,
            currentSignerSha256 = installed.signerSha256,
            deviceSdk = Build.VERSION.SDK_INT,
            deviceAbis = Build.SUPPORTED_ABIS.toList(),
            archive = archive,
            expectedVersionName = release.versionName,
        )
    }

    fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
        data = Uri.parse("package:${context.packageName}")
    }

    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            clipData = ClipData.newRawUri("KireiBox update", uri)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    @Suppress("DEPRECATION")
    private fun inspectArchive(file: File): UpdatePackageMetadata? {
        if (!file.isFile) return null
        val info = packageManager.getPackageArchiveInfo(file.absolutePath, signingFlags()) ?: return null
        return packageMetadata(info, apkAbis(file))
    }

    @Suppress("DEPRECATION")
    private fun installedPackageMetadata(): UpdatePackageMetadata? {
        val info = packageManager.getPackageInfo(context.packageName, signingFlags())
        return packageMetadata(info, Build.SUPPORTED_ABIS.toList())
    }

    @Suppress("DEPRECATION")
    private fun packageMetadata(info: PackageInfo, abis: List<String>): UpdatePackageMetadata? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            info.signatures.orEmpty()
        }
        if (signatures.size != 1) return null
        val signer = MessageDigest.getInstance("SHA-256")
            .digest(signatures.single().toByteArray())
            .joinToString("") { "%02x".format(it) }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            info.versionCode
        }
        return UpdatePackageMetadata(
            packageName = info.packageName,
            versionCode = versionCode,
            versionName = info.versionName.orEmpty(),
            minSdk = info.applicationInfo?.minSdkVersion ?: 1,
            abis = abis.distinct(),
            signerSha256 = signer,
        )
    }

    private fun apkAbis(file: File): List<String> = runCatching {
        ZipFile(file).use { zip ->
            zip.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("lib/") && it.endsWith(".so") }
                .mapNotNull { it.split('/').getOrNull(1) }
                .distinct()
                .toList()
        }
    }.getOrDefault(emptyList())

    @Suppress("DEPRECATION")
    private fun signingFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }
}
