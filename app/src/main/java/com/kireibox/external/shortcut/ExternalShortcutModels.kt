package com.kireibox.external.shortcut

import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import java.nio.charset.StandardCharsets

enum class ShortcutServiceStatus {
    NOT_INSTALLED,
    NOT_RUNNING,
    PERMISSION_REQUIRED,
    PERMISSION_DENIED,
    UNSUPPORTED_VERSION,
    UNSUPPORTED_IDENTITY,
    BINDING,
    READY,
    SYSTEM_PERMISSION_MISSING,
    CONNECTION_LOST,
}

object ShortcutServiceStatusPolicy {
    fun resolve(
        installed: Boolean,
        running: Boolean,
        supportedVersion: Boolean,
        permissionGranted: Boolean,
        rationale: Boolean,
        uid: Int,
        binding: Boolean,
        connectionLost: Boolean,
    ): ShortcutServiceStatus =
        when {
            !installed -> ShortcutServiceStatus.NOT_INSTALLED
            !running -> ShortcutServiceStatus.NOT_RUNNING
            !supportedVersion -> ShortcutServiceStatus.UNSUPPORTED_VERSION
            binding -> ShortcutServiceStatus.BINDING
            connectionLost -> ShortcutServiceStatus.CONNECTION_LOST
            !permissionGranted && rationale -> ShortcutServiceStatus.PERMISSION_DENIED
            !permissionGranted -> ShortcutServiceStatus.PERMISSION_REQUIRED
            uid != 2000 -> ShortcutServiceStatus.UNSUPPORTED_IDENTITY
            else -> ShortcutServiceStatus.READY
        }
}

data class ExternalShortcutDescriptor(
    val userId: Int,
    val packageName: String,
    val appName: String,
    val shortcutId: String,
    val title: String,
    val activityName: String?,
    val pinned: Boolean,
    val dynamic: Boolean,
    /** Opaque, validated recipe captured while Shizuku is available. */
    val directLaunchTarget: String? = null,
) {
    val stableKey: String get() = "$userId/$packageName/$shortcutId"
}

/**
 * A deliberately narrow direct-launch recipe. GameHub 6.1.2 exports DeepLinkActivity, so the
 * shortcut's local game id can be persisted once and launched later without Shizuku. No arbitrary
 * component, action, flag or extra is accepted from storage.
 */
data class ExternalShortcutDirectLaunch(
    val gameId: String,
) {
    fun encode(): String = "$PREFIX${gameId.toUtf8Hex()}"

    fun intentSpec(): IntentSpec =
        IntentSpec(
            packageName = GAME_HUB_PACKAGE,
            activityName = GAME_HUB_ACTIVITY,
            action = ACTION_VIEW,
            flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP,
            extras =
                mapOf(
                    EXTRA_NAV_TARGET to NAV_TARGET_LOCAL_GAME,
                    EXTRA_GAME_ID to gameId,
                ),
        )

    data class IntentSpec(
        val packageName: String,
        val activityName: String,
        val action: String,
        val flags: Int,
        val extras: Map<String, String>,
    )

    companion object {
        const val GAME_HUB_PACKAGE = "com.xiaoji.egggame"
        const val GAME_HUB_ACTIVITY = "com.xiaoji.egggame.DeepLinkActivity"
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val EXTRA_NAV_TARGET = "app_nav_target"
        const val EXTRA_GAME_ID = "app_nav_game_id"
        const val NAV_TARGET_LOCAL_GAME = "local_game_launch"
        const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
        const val FLAG_ACTIVITY_CLEAR_TOP = 0x04000000

        private const val PREFIX = "kireibox-direct://gamehub/v1/"
        private val GAME_ID = Regex("local_[A-Za-z0-9_-]{1,256}")

        fun create(gameId: String?): ExternalShortcutDirectLaunch? {
            val normalized = gameId?.trim().orEmpty()
            return normalized.takeIf(GAME_ID::matches)?.let(::ExternalShortcutDirectLaunch)
        }

        fun parse(raw: String?): ExternalShortcutDirectLaunch? {
            val value = raw?.trim().orEmpty()
            if (!value.startsWith(PREFIX, ignoreCase = true)) return null
            return create(value.substring(PREFIX.length).hexToUtf8())
        }

        fun fromGame(game: Game?): ExternalShortcutDirectLaunch? =
            game
                ?.takeIf { it.engine == EngineType.ANDROID_APP }
                ?.launchTarget
                ?.let(::parse)
    }
}

object ExternalShortcutPolicy {
    fun descriptors(
        values: Iterable<ExternalShortcutDescriptor>,
        ownPackage: String
    ): List<ExternalShortcutDescriptor> =
        values.asSequence()
            .filter { it.packageName.isNotBlank() && it.shortcutId.isNotBlank() }
            .filter { it.packageName != ownPackage }
            .filter { it.pinned }
            .distinctBy(ExternalShortcutDescriptor::stableKey)
            .sortedWith(
                compareBy<ExternalShortcutDescriptor> { it.appName.lowercase() }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.shortcutId },
            )
            .toList()
}

/** Stable identity stored in Game.rootUri without a database migration. */
object ExternalShortcutIdentity {
    private const val PREFIX = "android-shortcut://shizuku/"
    private const val LEGACY_VIVO_PREFIX = "android-shortcut://vivo/"

    data class Parsed(
        val userId: Int,
        val packageName: String,
        val shortcutId: String,
        val legacy: Boolean = false,
    )

    fun rootUri(
        userId: Int,
        packageName: String,
        shortcutId: String
    ): String = "$PREFIX" + "u${userId.coerceAtLeast(0)}/${packageName.trim()}/${shortcutId.toUtf8Hex()}"

    fun legacyVivoRootUri(
        packageName: String,
        shortcutId: String
    ): String = "$LEGACY_VIVO_PREFIX${packageName.trim()}/${shortcutId.toUtf8Hex()}"

    fun parse(raw: String?): Parsed? {
        val value = raw?.trim().orEmpty()
        if (value.startsWith(PREFIX, ignoreCase = true)) {
            val parts = value.substring(PREFIX.length).split('/', limit = 3)
            if (parts.size != 3 || !parts[0].startsWith('u')) return null
            val userId = parts[0].drop(1).toIntOrNull()?.takeIf { it >= 0 } ?: return null
            val packageName = parts[1].trim().takeIf(String::isNotEmpty) ?: return null
            val shortcutId = parts[2].hexToUtf8()?.takeIf(String::isNotEmpty) ?: return null
            return Parsed(userId, packageName, shortcutId)
        }
        if (value.startsWith(LEGACY_VIVO_PREFIX, ignoreCase = true)) {
            val parts = value.substring(LEGACY_VIVO_PREFIX.length).split('/', limit = 2)
            if (parts.size != 2) return null
            val packageName = parts[0].trim().takeIf(String::isNotEmpty) ?: return null
            val shortcutId = parts[1].hexToUtf8()?.takeIf(String::isNotEmpty) ?: return null
            return Parsed(0, packageName, shortcutId, legacy = true)
        }
        return null
    }

    fun fromGame(game: Game?): Parsed? =
        game
            ?.takeIf { it.engine == EngineType.ANDROID_APP }
            ?.rootUri
            ?.let(::parse)

    fun isExternalShortcut(game: Game?): Boolean = fromGame(game) != null
}

object ExternalShortcutMergePolicy {
    fun isUserRenamed(
        currentTitle: String?,
        originalTitle: String?
    ): Boolean {
        val current = currentTitle?.trim().orEmpty()
        val original = originalTitle?.trim().orEmpty()
        return current.isNotEmpty() && original.isNotEmpty() && current != original
    }

    fun isImported(existing: Game?): Boolean = existing != null && !existing.hidden
}

object ExternalShortcutLaunchPolicy {
    fun requiresShizuku(game: Game?): Boolean = ExternalShortcutIdentity.fromGame(game) != null && ExternalShortcutDirectLaunch.fromGame(game) == null
}

private fun String.toUtf8Hex(): String =
    toByteArray(StandardCharsets.UTF_8)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun String.hexToUtf8(): String? {
    if (length % 2 != 0 || any { it.digitToIntOrNull(16) == null }) return null
    val bytes =
        ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    return runCatching { String(bytes, StandardCharsets.UTF_8) }.getOrNull()
}
