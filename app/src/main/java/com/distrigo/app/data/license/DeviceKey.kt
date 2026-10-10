package com.distrigo.app.data.license

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.ProviderException
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/** A new device key, and what the server needs to judge it. */
class CreatedKey(
    val publicKeyHash: String,
    /** DER certificates, the key's own first. With [attested], Google's attestation of it, up to a Google root. */
    val certificateChain: List<ByteArray>,
    val strongBox: Boolean,
    val attested: Boolean,
)

/**
 * This phone's key in the Android Keystore: what a license is bound to (docs/license_architecture.md §4.5).
 *
 * The private key never leaves the Keystore, so a copy of the app's files on another phone carries the
 * license but not the key, and the license names a key that phone does not have. At a check-in the phone
 * signs the server's nonce with it, which a copy cannot do.
 *
 * No screen lock is required to use it: many field phones have none, and a key bound to the lock is wiped
 * when the lock changes. The alias never changes, so an update keeps the key (and the license with it).
 *
 * Every call is Keystore I/O: off the main thread.
 */
class DeviceKey(private val alias: String = ALIAS) {

    @Volatile
    private var hash: String? = null

    /** The hash licenses name this phone by, or null when there is no key: never activated, or the Keystore lost it. */
    fun publicKeyHash(): String? =
        hash ?: keyStore().getCertificate(alias)?.publicKey?.encoded?.let(LicenseCrypto::keyHash)?.also { hash = it }

    /**
     * Makes a new key, replacing any, attested with the server's [challenge].
     *
     * In StrongBox when the phone has one (API 28+), otherwise in the TEE. A phone that cannot attest at all
     * still gets a key, without attestation: refusing it would strand a paying customer, and the server
     * decides what an unattested key is worth (§5.3).
     */
    fun create(challenge: ByteArray): CreatedKey {
        val attempts = buildList<Pair<Boolean, ByteArray?>> {
            if (Build.VERSION.SDK_INT >= 28) add(true to challenge)
            add(false to challenge)
            add(false to null)
        }
        var failure: ProviderException? = null
        for ((strongBox, attestWith) in attempts) {
            delete()
            try {
                generate(strongBox, attestWith)
            } catch (e: ProviderException) {
                // StrongBoxUnavailableException is one; so is a phone failing to make its attestation.
                failure = e
                continue
            }
            val store = keyStore()
            val publicKey = store.getCertificate(alias).publicKey.encoded
            return CreatedKey(
                publicKeyHash = LicenseCrypto.keyHash(publicKey).also { hash = it },
                certificateChain = store.getCertificateChain(alias).map { it.encoded },
                strongBox = strongBox,
                attested = attestWith != null,
            )
        }
        throw failure ?: ProviderException("no device key could be made")
    }

    /** An ECDSA (SHA-256) signature of [data], DER, as Java writes it. Throws if there is no key. */
    fun sign(data: ByteArray): ByteArray {
        val key = keyStore().getKey(alias, null) as? PrivateKey ?: throw IllegalStateException("no device key")
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(key)
            update(data)
            sign()
        }
    }

    fun delete() {
        keyStore().deleteEntry(alias)
        hash = null
    }

    private fun generate(strongBox: Boolean, challenge: ByteArray?) {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .apply { if (challenge != null) setAttestationChallenge(challenge) }
            .apply { if (strongBox && Build.VERSION.SDK_INT >= 28) setIsStrongBoxBacked(true) }
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).run {
            initialize(spec)
            generateKeyPair()
        }
    }

    companion object {
        const val ALIAS = "distrigo_device_v1"
    }
}

/**
 * [StateSeal] with an HMAC-SHA256 key that never leaves the Keystore: without root, the anchor state cannot
 * be edited and re-sealed. If the Keystore loses the key, a new one is made and the old state reads as
 * altered, which a check-in repairs.
 */
class KeystoreSeal(private val alias: String = ALIAS) : StateSeal {

    @Volatile
    private var key: SecretKey? = null

    override fun tag(data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(key())
            doFinal(data)
        }

    private fun key(): SecretKey = key ?: synchronized(this) {
        key ?: ((keyStore().getKey(alias, null) as? SecretKey) ?: create()).also { key = it }
    }

    private fun create(): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE).run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN).build())
            generateKey()
        }

    companion object {
        const val ALIAS = "distrigo_anchor_mac"
    }
}

private const val KEYSTORE = "AndroidKeyStore"

private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
