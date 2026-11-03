package com.distrigo.app.data.backup.cloud

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The key ring (`keyring.json`, plan §3.3): the business's [BackupKey], sealed twice, once by a key derived from
 * the backup password and once by a key derived from the recovery code. Safe to store anywhere (Drive, the phone):
 * without one of the two secrets it reveals nothing.
 *
 * Each slot seals the key with AES-256-GCM and authenticates the format, the slot, the key id and the business, so
 * a slot cannot be swapped into another ring.
 */
class BackupKeyRing internal constructor(
    val businessId: String,
    val keyId: String,
    val createdAtMillis: Long,
    internal val password: Slot,
    internal val recovery: Slot,
) {
    internal class Slot(val iterations: Int, val salt: ByteArray, val nonce: ByteArray, val sealed: ByteArray)

    /** The key, or null when [password] is not this ring's password. Slow on purpose (about a second). */
    fun unlockWithPassword(password: String): BackupKey? = open(this.password, PASSWORD_SLOT, BackupPassword.normalize(password))

    /** The key, or null when [typed] is not this ring's recovery code (or not a recovery code at all). */
    fun unlockWithRecoveryCode(typed: String): BackupKey? =
        RecoveryCode.normalize(typed)?.let { open(recovery, RECOVERY_SLOT, it) }

    /** The same key and recovery code under a new password: backups already sent stay readable. */
    fun withNewPassword(key: BackupKey, newPassword: String, random: SecureRandom = SecureRandom()): BackupKeyRing {
        require(key.idText == keyId) { "Not this ring's key" }
        require(BackupPassword.isLongEnough(newPassword)) { "Password too short" }
        val slot = seal(key, PASSWORD_SLOT, BackupPassword.normalize(newPassword), password.iterations, random)
        return BackupKeyRing(businessId, keyId, createdAtMillis, slot, recovery)
    }

    fun toJson(): String = JsonObject().apply {
        addProperty("format", FORMAT)
        addProperty("businessId", businessId)
        addProperty("keyId", keyId)
        addProperty("createdAt", createdAtMillis)
        addProperty("kdf", KDF)
        add(PASSWORD_SLOT, password.toJson())
        add(RECOVERY_SLOT, recovery.toJson())
    }.toString()

    private fun open(slot: Slot, name: String, secret: String): BackupKey? {
        if (secret.isEmpty()) return null
        val wrapping = Pbkdf2.deriveKey(secret, slot.salt, slot.iterations)
        return try {
            val material = slotCipher(Cipher.DECRYPT_MODE, wrapping, slot.nonce, aad(name)).doFinal(slot.sealed)
            if (material.size != BackupKey.SIZE) null else BackupKey(fromBase64Url(keyId)!!, material)
        } catch (e: AEADBadTagException) {
            null
        } finally {
            wrapping.fill(0)
        }
    }

    private fun seal(key: BackupKey, name: String, secret: String, iterations: Int, random: SecureRandom): Slot =
        sealSlot(key, businessId, name, secret, iterations, random)

    private fun aad(name: String) = aad(businessId, keyId, name)

    companion object {
        const val FORMAT = 1
        const val FILE_NAME = "keyring.json"
        private const val KDF = "pbkdf2-hmac-sha256"
        private const val PASSWORD_SLOT = "password"
        private const val RECOVERY_SLOT = "recovery"

        /**
         * OWASP's figure for PBKDF2-HMAC-SHA256 (2023). The password is the weak secret: each guess must cost this
         * much. Stored in the ring, so it can be raised later without touching existing backups.
         */
        const val PASSWORD_ITERATIONS = 600_000

        /** The recovery code already holds 120 random bits; the derivation only has to be sound, not slow. */
        const val RECOVERY_ITERATIONS = 10_000

        /** A ring from Drive is untrusted input: a huge count would hang the phone. */
        private const val MAX_ITERATIONS = 10_000_000
        private const val SALT_SIZE = 16
        private const val NONCE_SIZE = 12

        /** A new ring for [key]. Both secrets are checked by the caller's screen; this only refuses what is invalid. */
        fun create(
            businessId: String,
            key: BackupKey,
            password: String,
            recoveryCode: String,
            createdAtMillis: Long,
            random: SecureRandom = SecureRandom(),
            passwordIterations: Int = PASSWORD_ITERATIONS,
            recoveryIterations: Int = RECOVERY_ITERATIONS,
        ): BackupKeyRing {
            require(BackupPassword.isLongEnough(password)) { "Password too short" }
            val code = requireNotNull(RecoveryCode.normalize(recoveryCode)) { "Not a recovery code" }
            return BackupKeyRing(
                businessId,
                key.idText,
                createdAtMillis,
                sealSlot(key, businessId, PASSWORD_SLOT, BackupPassword.normalize(password), passwordIterations, random),
                sealSlot(key, businessId, RECOVERY_SLOT, code, recoveryIterations, random),
            )
        }

        /** Reads a ring; throws [BackupKeyRingException] when the text is not one this app can use. */
        fun fromJson(text: String): BackupKeyRing {
            val json = try {
                JsonParser().parse(text).asJsonObject
            } catch (e: Exception) {
                throw BackupKeyRingException(unsupportedVersion = false, "Not JSON")
            }
            val format = json.int("format")
            if (format != FORMAT) throw BackupKeyRingException(unsupportedVersion = format > FORMAT, "Format $format")
            val kdf = json.string("kdf")
            if (kdf != KDF) throw BackupKeyRingException(unsupportedVersion = kdf.isNotEmpty(), "KDF $kdf")
            val keyId = json.string("keyId")
            if (fromBase64Url(keyId)?.size != BackupKey.ID_SIZE) throw BackupKeyRingException(false, "Key id")
            return BackupKeyRing(
                businessId = json.string("businessId").ifEmpty { throw BackupKeyRingException(false, "Business") },
                keyId = keyId,
                createdAtMillis = json.long("createdAt"),
                password = slotFromJson(json.get(PASSWORD_SLOT) as? JsonObject),
                recovery = slotFromJson(json.get(RECOVERY_SLOT) as? JsonObject),
            )
        }

        private fun sealSlot(
            key: BackupKey,
            businessId: String,
            name: String,
            secret: String,
            iterations: Int,
            random: SecureRandom,
        ): Slot {
            val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
            val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
            val wrapping = Pbkdf2.deriveKey(secret, salt, iterations)
            try {
                val sealed = slotCipher(Cipher.ENCRYPT_MODE, wrapping, nonce, aad(businessId, key.idText, name))
                    .doFinal(key.material)
                return Slot(iterations, salt, nonce, sealed)
            } finally {
                wrapping.fill(0)
            }
        }

        private fun aad(businessId: String, keyId: String, slot: String) =
            "DistriGo backup key ring $FORMAT|$slot|$keyId|$businessId".toByteArray(Charsets.UTF_8)

        private fun slotCipher(mode: Int, wrapping: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(wrapping, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(aad)
            }

        private fun Slot.toJson() = JsonObject().apply {
            addProperty("iterations", iterations)
            addProperty("salt", base64Url(salt))
            addProperty("nonce", base64Url(nonce))
            addProperty("sealed", base64Url(sealed))
        }

        private fun slotFromJson(json: JsonObject?): Slot {
            if (json == null) throw BackupKeyRingException(false, "Slot missing")
            val iterations = json.int("iterations")
            if (iterations < 1 || iterations > MAX_ITERATIONS) throw BackupKeyRingException(false, "Iterations")
            val salt = fromBase64Url(json.string("salt"))
            val nonce = fromBase64Url(json.string("nonce"))
            val sealed = fromBase64Url(json.string("sealed"))
            if (salt == null || salt.isEmpty() || nonce?.size != NONCE_SIZE || sealed?.size != BackupKey.SIZE + 16) {
                throw BackupKeyRingException(false, "Slot")
            }
            return Slot(iterations, salt, nonce, sealed)
        }

        private fun JsonObject.string(name: String): String =
            try {
                get(name)?.takeIf { it.isJsonPrimitive }?.asString ?: ""
            } catch (e: Exception) {
                ""
            }

        private fun JsonObject.long(name: String): Long =
            try {
                get(name)?.asLong ?: throw BackupKeyRingException(false, name)
            } catch (e: BackupKeyRingException) {
                throw e
            } catch (e: Exception) {
                throw BackupKeyRingException(false, name)
            }

        private fun JsonObject.int(name: String): Int = long(name).let {
            if (it < Int.MIN_VALUE || it > Int.MAX_VALUE) throw BackupKeyRingException(false, name) else it.toInt()
        }
    }
}

/** A key ring this app cannot read: damaged, or ([unsupportedVersion]) written by a newer DistriGo. */
class BackupKeyRingException(val unsupportedVersion: Boolean, message: String) : Exception(message)
