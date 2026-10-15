package com.distrigo.app.data.license

import com.distrigo.app.data.auth.AccessToken
import com.distrigo.app.data.auth.AuthApi
import com.distrigo.app.data.auth.AuthError
import com.distrigo.app.data.auth.AuthRepository
import com.distrigo.app.data.auth.Backend
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** What an Edge Function answered: its JSON (HTTP 200), or why there is none, in the user's words. */
internal sealed interface Reply {
    data class Answer(val body: JsonObject) : Reply
    data class Refused(val message: String) : Reply
}

/**
 * DistriGo's Edge Functions, called as the signed-in account: its access token goes with each call, and a refused
 * one is renewed once. LicenseClient and CodesAdminClient both go through it.
 */
internal class FunctionCalls(
    private val http: OkHttpClient,
    private val backend: Backend,
    private val auth: AuthRepository,
) {
    suspend fun call(function: String, body: JsonObject): Reply {
        repeat(2) { attempt ->
            val token = when (val access = auth.accessToken(forceRefresh = attempt > 0)) {
                is AccessToken.Granted -> access.token
                is AccessToken.Denied -> return Reply.Refused(access.error.message)
            }
            val (status, json) = post(function, token, body) ?: return Reply.Refused(AuthError.NETWORK.message)
            when {
                status == 401 && attempt == 0 -> Unit // the token: renewed, then once more
                status == 401 -> return Reply.Refused(AuthError.SESSION_EXPIRED.message)
                status == 200 && json != null -> return Reply.Answer(json)
                else -> return Reply.Refused(SERVER_MESSAGE)
            }
        }
        return Reply.Refused(SERVER_MESSAGE)
    }

    private suspend fun post(function: String, token: String, body: JsonObject): Pair<Int, JsonObject?>? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(backend.function(function))
                .header("apikey", backend.publishableKey)
                .header("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody(AuthApi.JSON))
                .build()
            try {
                http.newCall(request).execute().use { response ->
                    val json = try {
                        response.body?.string()?.let { JsonParser().parse(it).asJsonObject }
                    } catch (e: RuntimeException) {
                        null
                    }
                    response.code to json
                }
            } catch (e: IOException) {
                null
            }
        }

    companion object {
        const val SERVER_MESSAGE = "Le service ne répond pas comme prévu. Réessayez plus tard."
    }
}
