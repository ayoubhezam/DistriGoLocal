package com.distrigo.app.data.license

import com.google.gson.JsonObject
import java.security.PublicKey

/** Why a token is not a license for this phone. */
enum class Rejection {
    /** Not three base64url parts, a header that is not JSON, or claims missing or of the wrong kind. */
    MALFORMED,
    /** Anything but ES256: refuses `none`, and HS256 "verified" with the public key as its secret. */
    WRONG_ALGORITHM,
    /** Another kind of JWT, such as the auth session's. */
    WRONG_TYPE,
    /** Signed with a key this app does not pin. */
    UNKNOWN_KEY,
    BAD_SIGNATURE,
    WRONG_ISSUER,
    /** Issued to another app. */
    WRONG_PACKAGE,
    /** Issued to another installation: a copy of another phone's files. */
    WRONG_INSTALLATION,
    /** Issued to another device key: a copy, or a key the Keystore lost. */
    WRONG_DEVICE,
    /** This phone has no device key: never activated, or the Keystore was wiped. */
    NO_DEVICE_KEY,
    /** Older than a license this phone already accepted. */
    REPLAYED,
}

/** What a token turned out to be. */
sealed interface LicenseCheck {
    data class Valid(val license: License) : LicenseCheck
    data class Rejected(val why: Rejection) : LicenseCheck
}

/** What a license must say to be this phone's. */
data class LicenseBinding(
    val packageName: String,
    val installationId: String,
    val deviceKeyHash: String,
    /** The lowest `seq` still acceptable. */
    val minSeq: Long,
)

/**
 * Decides whether a token is a license for this phone, offline: docs/license_architecture.md §4.2.
 *
 * In this order: the shape and the header (exactly ES256, our `typ`, a pinned `kid`), then the signature,
 * and only then the claims, which are not read for any decision before the signature holds. The clock is
 * not this class's business: a license whose dates have passed is still valid *as a license*; what it
 * allows today is [LicenseRules]'s question.
 */
class LicenseVerifier(private val keys: Map<String, PublicKey>) {

    fun verify(token: String, binding: LicenseBinding): LicenseCheck {
        val jws = Jws.parse(token) ?: return rejected(Rejection.MALFORMED)
        if (jws.header.claimText("alg") != License.ALGORITHM) return rejected(Rejection.WRONG_ALGORITHM)
        if (jws.header.claimText("typ") != License.TYPE) return rejected(Rejection.WRONG_TYPE)
        val keyId = jws.header.claimText("kid") ?: return rejected(Rejection.UNKNOWN_KEY)
        val key = keys[keyId] ?: return rejected(Rejection.UNKNOWN_KEY)
        if (!LicenseCrypto.verifyEs256(key, jws.signingInput, jws.signature)) return rejected(Rejection.BAD_SIGNATURE)

        val claims = jws.payload as? JsonObject ?: return rejected(Rejection.MALFORMED)
        if (claims.claimText("iss") != License.ISSUER) return rejected(Rejection.WRONG_ISSUER)
        val license = License.fromClaims(claims, keyId, token) ?: return rejected(Rejection.MALFORMED)
        return when {
            license.packageName != binding.packageName -> rejected(Rejection.WRONG_PACKAGE)
            license.installationId != binding.installationId -> rejected(Rejection.WRONG_INSTALLATION)
            license.deviceKeyHash != binding.deviceKeyHash -> rejected(Rejection.WRONG_DEVICE)
            license.seq < binding.minSeq -> rejected(Rejection.REPLAYED)
            else -> LicenseCheck.Valid(license)
        }
    }

    private fun rejected(why: Rejection) = LicenseCheck.Rejected(why)
}
