package com.distrigo.app.data.license

import com.distrigo.app.data.auth.AuthRepository
import com.distrigo.app.data.auth.Backend
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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

/**
 * What the user typed that decides the business, sent with the activation (docs/activation_codes.md):
 * an activation code, or a new e-mail account's business. All optional; the server ignores what it doesn't need.
 */
data class ActivationDetails(
    /** As typed: the server reads it leniently (case, dashes, O for 0). */
    val activationCode: String? = null,
    val businessName: String? = null,
    val contactPhone: String? = null,
    /** One of the server's codes: alimentation, boissons, frais, hygiene, droguerie, materiaux, pharmacie, autre. */
    val activity: String? = null,
    /** 1, 2-5, 6-10 or 10+. */
    val salesReps: String? = null,
)

/** Where an activation stands: done, or what the user must answer next. */
sealed interface ActivationStep {
    /** [code]: what an activation code did — created, joined or renewed — or null without one. */
    data class Activated(val trial: Boolean?, val reduced: Boolean, val code: String? = null) : ActivationStep

    /** The account has no business yet: its name, then [ActivationRequest] again. */
    data class NeedsBusinessName(val request: ActivationRequest) : ActivationStep

    /** A valid code that names no company: its name and a phone number, then [ActivationRequest] again. */
    data class CodeNeedsBusiness(val request: ActivationRequest) : ActivationStep

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

/** What a code bought for the account's business (code-redeem). */
sealed interface Renewal {
    /** The days were added, and the phone holds the license they pay for. */
    data object Renewed : Renewal
    data class Failed(val message: String) : Renewal
}

/** « Informations de l'entreprise » (business-profile): what the server keeps about the account's business. */
data class BusinessProfile(
    val businessId: String,
    val name: String,
    val contactPhone: String?,
    /** One of the server's codes, as in [ActivationDetails.activity]; null until given. */
    val activity: String?,
    /** 1, 2-5, 6-10 or 10+; null until given. */
    val salesReps: String?,
    /** Owners and managers change it; agents only read it. */
    val canEdit: Boolean,
)

sealed interface ProfileAnswer {
    data class Found(val profile: BusinessProfile) : ProfileAnswer

    /** The account has no business yet: nothing to show. */
    data object NoBusiness : ProfileAnswer
    data class Failed(val message: String) : ProfileAnswer
}

sealed interface CheckIn {
    data object Done : CheckIn

    /** The server says this phone no longer has the subscription: the license is revoked here too. */
    data object Revoked : CheckIn

    /** The business's subscription is suspended: the license stays, marked, and check-ins go on. */
    data object Suspended : CheckIn

    data object NotActivated : CheckIn

    /** [retry]: the network or the server failed, and trying again later may succeed (the background job does). */
    data class Failed(val message: String, val retry: Boolean = false) : CheckIn
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
    private val functions = FunctionCalls(http, backend, auth)

    /**
     * Check-ins, releasing and deleting change the license here after the server answers: one at a time, so a
     * check-in's answer never lands after a sign-out has forgotten the license, and two check-ins never race to
     * install their licenses.
     */
    private val licenseCalls = Mutex()
    /**
     * Starts activating this phone: a nonce, a new device key attested with it, the request, with [details]
     * when the user gave a code or a new business. Refused while the phone holds a license — making a new key
     * would invalidate it.
     */
    suspend fun startActivation(details: ActivationDetails = ActivationDetails()): ActivationStep {
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
                details.activationCode?.let { addProperty("activation_code", it) }
                details.businessName?.let { addProperty("business_name", it) }
                details.contactPhone?.let { addProperty("contact_phone", it) }
                details.activity?.let { addProperty("business_activity", it) }
                details.salesReps?.let { addProperty("business_sales_reps", it) }
            }
        }
        return send(ActivationRequest(body))
    }

    /**
     * The user's answer: the business's name (with [contactPhone] for a code's company), or yes to the transfer
     * (of [replaceDevice], or the oldest).
     */
    suspend fun continueActivation(
        request: ActivationRequest,
        businessName: String? = null,
        transfer: Boolean = false,
        replaceDevice: String? = null,
        contactPhone: String? = null,
    ): ActivationStep {
        val body = request.body.deepCopy().apply {
            businessName?.let { addProperty("business_name", it) }
            contactPhone?.let { addProperty("contact_phone", it) }
            if (transfer) addProperty("transfer", true)
            replaceDevice?.let { addProperty("replace_device", it) }
        }
        return send(ActivationRequest(body))
    }

    /**
     * « Saisir un code » (docs/activation_codes.md): a new code's days for the account's business. Then a check-in,
     * so the phone holds the license they pay for at once, not at its next check-in.
     */
    suspend fun renewWithCode(code: String): Renewal {
        val reply = when (val answer = call("code-redeem", JsonObject().apply { addProperty("code", code) })) {
            is Reply.Answer -> answer.body
            is Reply.Refused -> return Renewal.Failed(answer.message)
        }
        return when (reply.text("status")) {
            "renewed" -> when (val check = checkIn()) {
                CheckIn.Done -> Renewal.Renewed
                // Paid for all the same: the next check-in brings it
                is CheckIn.Failed -> Renewal.Failed("Le code a bien été ajouté, mais la licence n'a pas pu être mise à jour : ${check.message}")
                // The days are added; the license comes with the reactivation
                CheckIn.Suspended -> Renewal.Failed("Le code a bien été ajouté, mais l'abonnement est suspendu. Contactez DistriGo.")
                CheckIn.Revoked, CheckIn.NotActivated -> Renewal.Failed("Le code a bien été ajouté. Activez maintenant ce téléphone.")
            }
            "bad_code" -> Renewal.Failed("Ce code n'existe pas ou n'est plus valable.")
            "code_expired" -> Renewal.Failed("Ce code a expiré. Demandez-en un nouveau à votre revendeur.")
            "code_used" -> Renewal.Failed("Ce code a déjà été utilisé.")
            "too_many_attempts" -> Renewal.Failed(tooManyAttempts(reply.text("retry_at")?.let(::instant)))
            "no_business" -> Renewal.Failed("Ce compte n'a pas encore d'entreprise à renouveler.")
            "choose_business" -> Renewal.Failed("Ce compte appartient à plusieurs entreprises : ce cas n'est pas encore pris en charge.")
            else -> Renewal.Failed(SERVER_MESSAGE)
        }
    }

    /**
     * The business's profile; with [changes] (the server's field names: name, contact_phone, activity, sales_reps;
     * a null phone clears it), those are changed first. Either way the answer is the whole profile as it now is.
     */
    suspend fun businessProfile(changes: Map<String, String?> = emptyMap()): ProfileAnswer {
        val body = JsonObject().apply { changes.forEach { (field, value) -> addProperty(field, value) } }
        val reply = when (val answer = call("business-profile", body)) {
            is Reply.Answer -> answer.body
            is Reply.Refused -> return ProfileAnswer.Failed(answer.message)
        }
        return when (reply.text("status")) {
            "ok" -> {
                val businessId = reply.text("business_id")
                val name = reply.text("name")
                if (businessId == null || name == null) return ProfileAnswer.Failed(SERVER_MESSAGE)
                ProfileAnswer.Found(
                    BusinessProfile(
                        businessId = businessId,
                        name = name,
                        contactPhone = reply.text("contact_phone"),
                        activity = reply.text("activity"),
                        salesReps = reply.text("sales_reps"),
                        canEdit = reply.get("can_edit")?.takeIf { it.isJsonPrimitive }?.asBoolean == true,
                    ),
                )
            }
            "no_business" -> ProfileAnswer.NoBusiness
            "not_allowed" -> ProfileAnswer.Failed("Seuls le gérant et les responsables peuvent modifier ces informations.")
            "not_member" -> ProfileAnswer.Failed("Ce compte n'est plus membre de l'entreprise.")
            "choose_business" -> ProfileAnswer.Failed("Ce compte appartient à plusieurs entreprises : ce cas n'est pas encore pris en charge.")
            else -> ProfileAnswer.Failed(SERVER_MESSAGE)
        }
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
                            code = reply.text("code"),
                        )
                    }
                    is LicenseCheck.Rejected -> ActivationStep.Failed("La licence reçue n'a pas pu être vérifiée (${check.why}).")
                }
            }
            "no_business" -> ActivationStep.NeedsBusinessName(request)
            "code_needs_business" -> ActivationStep.CodeNeedsBusiness(request)
            "bad_code" -> ActivationStep.Failed("Ce code n'existe pas ou n'est plus valable.")
            "code_expired" -> ActivationStep.Failed("Ce code a expiré. Demandez-en un nouveau à votre revendeur.")
            "code_used" -> ActivationStep.Failed("Ce code a déjà servi à un renouvellement.")
            "too_many_attempts" -> ActivationStep.Failed(tooManyAttempts(reply.text("retry_at")?.let(::instant)))
            "seat_taken" -> ActivationStep.SeatTaken(request, holders(reply.getAsJsonArray("devices")))
            "transfer_limit" -> ActivationStep.TransferLimit(reply.text("next_at")?.let(::instant))
            "choose_business" -> ActivationStep.Failed("Ce compte appartient à plusieurs entreprises : ce cas n'est pas encore pris en charge.")
            "not_member" -> ActivationStep.Failed("Ce compte n'est plus membre de l'entreprise.")
            "refused" -> ActivationStep.Failed(
                if (reply.text("reason") == "repackaged") "Cette copie de DistriGo n'est pas l'application officielle."
                else "Ce téléphone n'a pas pu prouver son identité. Réessayez.",
            )
            "subscription_inactive" -> ActivationStep.Failed(SUSPENDED_MESSAGE)
            "bad_nonce" -> ActivationStep.Failed("Le délai est dépassé. Recommencez l'activation.")
            else -> ActivationStep.Failed(SERVER_MESSAGE)
        }
    }

    /**
     * The check-in: the phone proves it still holds its key, and gets the next license. The app's check-ins go
     * through LicenseCheckIn; only a code's renewal calls this directly, for a license that includes its days.
     */
    suspend fun checkIn(): CheckIn = licenseCalls.withLock { checkInNow() }

    private suspend fun checkInNow(): CheckIn {
        val state = withContext(Dispatchers.IO) { license.evaluate() }
        if (!state.holdsLicense) return CheckIn.NotActivated
        val nonce = when (val reply = call("license-nonce", JsonObject().apply { addProperty("purpose", "refresh") })) {
            is Reply.Answer -> reply.body.text("nonce") ?: return CheckIn.Failed(SERVER_MESSAGE, retry = true)
            is Reply.Refused -> return CheckIn.Failed(reply.message, reply.retry)
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
            is Reply.Refused -> return CheckIn.Failed(answer.message, answer.retry)
        }
        return when (reply.text("status")) {
            "ok" -> {
                val token = reply.text("license") ?: return CheckIn.Failed(SERVER_MESSAGE, retry = true)
                when (val check = withContext(Dispatchers.IO) { license.install(token) }) {
                    is LicenseCheck.Valid -> CheckIn.Done
                    is LicenseCheck.Rejected -> CheckIn.Failed("La licence reçue n'a pas pu être vérifiée (${check.why}).")
                }
            }
            "revoked", "not_member", "unknown_device" -> {
                withContext(Dispatchers.IO) { license.revoke() }
                CheckIn.Revoked
            }
            "subscription_inactive" -> {
                withContext(Dispatchers.IO) { license.suspend() }
                CheckIn.Suspended
            }
            // The nonce expired between the two calls: a new one will do
            "bad_nonce" -> CheckIn.Failed(SERVER_MESSAGE, retry = true)
            // The server doesn't know this phone's key: trying again won't change that
            "bad_signature" -> CheckIn.Failed("Ce téléphone n'a pas pu prouver son identité. Contactez DistriGo.")
            else -> CheckIn.Failed(SERVER_MESSAGE)
        }
    }

    /** « Libérer cet appareil »: the seat is freed on the server; the license here is then forgotten. */
    suspend fun releaseThisDevice(): String? = licenseCalls.withLock { releaseNow() }

    private suspend fun releaseNow(): String? {
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
    suspend fun deleteAccount(): String? = licenseCalls.withLock { deleteNow() }

    private suspend fun deleteNow(): String? {
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

    /** An Edge Function, as the signed-in account (FunctionCalls). */
    private suspend fun call(function: String, body: JsonObject): Reply = functions.call(function, body)

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

    /** Too many codes refused within the hour: when the phone may try again, in its own time. */
    private fun tooManyAttempts(retryAt: Instant?): String =
        if (retryAt == null) {
            "Trop d'essais. Réessayez dans une heure."
        } else {
            "Trop d'essais. Réessayez après " +
                java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(retryAt.atZone(java.time.ZoneId.systemDefault())) + "."
        }

    private fun JsonObject.text(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString

    companion object {
        private const val SERVER_MESSAGE = FunctionCalls.SERVER_MESSAGE

        /** A suspended business, wherever the phone hears it: activation, check-in, a code. */
        const val SUSPENDED_MESSAGE = "L'abonnement est suspendu. Contactez DistriGo."

        /** What the device key signs: supabase/functions/_shared/protocol.ts, signedMessage. */
        internal fun signedMessage(purpose: String, nonce: String, installationId: String): ByteArray =
            "distrigo-$purpose\n$nonce\n$installationId".toByteArray(Charsets.UTF_8)
    }
}

/** Whether this state holds a license the phone verified: a check-in renews it, a new activation would break it. */
val LicenseState.holdsLicense: Boolean
    get() = heldLicense != null

/** The license this state was judged from, when there is one: its plan, its dates, its business. */
val LicenseState.heldLicense: License?
    get() = when (this) {
        is LicenseState.Judged -> license
        is LicenseState.CheckInRequired -> license
        is LicenseState.Suspended -> license
        is LicenseState.OtherBusiness -> license
        else -> null
    }
