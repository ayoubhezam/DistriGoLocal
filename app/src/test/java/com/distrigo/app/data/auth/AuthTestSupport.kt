package com.distrigo.app.data.auth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The Keystore's AES-GCM, in software, with a key the test chooses. */
internal class SoftwareSessionCipher(seed: Int = 1) : SessionCipher {
    private val key = SecretKeySpec(ByteArray(32) { (it * 7 + seed).toByte() }, "AES")
    private val random = java.security.SecureRandom()

    override fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        return iv + cipher.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, 12))
        }
        return cipher.doFinal(sealed, 12, sealed.size - 12)
    }
}

internal fun MockWebServer.backend() = Backend(url("/").toString().trimEnd('/'), "sb_publishable_test")

internal fun jsonResponse(status: Int, body: String) =
    MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

/** Supabase Auth's answer to a sign-in or a refresh. */
internal fun sessionJson(
    access: String = "access-1",
    refresh: String = "refresh-1",
    expiresAt: Long = 4_102_444_800, // 2100
    name: String? = "Ayoub Test",
    provider: String = "email",
) = """{"access_token":"$access","token_type":"bearer","expires_in":3600,"expires_at":$expiresAt,"refresh_token":"$refresh",
    "user":{"id":"0b6f4c1e-5d2a-4f7e-9c3b-1a2d3e4f5a6b","email":"a@example.com",
    "user_metadata":{${name?.let { "\"full_name\":\"$it\"" } ?: ""}},"app_metadata":{"provider":"$provider"}}}"""

internal val RecordedRequest.json: JsonObject get() = JsonParser().parse(body.readUtf8()).asJsonObject

internal fun session(expiresAt: Long = 4_102_444_800_000, refresh: String = "refresh-0") = AuthSession(
    accessToken = "access-0",
    refreshToken = refresh,
    expiresAt = expiresAt,
    userId = "0b6f4c1e-5d2a-4f7e-9c3b-1a2d3e4f5a6b",
    email = "a@example.com",
    displayName = "Ayoub Test",
    provider = "email",
)
