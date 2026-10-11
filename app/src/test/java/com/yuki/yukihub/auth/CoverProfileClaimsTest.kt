package com.yuki.yukihub.auth

import com.yuki.yukihub.metadata.model.CoverSourceMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class CoverProfileClaimsTest {
    @Test
    fun nextmoeUserinfoSuppliesTheUserNameAndAvatar() {
        val profile =
            CoverProfileClaims.fromJson(
                CoverSourceMode.NEXTMOE,
                JSONObject(
                    """{"id":"1","name":"Yukin0a123","sub":"72acc252-7578-484f-8052-a66f9c059fe1",
                    "picture":"https://img.example/a.webp","adult_confirmed":true,"nsfw_display":"show"}"""
                )
            )
        assertEquals("Yukin0a123", profile.name)
        assertEquals("72acc252-7578-484f-8052-a66f9c059fe1", profile.accountId)
        assertEquals("https://img.example/a.webp", profile.avatarUrl)
    }

    @Test
    fun nextmoeIdTokenHasOnlyTheOpaqueSubject() {
        // The real NextMoe id_token: aud, exp, iat, iss, nonce, sub — no name, no picture.
        val profile = CoverProfileClaims.fromJson(CoverSourceMode.NEXTMOE, JSONObject("""{"aud":"c","iss":"https://account.nextmoe.com","sub":"72acc252"}"""))
        assertEquals("", profile.name)
        assertEquals("72acc252", profile.accountId)
        assertEquals("", profile.avatarUrl)
    }

    @Test
    fun hikarinagiPrefersNicknameThenNameThenHandle() {
        val all = JSONObject("""{"nickname":"Nick","name":"Name","preferred_username":"handle","sub":"9"}""")
        assertEquals("Nick", CoverProfileClaims.fromJson(CoverSourceMode.HIKARINAGI, all).name)
        assertEquals("Name", CoverProfileClaims.fromJson(CoverSourceMode.HIKARINAGI, JSONObject("""{"name":"Name","preferred_username":"handle"}""")).name)
        assertEquals("handle", CoverProfileClaims.fromJson(CoverSourceMode.HIKARINAGI, JSONObject("""{"preferred_username":"handle"}""")).name)
    }

    @Test
    fun blankOrMissingClaimsGiveAnEmptyProfile() {
        assertEquals(CoverProfile(), CoverProfileClaims.fromJson(CoverSourceMode.NEXTMOE, null))
        assertEquals("", CoverProfileClaims.fromJson(CoverSourceMode.NEXTMOE, JSONObject("""{"name":"   "}""")).name)
    }

    @Test
    fun idTokenPayloadDecodesWithOrWithoutPadding() {
        fun jwt(payload: String) = "h." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray()) + ".s"
        assertEquals("a", CoverProfileClaims.idTokenClaims(jwt("""{"sub":"a"}"""))?.optString("sub"))
        assertEquals("ü", CoverProfileClaims.idTokenClaims(jwt("""{"name":"ü"}"""))?.optString("name"))
        assertNull(CoverProfileClaims.idTokenClaims(null))
        assertNull(CoverProfileClaims.idTokenClaims("not-a-jwt"))
        assertNull(CoverProfileClaims.idTokenClaims("h.@@@.s"))
    }

    @Test
    fun placeholderNamesAreRecognised() {
        val next = CoverSourceMode.NEXTMOE
        assertTrue(CoverProfileClaims.isPlaceholder(next, "", "id"))
        assertTrue(CoverProfileClaims.isPlaceholder(next, "NextMoe 已授权", "id"))
        assertTrue(CoverProfileClaims.isPlaceholder(next, "72acc252", "72acc252"))
        assertFalse(CoverProfileClaims.isPlaceholder(next, "Yukin0a123", "72acc252"))
    }

    @Test
    fun onlyNextmoeAsksUserinfoForItsName() {
        assertEquals("https://account.nextmoe.com/api/v1/oauth/userinfo", CoverOAuthConfig.userinfoUrl(CoverSourceMode.NEXTMOE))
        assertNull(CoverOAuthConfig.userinfoUrl(CoverSourceMode.HIKARINAGI))
        assertNull(CoverOAuthConfig.userinfoUrl(CoverSourceMode.BANGUMI))
    }

    @Test
    fun aTokenRefreshOrSameAccountLoginKeepsTheStoredIdentity() {
        val stored = CoverProfile("Yukin0a123", "72acc252", "https://img.example/a.webp")
        assertEquals(stored, CoverProfileClaims.merge(stored, CoverProfile()))
        assertEquals(stored, CoverProfileClaims.merge(stored, CoverProfile(accountId = "72acc252")))
        assertEquals("Renamed", CoverProfileClaims.merge(stored, CoverProfile("Renamed", "72acc252")).name)
    }

    @Test
    fun aLoginToAnotherAccountNeverInheritsThePreviousName() {
        val stored = CoverProfile("Yukin0a123", "72acc252", "https://img.example/a.webp")
        val merged = CoverProfileClaims.merge(stored, CoverProfile(accountId = "other-sub"))
        assertEquals(CoverProfile(accountId = "other-sub"), merged)
        // The blank name is a placeholder, so the settings card asks userinfo for the new account's name.
        assertTrue(CoverProfileClaims.isPlaceholder(CoverSourceMode.NEXTMOE, merged.name, merged.accountId))
        // A legacy record without an id cannot prove it is the same account either.
        assertEquals("", CoverProfileClaims.merge(CoverProfile("Old"), CoverProfile(accountId = "new")).name)
    }
}
