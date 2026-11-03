package com.distrigo.app.data.backup.cloud

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** The backup key opens with the password or the recovery code, and with nothing else. */
class BackupKeyRingTest {

    private val key = BackupKey.generate()
    private val code = RecoveryCode.generate()
    private val password = "Tournee2026!"

    // Low counts keep the tests fast; the real counts are stored in the ring and checked below.
    private fun ring(pw: String = password, business: String = "biz-1") =
        BackupKeyRing.create(business, key, pw, code, 1_762_000_000_000, passwordIterations = 1_000, recoveryIterations = 100)

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun pbkdf2MatchesThePublishedVectors() {
        // PBKDF2-HMAC-SHA256, P = "password", S = "salt", dkLen = 32.
        val salt = "salt".toByteArray()
        assertEquals("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b", hex(Pbkdf2.deriveKey("password", salt, 1)))
        assertEquals("ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43", hex(Pbkdf2.deriveKey("password", salt, 2)))
        assertEquals("c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a", hex(Pbkdf2.deriveKey("password", salt, 4096)))
    }

    @Test
    fun pbkdf2MatchesTheJvmForAsciiAndLongerOutputs() {
        val salt = ByteArray(16) { it.toByte() }
        val jvm = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec("Tournee2026!".toCharArray(), salt, 777, 48 * 8)).encoded
        assertEquals(hex(jvm), hex(Pbkdf2.deriveKey("Tournee2026!", salt, 777, 48)))
    }

    @Test
    fun opensWithThePassword() {
        val opened = ring().unlockWithPassword(password)
        assertNotNull(opened)
        assertTrue(opened!!.sameAs(key))
    }

    @Test
    fun opensWithTheRecoveryCodeHoweverItIsTyped() {
        val ring = ring()
        val plain = RecoveryCode.normalize(code)!!
        assertTrue(ring.unlockWithRecoveryCode(code)!!.sameAs(key))
        assertTrue(ring.unlockWithRecoveryCode(plain.lowercase())!!.sameAs(key))
        assertTrue(ring.unlockWithRecoveryCode(plain.chunked(4).joinToString(" "))!!.sameAs(key))
        val sloppy = plain.replace('0', 'O').replace('1', 'l')
        assertTrue(ring.unlockWithRecoveryCode(sloppy)!!.sameAs(key))
        val arabicDigits = plain.map { if (it in '0'..'9') '٠' + (it - '0') else it }.joinToString("")
        assertTrue(ring.unlockWithRecoveryCode(arabicDigits)!!.sameAs(key))
    }

    @Test
    fun wrongSecretsOpenNothing() {
        val ring = ring()
        assertNull(ring.unlockWithPassword("Tournee2026?"))
        assertNull(ring.unlockWithPassword(""))
        assertNull(ring.unlockWithRecoveryCode(RecoveryCode.generate()))
        assertNull(ring.unlockWithRecoveryCode("not a code"))
        assertNull(ring.unlockWithRecoveryCode(code.dropLast(1)))
        // The password is not the code, and the code is not the password.
        assertNull(ring.unlockWithPassword(code))
    }

    @Test
    fun accentsTypedEitherWayGiveTheSamePassword() {
        val composed = "Café-dépôt"
        val decomposed = "Café-dépôt"
        assertTrue(ring(composed).unlockWithPassword(decomposed)!!.sameAs(key))
    }

    @Test
    fun arabicPasswordsWork() {
        val arabic = "مستودع الجزائر"
        assertTrue(ring(arabic).unlockWithPassword(arabic)!!.sameAs(key))
    }

    @Test
    fun passwordsUnderEightCharactersAreRefused() {
        assertFalse(BackupPassword.isLongEnough("1234567"))
        assertTrue(BackupPassword.isLongEnough("12345678"))
        // Eight characters as the user sees them, even outside the basic plane.
        assertTrue(BackupPassword.isLongEnough("🚚".repeat(8)))
        assertFalse(BackupPassword.isLongEnough("🚚".repeat(4)))
        try {
            ring("1234567")
            fail("Short password accepted")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun newPasswordKeepsTheKeyAndTheRecoveryCode() {
        val changed = ring().withNewPassword(key, "Nouveau-mot-2027")
        assertNull(changed.unlockWithPassword(password))
        assertTrue(changed.unlockWithPassword("Nouveau-mot-2027")!!.sameAs(key))
        assertTrue(changed.unlockWithRecoveryCode(code)!!.sameAs(key))
        assertEquals(key.idText, changed.keyId)
    }

    @Test
    fun survivesJson() {
        val read = BackupKeyRing.fromJson(ring().toJson())
        assertEquals("biz-1", read.businessId)
        assertEquals(key.idText, read.keyId)
        assertEquals(1_762_000_000_000, read.createdAtMillis)
        assertTrue(read.unlockWithPassword(password)!!.sameAs(key))
        assertTrue(read.unlockWithRecoveryCode(code)!!.sameAs(key))
    }

    @Test
    fun realRingsUseTheStrongCounts() {
        val json = JsonParser().parse(
            BackupKeyRing.create("biz-1", key, password, code, 0, recoveryIterations = 1, passwordIterations = BackupKeyRing.PASSWORD_ITERATIONS).toJson(),
        ).asJsonObject
        assertEquals(600_000, json.getAsJsonObject("password").get("iterations").asInt)
        assertEquals(600_000, BackupKeyRing.PASSWORD_ITERATIONS)
    }

    @Test
    fun aRingMovedToAnotherBusinessOpensNothing() {
        val json = JsonParser().parse(ring().toJson()).asJsonObject.apply { addProperty("businessId", "biz-2") }
        val moved = BackupKeyRing.fromJson(json.toString())
        assertNull(moved.unlockWithPassword(password))
        assertNull(moved.unlockWithRecoveryCode(code))
    }

    @Test
    fun slotsCannotBeSwapped() {
        val json = JsonParser().parse(ring().toJson()).asJsonObject
        val password = json.getAsJsonObject("password")
        json.add("password", json.getAsJsonObject("recovery"))
        json.add("recovery", password)
        val swapped = BackupKeyRing.fromJson(json.toString())
        assertNull(swapped.unlockWithPassword(code))
        assertNull(swapped.unlockWithRecoveryCode(code))
    }

    @Test
    fun unreadableRingsAreRefused() {
        fun refused(text: String, newer: Boolean = false) {
            try {
                BackupKeyRing.fromJson(text)
                fail("Accepted: $text")
            } catch (e: BackupKeyRingException) {
                assertEquals(text, newer, e.unsupportedVersion)
            }
        }
        val good = JsonParser().parse(ring().toJson()).asJsonObject
        fun edited(change: JsonObject.() -> Unit) = good.deepCopy().apply(change).toString()

        refused("")
        refused("not json")
        refused("[]")
        refused(edited { addProperty("format", 2) }, newer = true)
        refused(edited { addProperty("kdf", "argon2id") }, newer = true)
        refused(edited { remove("recovery") })
        refused(edited { addProperty("keyId", "short") })
        refused(edited { getAsJsonObject("password").addProperty("iterations", 2_000_000_000) })
        refused(edited { getAsJsonObject("password").addProperty("iterations", 0) })
        refused(edited { getAsJsonObject("password").addProperty("sealed", "AAAA") })
    }

    @Test
    fun recoveryCodesAreGroupedAndRandom() {
        val first = RecoveryCode.generate()
        assertEquals(29, first.length)
        assertTrue(Regex("([0-9A-HJKMNP-TV-Z]{4}-){5}[0-9A-HJKMNP-TV-Z]{4}").matches(first))
        assertEquals(RecoveryCode.LENGTH, RecoveryCode.normalize(first)!!.length)
        assertNotEquals(first, RecoveryCode.generate())
        assertNull(RecoveryCode.normalize(first + "A"))
        assertNull(RecoveryCode.normalize(first.replaceFirst(Regex("[A-Z]"), "U")))
    }
}
