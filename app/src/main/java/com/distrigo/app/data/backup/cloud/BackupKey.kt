package com.distrigo.app.data.backup.cloud

import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * A business's backup key (the DEK of the plan, §3.3): 256 random bits that seal every cloud backup, and an id that
 * every sealed file carries so the wrong key is caught before any decryption.
 */
class BackupKey(id: ByteArray, material: ByteArray) {
    val id: ByteArray = id.copyOf()
    internal val material: ByteArray = material.copyOf()

    init {
        require(id.size == ID_SIZE) { "Key id size ${id.size}" }
        require(material.size == SIZE) { "Key size ${material.size}" }
    }

    /** The id as it appears in the key ring and Drive's `appProperties`. */
    val idText: String get() = base64Url(id)

    fun sameAs(other: BackupKey) = id.contentEquals(other.id) && material.contentEquals(other.material)

    override fun toString() = "BackupKey($idText)"

    companion object {
        const val SIZE = 32
        const val ID_SIZE = 16

        fun generate(random: SecureRandom = SecureRandom()) =
            BackupKey(ByteArray(ID_SIZE).also(random::nextBytes), ByteArray(SIZE).also(random::nextBytes))
    }
}

/** The backup password the user chooses (owner's decision §9.1: at least 8 characters). */
object BackupPassword {
    const val MIN_LENGTH = 8

    /** NFC, so the same password typed on two keyboards (Arabic letters with marks, accents) gives the same key. */
    fun normalize(password: String): String = Normalizer.normalize(password, Normalizer.Form.NFC)

    /** Counted in characters as the user sees them (code points), not UTF-16 units. */
    fun isLongEnough(password: String): Boolean = normalize(password).let { it.codePointCount(0, it.length) } >= MIN_LENGTH
}

/**
 * The printable recovery code: 120 random bits as 24 Crockford base-32 characters, shown in groups of four
 * (`7K2M-Q9XD-…`). It unlocks the backups when the password is forgotten.
 *
 * Typing is forgiving: case, spaces, dashes, O for 0, I or L for 1, and Arabic-Indic digits are all accepted.
 */
object RecoveryCode {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val LENGTH = 24
    private const val GROUP = 4

    fun generate(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(LENGTH * 5 / 8).also(random::nextBytes)
        val code = StringBuilder(LENGTH)
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                code.append(ALPHABET[(buffer shr bits) and 31])
            }
        }
        return format(code.toString())
    }

    /** `ABCD-EFGH-…` from the 24 characters. */
    fun format(normalized: String): String = normalized.chunked(GROUP).joinToString("-")

    /** The 24 canonical characters, or null if [typed] cannot be a recovery code. */
    fun normalize(typed: String): String? {
        val code = StringBuilder(LENGTH)
        for (char in typed) {
            val canonical = when (char) {
                ' ', '-', ' ', '‐', '‑', '‒', '–', '—', '−' -> continue
                in '٠'..'٩' -> '0' + (char - '٠')
                in '۰'..'۹' -> '0' + (char - '۰')
                'o', 'O' -> '0'
                'i', 'I', 'l', 'L' -> '1'
                else -> char.uppercaseChar()
            }
            if (canonical !in ALPHABET) return null
            code.append(canonical)
        }
        return code.toString().takeIf { it.length == LENGTH }
    }
}

/**
 * PBKDF2-HMAC-SHA256 (RFC 8018) on the UTF-8 bytes of the secret, written out on `Mac` rather than taken from
 * `SecretKeyFactory`: providers do not agree on how they turn non-ASCII password characters into bytes, and a key
 * made on one phone must open on any other.
 */
internal object Pbkdf2 {
    fun deriveKey(secret: String, salt: ByteArray, iterations: Int, length: Int = 32): ByteArray {
        require(iterations >= 1) { "Iterations $iterations" }
        require(secret.isNotEmpty()) { "Empty secret" } // HMAC keys cannot be empty in JCA; never the case here
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val out = ByteArray(length)
        val blocks = (length + 31) / 32
        for (block in 1..blocks) {
            mac.update(salt)
            mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            repeat(iterations - 1) {
                u = mac.doFinal(u)
                for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            val offset = (block - 1) * 32
            System.arraycopy(t, 0, out, offset, minOf(32, length - offset))
        }
        return out
    }
}

internal fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

internal fun fromBase64Url(text: String): ByteArray? =
    try {
        Base64.getUrlDecoder().decode(text)
    } catch (e: IllegalArgumentException) {
        null
    }
