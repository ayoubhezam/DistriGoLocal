package com.distrigo.app.data.auth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** What went wrong, in the words the user reads. */
enum class AuthError(val message: String) {
    INVALID_CREDENTIALS("E-mail ou mot de passe incorrect."),
    EMAIL_TAKEN("Un compte existe déjà avec cet e-mail. Connectez-vous."),
    WEAK_PASSWORD("Mot de passe trop faible : 8 caractères au moins, lettres et chiffres."),
    INVALID_EMAIL("Adresse e-mail invalide."),
    EMAIL_NOT_CONFIRMED("Confirmez d'abord votre adresse e-mail : un message vous a été envoyé."),
    SIGNUP_DISABLED("Les inscriptions sont fermées pour le moment."),
    PROVIDER_DISABLED("Cette méthode de connexion n'est pas encore disponible."),
    RATE_LIMITED("Trop de tentatives. Réessayez dans quelques minutes."),
    SESSION_EXPIRED("Votre session a expiré. Reconnectez-vous."),
    ANONYMOUS_DISABLED("L'activation par code n'est pas encore ouverte. Réessayez plus tard."),
    NETWORK("Pas de connexion Internet. Réessayez une fois connecté."),
    SERVER("Le service ne répond pas. Réessayez plus tard."),
    ;

    companion object {
        /** The error Supabase Auth answered with: its error_code (or older error field), else the HTTP status. */
        internal fun from(httpStatus: Int, body: JsonObject?): AuthError {
            val code = body?.text("error_code") ?: body?.text("error")
            val message = (body?.text("msg") ?: body?.text("error_description") ?: body?.text("message")).orEmpty().lowercase()
            return when (code) {
                "invalid_credentials" -> INVALID_CREDENTIALS
                "invalid_grant" -> if ("refresh" in message) SESSION_EXPIRED else INVALID_CREDENTIALS
                "user_already_exists", "email_exists" -> EMAIL_TAKEN
                "weak_password" -> WEAK_PASSWORD
                "email_address_invalid", "email_address_not_authorized" -> INVALID_EMAIL
                "email_not_confirmed" -> EMAIL_NOT_CONFIRMED
                "signup_disabled" -> SIGNUP_DISABLED
                "provider_disabled", "email_provider_disabled" -> PROVIDER_DISABLED
                "anonymous_provider_disabled" -> ANONYMOUS_DISABLED
                "over_request_rate_limit", "over_email_send_rate_limit" -> RATE_LIMITED
                "refresh_token_not_found", "refresh_token_already_used", "session_not_found", "session_expired",
                "user_not_found", "user_banned", "bad_jwt" -> SESSION_EXPIRED
                "validation_failed" -> if ("email" in message) INVALID_EMAIL else WEAK_PASSWORD
                else -> when {
                    httpStatus == 429 -> RATE_LIMITED
                    httpStatus >= 500 -> SERVER
                    "invalid login" in message -> INVALID_CREDENTIALS
                    else -> SERVER
                }
            }
        }

        private fun JsonObject.text(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString
    }
}

/** How a sign-in, a sign-up or a refresh ended. */
sealed interface AuthOutcome {
    data class SignedIn(val session: AuthSession) : AuthOutcome

    /** Signed up, but Supabase waits for the e-mail to be confirmed before giving a session. */
    data class ConfirmEmail(val email: String) : AuthOutcome

    data class Failed(val error: AuthError) : AuthOutcome
}

/** How giving an account made by a code an e-mail ended. */
sealed interface EmailUpdate {
    /** The address holds: the account signs in with it from now on. */
    data object Done : EmailUpdate

    /** Supabase waits for the link it sent to [email] (e-mail confirmation turned on). */
    data class ConfirmByLink(val email: String) : EmailUpdate

    data class Failed(val error: AuthError) : EmailUpdate
}

/**
 * Supabase Auth, over HTTPS: the calls DistriGo needs, written here rather than taken from an SDK so that
 * the app keeps its one HTTP client and its JSON as everywhere else.
 */
class AuthApi(
    private val http: OkHttpClient,
    private val backend: Backend,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun signUp(email: String, password: String, name: String): AuthOutcome {
        val body = JsonObject().apply {
            addProperty("email", email)
            addProperty("password", password)
            add("data", JsonObject().apply { addProperty("full_name", name) })
        }
        return post("signup", body) { json ->
            AuthSession.fromAuthResponse(json, clock())?.let(AuthOutcome::SignedIn) ?: AuthOutcome.ConfirmEmail(email)
        }
    }

    /**
     * An account without an e-mail, for an activation code (docs/activation_codes.md): Supabase's anonymous
     * sign-in, a sign-up with neither e-mail nor phone. It holds a license like any account.
     */
    suspend fun signInAnonymously(): AuthOutcome =
        post("signup", JsonObject().apply { add("data", JsonObject()) }, onSuccess = ::session)

    suspend fun signInWithPassword(email: String, password: String): AuthOutcome {
        val body = JsonObject().apply {
            addProperty("email", email)
            addProperty("password", password)
        }
        return post("token?grant_type=password", body, onSuccess = ::session)
    }

    /** Google's ID token from Credential Manager, and the nonce whose SHA-256 Google signed into it. */
    suspend fun signInWithGoogle(idToken: String, rawNonce: String): AuthOutcome {
        val body = JsonObject().apply {
            addProperty("provider", "google")
            addProperty("id_token", idToken)
            addProperty("nonce", rawNonce)
        }
        return post("token?grant_type=id_token", body, onSuccess = ::session)
    }

    suspend fun refresh(refreshToken: String): AuthOutcome {
        val body = JsonObject().apply { addProperty("refresh_token", refreshToken) }
        return post("token?grant_type=refresh_token", body, onSuccess = ::session)
    }

    /**
     * Gives an account made by an activation code an e-mail and a password (PUT /auth/v1/user): it becomes an
     * ordinary account, same id, same business, which can sign in on any phone. Both go in one request: Supabase
     * refuses a password alone for an anonymous account. With e-mail confirmation off (this project) the address
     * holds at once and the account stops being anonymous; on, it waits for its link.
     */
    suspend fun updateUser(accessToken: String, email: String, password: String): EmailUpdate {
        val body = JsonObject().apply {
            addProperty("email", email)
            addProperty("password", password)
        }
        return send("user", body, bearer = accessToken, method = "PUT", failed = EmailUpdate::Failed) { user ->
            when {
                user.text("email").equals(email, ignoreCase = true) -> EmailUpdate.Done
                user.text("new_email").equals(email, ignoreCase = true) -> EmailUpdate.ConfirmByLink(email)
                else -> EmailUpdate.Failed(AuthError.SERVER)
            }
        }
    }

    /** Ends the session on the server too. Its failure changes nothing: the phone forgets the session anyway. */
    suspend fun signOut(accessToken: String) {
        post("logout", JsonObject(), bearer = accessToken) { AuthOutcome.ConfirmEmail("") }
    }

    private fun session(json: JsonObject): AuthOutcome =
        AuthSession.fromAuthResponse(json, clock())?.let(AuthOutcome::SignedIn) ?: AuthOutcome.Failed(AuthError.SERVER)

    private suspend fun post(
        path: String,
        body: JsonObject,
        bearer: String? = null,
        onSuccess: (JsonObject) -> AuthOutcome,
    ): AuthOutcome = send(path, body, bearer, "POST", AuthOutcome::Failed, onSuccess)

    private suspend fun <T> send(
        path: String,
        body: JsonObject,
        bearer: String?,
        method: String,
        failed: (AuthError) -> T,
        onSuccess: (JsonObject) -> T,
    ): T = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(backend.auth(path))
            .header("apikey", backend.publishableKey)
            .apply { if (bearer != null) header("Authorization", "Bearer $bearer") }
            .method(method, body.toString().toRequestBody(JSON))
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val json = parse(response.body?.string())
                if (response.isSuccessful) {
                    onSuccess(json ?: JsonObject())
                } else {
                    failed(AuthError.from(response.code, json))
                }
            }
        } catch (e: IOException) {
            failed(AuthError.NETWORK)
        }
    }

    private fun JsonObject.text(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun parse(text: String?): JsonObject? =
        try {
            text?.takeIf { it.isNotBlank() }?.let { JsonParser().parse(it).asJsonObject }
        } catch (e: RuntimeException) {
            null
        }

    companion object {
        internal val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
