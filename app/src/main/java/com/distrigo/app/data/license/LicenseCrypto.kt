package com.distrigo.app.data.license

import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * The few cryptographic operations the license needs, on the platform's own providers: no crypto library.
 *
 * Plain JVM code, so the unit tests run exactly what the phone runs.
 */
internal object LicenseCrypto {

    /** Base64url without padding, as JWS writes every part. */
    fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /** The bytes of a base64url text, or null if it is not one. Padding is tolerated, other characters are not. */
    fun fromBase64Url(text: String): ByteArray? =
        try {
            Base64.getUrlDecoder().decode(text)
        } catch (e: IllegalArgumentException) {
            null
        }

    /**
     * How a license names a device key: SHA-256 of its SubjectPublicKeyInfo (`PublicKey.encoded`), base64url.
     * The server computes the same from the attested certificate.
     */
    fun keyHash(subjectPublicKeyInfo: ByteArray): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(subjectPublicKeyInfo))

    /** An EC public key from its SubjectPublicKeyInfo, in standard base64 (what `openssl` and Node print). */
    fun publicKey(subjectPublicKeyInfoBase64: String): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(subjectPublicKeyInfoBase64)))

    /**
     * Whether [signature] is a valid ES256 signature of [data] by [key].
     *
     * JWS writes an ES256 signature as the two 32-byte integers R and S side by side; Java's verifier reads
     * DER. Anything other than exactly 64 bytes is refused rather than guessed at: a DER signature where a JWS
     * one belongs means a server bug, not a license.
     */
    fun verifyEs256(key: PublicKey, data: ByteArray, signature: ByteArray): Boolean {
        if (signature.size != ES256_SIGNATURE_SIZE) return false
        return try {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(key)
                update(data)
                verify(derSignature(signature))
            }
        } catch (e: GeneralSecurityException) {
            false
        }
    }

    /** `SEQUENCE { INTEGER r, INTEGER s }` from JWS's raw R‖S. */
    fun derSignature(raw: ByteArray): ByteArray {
        require(raw.size == ES256_SIGNATURE_SIZE)
        val r = derInteger(raw.copyOfRange(0, 32))
        val s = derInteger(raw.copyOfRange(32, 64))
        // At most 2 × 35 bytes: the length always fits the short form.
        return byteArrayOf(0x30, (r.size + s.size).toByte()) + r + s
    }

    /** A DER INTEGER of an unsigned big-endian value: leading zeros dropped, one added back if the top bit is set. */
    private fun derInteger(unsigned: ByteArray): ByteArray {
        var start = 0
        while (start < unsigned.size - 1 && unsigned[start] == 0.toByte()) start++
        var value = unsigned.copyOfRange(start, unsigned.size)
        if (value[0] < 0) value = byteArrayOf(0) + value
        return byteArrayOf(0x02, value.size.toByte()) + value
    }

    const val ES256_SIGNATURE_SIZE = 64
}
