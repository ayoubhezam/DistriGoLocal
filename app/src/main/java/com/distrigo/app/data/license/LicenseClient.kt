package com.distrigo.app.data.license

import com.distrigo.app.data.auth.AccessToken
import com.distrigo.app.data.auth.AuthApi
import com.distrigo.app.data.auth.AuthError
import com.distrigo.app.data.auth.AuthRepository
import com.distrigo.app.data.auth.Backend
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.util.Base64

/** What the phone knows about itself, for the server. */
interface DeviceFacts {
    /** DeviceIdentity: file I/O on first use. */
    val installationId: String
    /** Settings.Secure.ANDROID_ID: recognises a reinstall on the same phone; never a security check. */
    val androidId: String?
    val model: String
    val osVersion: String
    val appVersion: String
}

/** A phone already holding the business's seat, as the server lists them on seat_taken. */
data class SeatHolder(val id: String, val model: String?, val lastSeenAt: Instant?)

/** Where an activation stands: done, or what the user must answer next. */
sealed interface ActivationStep {
    data class Activated(val trial: Boolean?, val reduced: Boolean) : ActivationStep

    /** The account has no business yet: its name, then [ActivationRequest] again. */
    data class NeedsBusinessName(val request: ActivationRequest) : ActivationStep

    /** The business's seat is taken: transfer it here, or not. */
    data class SeatTaken(val request: ActivationRequest, val holders: List<SeatHolder>) : ActivationStep

    data class TransferLimit(val nextAt: Instant?) : ActivationStep

    /** The phone already has a license: a check-in, not an activation, is what it needs. */
    data object AlreadyActivated : ActivationStep

    data class Failed(val message: String) : ActivationStep
}

/**
 * An activation under way: the nonce, the new key's attestation and its signature. The server rolls back every
 * answer but "activated", so the same request serves the user's answer within the nonce's five minutes.
 */
class ActivationRequest internal constructor(internal val body: JsonObject)

sealed interface CheckIn {
    data object Done : CheckIn

    /** The server says this phone no longer has the subscription: the license is revoked here too. */
    data object Revoked : CheckIn

    data object NotActivated : CheckIn
    data class Failed(val message: String) : CheckIn
}

/**
 * The license's conversations with the server (docs/license_architecture.md §6.4): activation, check-in,
 * releasing this phone, deleting the account. Each call carries the account's access token, renewed once if
 * the server refuses it.
 */
class LicenseClient(
    private val http: OkHttpClient,
    private val backend: Backend,
    private val auth: AuthRepository,
    private val keys: DeviceKeys,
    private val license: LicenseManager,
    private val device: DeviceFacts,
) {
    /**
     * Starts activating this phone: a nonce, a new device key attested with it, the request. Refused while the
     * phone holds a license — making a new key would invalidate it.
     */
    suspend fun startActivation(): ActivationStep {
        val state = withContext(Dispatchers.IO) { license.evaluate() }
        if (state.holdsLicense) return ActivationStep.AlreadyActivated
        val nonce = when (val reply = call("license-nonce", JsonObject().apply { addProperty("purpose", "activate") })) {
            is Reply.Answer -> reply.body.text("nonce") ?: return ActivationStep.Failed(SERVER_MESSAGE)
            is Reply.Refused -> return ActivationStep.Failed(reply.message)
        }
        val body = withContext(Dispatchers.IO) {
            val nonceBytes = Base64.getUrlDecoder().decode(nonce)
            val key = keys.create(nonceBytes)
            JsonObject().apply {
                addProperty("nonce", nonce)
                addProperty("installation_id", device.installationId)
                add("certificate_chain", JsonArray().apply { key.certificateChain.forEach { add(Base64.getEncoder().encodeToString(it)) } })
                addProperty("signature", Base64.getEncoder().encodeToString(keys.sign(signedMessage("activate", nonce, device.installationId))))
                addProperty("android_id", device.androidId)
                addProperty("model", device.model)
                addProperty("os_version", device.osVersion)
                addProperty("app_version", device.appVersion)
            }
        }
        return send(ActivationRequest(body))
    }

    /** The user's answer: the business's name, or yes to the transfer (of [replaceDevice], or the oldest). */
    suspend fun continueActivation(
        request: ActivationRequest,
        businessName: String? = null,
        transfer: Boolean = false,
        replaceDevice: String? = null,
    ): ActivationStep {
        val body = request.body.deepCopy().apply {
            businessName?.let { addProperty("business_name", it) }
            if (transfer) addProperty("transfer", true)
            replaceDevice?.let { addProperty("replace_device", it) }
        }
        return send(ActivationRequest(body))
    }

    private suspend fun send(request: ActivationRequest): ActivationStep {
        val reply = when (val answer = call("license-activate", request.body)) {
            is Reply.Answer -> answer.body
            is Reply.Refused -> return ActivationStep.Failed(answer.message)
        }
        return when (reply.text("status")) {
            "activated" -> {
                val token = reply.text("license") ?: return ActivationStep.Failed(SERVER_MESSAGE)
                when (val check = withContext(Dispatchers.IO) { license.install(token) }) {
                    is LicenseCheck.Valid -> {
                        reply.text("device_id")?.let { withContext(Dispatchers.IO) { license.rememberDevice(it) } }
                        ActivationStep.Activated(
                            trial = reply.get("trial")?.takeIf { it.isJsonPrimitive }?.asBoolean,
                            reduced = reply.text("tier") == "reduced",
                        )
                    }
                    is LicenseCheck.Rejected -> ActivationStep.Failed("La licence reçue n'a pas pu être vérifiée (${check.why}).")
                }
            }
            "no_business" -> ActivationStep.NeedsBusinessName(request)
            "seat_taken" -> ActivationStep.SeatTaken(request, holders(reply.getAsJsonArray("devices")))
            "transfer_limit" -> ActivationStep.TransferLimit(reply.text("next_at")?.let(::instant))
            "choose_business" -> ActivationStep.Failed("Ce compte appartient à plusieurs entreprises : ce cas n'est pas encore pris en charge.")
            "not_member" -> ActivationStep.Failed("Ce compte n'est plus membre de l'entreprise.")
            "refused" -> ActivationStep.Failed(
                if (reply.text("reason") == "repackaged") "Cette copie de DistriGo n'est pas l'application officielle."
                else "Ce téléphone n'a pas pu prouver son identité. Réessayez.",
            )
            "subscription_inactive" -> ActivationStep.Failed("L'abonnement est suspendu. Contactez l'équipe DistriGo.")
            "bad_nonce" -> ActivationStep.Failed("Le délai est dépassé. Recommencez l'activation.")
            else -> ActivationStep.Failed(SERVER_MESSAGE)
        }
    }

    /** The check-in: the phone proves it still holds its key, and gets the next license. */
    suspend fun checkIn(): CheckIn {
        val state = withContext(Dispatchers.IO) { license.evaluate() }
        if (!state.holdsLicense) return CheckIn.NotActivated
        val nonce = when (val reply = call("license-nonce", JsonObject().apply { addProperty("purpose", "refresh") })) {
            is Reply.Answer -> reply.body.text("nonce") ?: return CheckIn.Failed(SERVER_MESSAGE)
            is Reply.Refused -> return CheckIn.Failed(reply.message)
        }
        val body = withContext(Dispatchers.IO) {
            JsonObject().apply {
                addProperty("nonce", nonce)
                addProperty("installation_id", device.installationId)
                addProperty("signature", Base64.getEncoder().encodeToString(keys.sign(signedMessage("refresh", nonce, device.installationId))))
                addProperty("app_version", device.appVersion)
            }
        }
        val reply = when (val answer = call("license-refresh", body)) {
            is Reply.Answer -> answer.body
            is Reply.Refused -> return CheckIn.Failed(answer.message)
        }
        return when (reply.text("status")) {
            "ok" -> {
                val token = reply.text("license") ?: return CheckIn.Failed(SERVER_MESSAGE)
                when (val check = withContext(Dispatchers.IO) { license.install(token) }) {
                    is LicenseCheck.Valid -> CheckIn.Done
                    is LicenseCheck.Rejected -> CheckIn.Failed("La licence reçue n'a pas pu être vérifiée (${check.why}).")
                }
            }
            "revoked", "not_member", "unknown_device", "subscription_inactive" -> {
                withContext(Dispatchers.IO) { license.revoke() }
                CheckIn.Revoked
            }
            else -> CheckIn.Failed(SERVER_MESSAGE)
        }
    }

    /** « Libérer cet appareil »: the seat is freed on the server; the license here is then forgotten. */
    suspend fun releaseThisDevice(): String? {
        val deviceId = withContext(Dispatchers.IO) { license.deviceId() }
        if (deviceId != null) {
            when (val reply = call("device-release", JsonObject().apply { addProperty("device_id", deviceId) })) {
                is Reply.Refused -> return reply.message
                is Reply.Answer -> if (reply.body.text("status") !in setOf("released", "unknown_device")) return SERVER_MESSAGE
            }
        }
        withContext(Dispatchers.IO) { license.clear() }
        return null
    }

    /** Deletes the account and, if it alone owns its business, the business. Null when done, else why not. */
    suspend fun deleteAccount(): String? {
        val reply = when (val answer = call("account-delete", JsonObject())) {
            is Reply.Answer -> answer.body
            is Reply.Refused -> return answer.message
        }
        return when (reply.text("status")) {
            "deleted" -> {
                withContext(Dispatchers.IO) { license.clear() }
                auth.forget()
                null
            }
            "transfer_ownership_first" -> "Confiez d'abord l'entreprise à un autre membre."
            else -> SERVER_MESSAGE
        }
    }

    private sealed interface Reply {
        data class Answer(val body: JsonObject) : Reply
        data class Refused(val message: String) : Reply
    }

    /** An Edge Function, with the access token; a refused token is renewed once. */
    private suspend fun call(function: String, body: JsonObject): Reply {
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

    private fun holders(devices: JsonArray?): List<SeatHolder> =
        devices?.mapNotNull { element ->
            val device = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            SeatHolder(
                id = device.text("id") ?: return@mapNotNull null,
                model = device.text("model"),
                lastSeenAt = device.text("last_seen_at")?.let(::instant),
            )
        }.orEmpty()

    private fun instant(text: String): Instant? = runCatching { java.time.OffsetDateTime.parse(text).toInstant() }.getOrNull()

    private fun JsonObject.text(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString

    companion object {
        private const val SERVER_MESSAGE = "Le service ne répond pas comme prévu. Réessayez plus tard."

        /** What the device key signs: supabase/functions/_shared/protocol.ts, signedMessage. */
        internal fun signedMessage(purpose: String, nonce: String, installationId: String): ByteArray =
            "distrigo-$purpose\n$nonce\n$installationId".toByteArray(Charsets.UTF_8)
    }
}

/** Whether this state holds a license the phone verified: a check-in renews it, a new activation would break it. */
val LicenseState.holdsLicense: Boolean
    get() = this is LicenseState.Judged || (this is LicenseState.CheckInRequired && license != null)
