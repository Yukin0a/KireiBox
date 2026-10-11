package com.yuki.yukihub.auth

import com.yuki.yukihub.metadata.model.CoverSourceMode
import org.json.JSONObject
import java.util.Base64

/** What the settings card shows for a signed-in source account. */
data class CoverProfile(
    val name: String = "",
    val accountId: String = "",
    val avatarUrl: String = "",
)

/**
 * Reads the display identity from an OIDC claim set (id_token payload or userinfo response).
 *
 * NextMoe's id_token carries only `sub`; the user name (`name`) and `picture` are served by its
 * userinfo endpoint, so a login that only reads the id_token ends up with a blank name.
 */
object CoverProfileClaims {
    fun fromJson(
        source: CoverSourceMode,
        claims: JSONObject?,
    ): CoverProfile {
        if (claims == null) return CoverProfile()
        val keys = if (source == CoverSourceMode.NEXTMOE) NEXTMOE_NAME_KEYS else DEFAULT_NAME_KEYS
        return CoverProfile(
            name = keys.firstNotNullOfOrNull { claims.optString(it).trim().ifBlank { null } }.orEmpty(),
            accountId = claims.optString("sub").ifBlank { claims.optString("user_id") },
            avatarUrl = claims.optString("picture"),
        )
    }

    /** The payload of an unverified JWT, or null when it is absent or malformed. */
    fun idTokenClaims(raw: String?): JSONObject? =
        runCatching {
            val payload = requireNotNull(raw).split('.')[1]
            JSONObject(String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8))
        }.getOrNull()

    /**
     * The identity a credential save keeps. A token refresh (no incoming id) or a fresh login to the same
     * account keeps what is stored; a login to another account starts from its own claims, so the card
     * never shows the previous account's name or avatar.
     */
    fun merge(
        stored: CoverProfile,
        incoming: CoverProfile,
    ): CoverProfile {
        if (incoming.accountId.isNotBlank() && incoming.accountId != stored.accountId) return incoming
        return CoverProfile(
            name = incoming.name.ifBlank { stored.name },
            accountId = incoming.accountId.ifBlank { stored.accountId },
            avatarUrl = incoming.avatarUrl.ifBlank { stored.avatarUrl },
        )
    }

    /** True while the stored name is only a stand-in (blank, the 「已授权」 fallback, or the opaque id). */
    fun isPlaceholder(
        source: CoverSourceMode,
        name: String,
        accountId: String,
    ): Boolean = name.isBlank() || name == "${source.label} 已授权" || (accountId.isNotBlank() && name == accountId)

    private val NEXTMOE_NAME_KEYS = listOf("name", "nickname", "preferred_username")
    private val DEFAULT_NAME_KEYS = listOf("nickname", "name", "preferred_username")
}
