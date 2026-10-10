package com.distrigo.app.data.license

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.random.Random

class LicenseCryptoTest {

    private val keys = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    /** R‖S from a DER signature: the inverse of [LicenseCrypto.derSignature], written independently. */
    private fun raw(der: ByteArray): ByteArray {
        var at = 2 // SEQUENCE, length
        fun integer(): ByteArray {
            check(der[at] == 0x02.toByte())
            val length = der[at + 1].toInt()
            val value = der.copyOfRange(at + 2, at + 2 + length)
            at += 2 + length
            return value.dropWhile { it == 0.toByte() }.toByteArray().let { ByteArray(32 - it.size) + it }
        }
        return integer() + integer()
    }

    @Test
    fun `a JWS signature converts back to exactly the DER Java signs`() {
        // Hundreds of signatures meet every case: R or S with the top bit set, and with leading zero bytes.
        repeat(500) { i ->
            val data = "message $i".toByteArray()
            val der = Signature.getInstance("SHA256withECDSA").run {
                initSign(keys.private)
                update(data)
                sign()
            }
            val jws = raw(der)
            assertEquals(64, jws.size)
            assertArrayEquals(der, LicenseCrypto.derSignature(jws))
            assertTrue(LicenseCrypto.verifyEs256(keys.public, data, jws))
            assertFalse(LicenseCrypto.verifyEs256(keys.public, "another message".toByteArray(), jws))
        }
    }

    @Test
    fun `leading zeros are dropped and a high bit gets its zero byte`() {
        val r = ByteArray(32).also { it[31] = 1 }               // 0x00…01
        val s = ByteArray(32) { 0x7F }.also { it[0] = 0x80.toByte() } // 0x80 7F…
        val der = LicenseCrypto.derSignature(r + s)
        val expected = byteArrayOf(0x30, (3 + 35).toByte(), 0x02, 0x01, 0x01, 0x02, 33, 0x00) + s
        assertArrayEquals(expected, der)
    }

    @Test
    fun `a signature that is not exactly 64 bytes is refused, not guessed at`() {
        val data = "x".toByteArray()
        for (size in listOf(0, 63, 65, 70, 72)) {
            assertFalse(LicenseCrypto.verifyEs256(keys.public, data, Random(size).nextBytes(size)))
        }
    }

    @Test
    fun `base64url without padding, and anything else is not base64url`() {
        val bytes = byteArrayOf(-5, -1, 0, 62, 63)
        val text = LicenseCrypto.base64Url(bytes)
        assertFalse(text.contains('=') || text.contains('+') || text.contains('/'))
        assertArrayEquals(bytes, LicenseCrypto.fromBase64Url(text))
        assertNull(LicenseCrypto.fromBase64Url("ab+/"))
        assertNull(LicenseCrypto.fromBase64Url("e30!"))
    }
}
