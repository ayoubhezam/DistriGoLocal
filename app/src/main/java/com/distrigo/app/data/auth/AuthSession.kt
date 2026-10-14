package com.distrigo.app.data.auth

import com.google.gson.JsonObject

/**
 * The Supabase project behind accounts and licenses (docs/license_architecture.md §6). Both values are public:
 * every client of the project carries them; what protects the data is row-level security.
 */
data class Backend(val url: String, val publishableKey: String) {
    fun auth(path: String) = "$url/auth/v1/$path"
    fun function(name: String) = "$url/functions/v1/$name"

    companion object {
        val DISTRIGO = Backend(
            url = "https://nwzriieoptyrdttyxdlx.supabase.co",
            publishableKey = "sb_publishable_ti9e2a41y-D7Y3ZwnLwtTg_vv54TwKN",
        )
    }
}

/**
 * A signed-in account, as Supabase Auth hands it over. The access token lasts an hour and is renewed with the
 * refresh token, which is as good as the password: both are kept encrypted (SessionStore) and never logged.
 */
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    /** When the access token expires, epoch milliseconds. */
    val expiresAt: Long,
    val userId: String,
    val email: String?,
    val displayName: String?,
    /** "email", "google", or "anonymous" for an account made by an activation code. */
    val provider: String,
) {
    override fun toString(): String = "AuthSession(user=$userId, provider=$provider)"

    /** Made by an activation code: no e-mail, so the code is how it comes back (docs/activation_codes.md). */
    val isAnonymous: Boolean get() = provider == ANONYMOUS

    /** Two letters for the avatar: from the name, or else the e-mail. */
    val initials: String?
        get() {
            val words = displayName?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()
            val letters = when {
                words.size >= 2 -> "${words[0].first()}${words[1].first()}"
                words.size == 1 -> words[0].take(2)
                else -> email?.take(1)
            }
            return letters?.uppercase()?.takeIf { it.isNotBlank() }
        }

    internal fun toJson(): JsonObject = JsonObject().apply {
        addProperty("v", 1)
        addProperty("access_token", accessToken)
        addProperty("refresh_token", refreshToken)
        addProperty("expires_at", expiresAt)
        addProperty("user_id", userId)
        addProperty("email", email)
        addProperty("display_name", displayName)
        addProperty("provider", provider)
    }

    companion object {
        /** A session as SessionStore wrote it; null for anything else. */
        internal fun fromJson(json: JsonObject): AuthSession? =
            try {
                if (json.get("v")?.asInt != 1) {
                    null
                } else {
                    AuthSession(
                        accessToken = json.get("access_token").asString,
                        refreshToken = json.get("refresh_token").asString,
                        expiresAt = json.get("expires_at").asLong,
                        userId = json.get("user_id").asString,
                        email = json.text("email"),
                        displayName = json.text("display_name"),
                        provider = json.text("provider") ?: "email",
                    )
                }
            } catch (e: RuntimeException) {
                null
            }

        /**
         * A session from Supabase Auth's answer to a sign-in, a sign-up or a refresh; null when the answer holds
         * no session (a sign-up waiting for its e-mail to be confirmed).
         */
        internal fun fromAuthResponse(json: JsonObject, now: Long): AuthSession? {
            val accessToken = json.text("access_token") ?: return null
            val refreshToken = json.text("refresh_token") ?: return null
            val user = json.getAsJsonObject("user") ?: return null
            val metadata = user.get("user_metadata")?.takeIf { it.isJsonObject }?.asJsonObject
            val appMetadata = user.get("app_metadata")?.takeIf { it.isJsonObject }?.asJsonObject
            val expiresAt = json.get("expires_at")?.takeIf { it.isJsonPrimitive }?.asLong?.times(1000)
                ?: (now + (json.get("expires_in")?.takeIf { it.isJsonPrimitive }?.asLong ?: 3600) * 1000)
            return AuthSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = expiresAt,
                userId = user.text("id") ?: return null,
                email = user.text("email"),
                displayName = metadata?.text("full_name") ?: metadata?.text("name"),
                provider = if (user.get("is_anonymous")?.takeIf { it.isJsonPrimitive }?.asBoolean == true) {
                    ANONYMOUS
                } else {
                    // A code account given an e-mail may still name "anonymous" as the provider it began with
                    appMetadata?.text("provider")?.takeIf { it != ANONYMOUS } ?: "email"
                },
            )
        }

        const val ANONYMOUS = "anonymous"

        private fun JsonObject.text(key: String): String? =
            get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
    }
}
