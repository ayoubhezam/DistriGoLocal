package com.distrigo.app.data.license

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * A license as the server signed it: what this phone may do, until when, and how long it may stay offline.
 *
 * Only [LicenseVerifier] makes one, after checking the signature and that it names this phone. Times are
 * epoch milliseconds here; the token carries whole seconds (JWT NumericDate), as every JWT library writes
 * them. See docs/license_architecture.md §4.
 */
data class License(
    /** The server key that signed it (`kid`). */
    val keyId: String,
    /** The account (`sub`). */
    val userId: String,
    /** The business the subscription belongs to (`org`). */
    val businessId: String,
    /** SHA-256 of the device key's public key (`dev`): the phone it was issued to. */
    val deviceKeyHash: String,
    /** The installation it was issued to (`iid`, DeviceIdentity). */
    val installationId: String,
    val packageName: String,
    val plan: String,
    val features: Set<String>,
    val validFrom: Long,
    val validTo: Long,
    /** End of the grace period; recording stops after it. */
    val graceUntil: Long,
    /** The server's time when it signed: the anchor of [TrustedClock]. */
    val issuedAt: Long,
    /** The phone must check in again before this. */
    val offlineUntil: Long,
    /** Reboots allowed between two check-ins. */
    val maxReboots: Int,
    /** One more at every issue for this device: an older license can never replace a newer one. */
    val seq: Long,
    /** The token itself, as stored. */
    val token: String,
) {
    override fun toString(): String = "License(plan=$plan, seq=$seq, validTo=$validTo, offlineUntil=$offlineUntil)"

    companion object {
        const val ISSUER = "distrigo-license"
        const val TYPE = "distrigo-license+jwt"
        const val ALGORITHM = "ES256"

        /**
         * The license [claims] describe, or null if one is missing or of the wrong kind. Claims this version
         * does not know are ignored, so a later server can add some without stranding phones not yet updated.
         */
        internal fun fromClaims(claims: JsonObject, keyId: String, token: String): License? {
            try {
                return License(
                    keyId = keyId,
                    userId = claims.claimText("sub") ?: return null,
                    businessId = claims.claimText("org") ?: return null,
                    deviceKeyHash = claims.claimText("dev") ?: return null,
                    installationId = claims.claimText("iid") ?: return null,
                    packageName = claims.claimText("pkg") ?: return null,
                    plan = claims.claimText("plan") ?: return null,
                    features = claims.claimTexts("feat"),
                    validFrom = claims.claimMillis("vf") ?: return null,
                    validTo = claims.claimMillis("vt") ?: return null,
                    graceUntil = claims.claimMillis("gu") ?: return null,
                    issuedAt = claims.claimMillis("iat") ?: return null,
                    offlineUntil = claims.claimMillis("ou") ?: return null,
                    maxReboots = claims.claimNumber("mb")?.toInt() ?: return null,
                    seq = claims.claimNumber("seq") ?: return null,
                    token = token,
                )
            } catch (e: RuntimeException) {
                // A number that is not a whole one, or one too large to be a time.
                return null
            }
        }
    }
}

/** A compact JWS split and decoded, not yet trusted. */
internal class Jws(
    val header: JsonObject,
    /** The payload as decoded JSON: an object for a license, but nothing is assumed before the signature. */
    val payload: JsonElement,
    /** `header.payload` exactly as received: what the signature covers. */
    val signingInput: ByteArray,
    val signature: ByteArray,
) {
    companion object {
        /** The token's three parts, or null if it is not three base64url parts with a JSON object for header. */
        fun parse(token: String): Jws? {
            val parts = token.split('.')
            if (parts.size != 3) return null
            val header = LicenseCrypto.fromBase64Url(parts[0])?.let(::json) as? JsonObject ?: return null
            val payload = LicenseCrypto.fromBase64Url(parts[1])?.let(::json) ?: return null
            val signature = LicenseCrypto.fromBase64Url(parts[2]) ?: return null
            return Jws(header, payload, "${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII), signature)
        }

        private fun json(bytes: ByteArray): JsonElement? =
            try {
                JsonParser().parse(String(bytes, Charsets.UTF_8))
            } catch (e: RuntimeException) {
                null
            }
    }
}

private fun JsonObject.claimPrimitive(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive

/** A string member, or null if absent or not a string. */
internal fun JsonObject.claimText(key: String): String? = claimPrimitive(key)?.takeIf { it.isString }?.asString

private fun JsonObject.claimNumber(key: String): Long? = claimPrimitive(key)?.takeIf { it.isNumber }?.asLong

private fun JsonObject.claimMillis(key: String): Long? = claimNumber(key)?.let { Math.multiplyExact(it, 1000L) }

private fun JsonObject.claimTexts(key: String): Set<String> =
    (get(key) as? JsonArray)?.mapNotNull { element -> element.takeIf { it.isJsonPrimitive }?.asString }?.toSet()
        ?: emptySet()
