package com.distrigo.app.data.license

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * The parts of the license only a real Keystore can test: the device key and the seal.
 *
 * Sandboxed: its own Keystore aliases and its own folder, so the app's device key, seal and license are never
 * touched. Run with `am instrument -e class com.distrigo.app.data.license.DeviceKeyTest`, never with a
 * connected* task (it uninstalls the app).
 */
@RunWith(AndroidJUnit4::class)
class DeviceKeyTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val key = DeviceKey(alias = "distrigo_device_test")
    private val sealAlias = "distrigo_anchor_mac_test"
    private val folder = File(context.cacheDir, "license-test").apply { deleteRecursively() }

    @After
    fun tearDown() {
        key.delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(sealAlias)
        folder.deleteRecursively()
    }

    private fun certificate(der: ByteArray) =
        CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate

    @Test
    fun a_new_key_is_named_by_the_hash_of_its_certificate_s_public_key() {
        val created = key.create("challenge".toByteArray())
        Log.i(TAG, "strongBox=${created.strongBox} attested=${created.attested} chain=${created.certificateChain.size}")
        val leaf = certificate(created.certificateChain.first())
        assertEquals(LicenseCrypto.keyHash(leaf.publicKey.encoded), created.publicKeyHash)
        assertEquals(created.publicKeyHash, key.publicKeyHash())
        // Read back from the Keystore, as after a restart or an update.
        assertEquals(created.publicKeyHash, DeviceKey(alias = "distrigo_device_test").publicKeyHash())
    }

    @Test
    fun the_attestation_carries_the_server_s_challenge_up_a_chain_that_holds() {
        val challenge = ByteArray(32) { (it * 7 + 3).toByte() }
        val created = key.create(challenge)
        assumeTrue("this phone could not attest", created.attested)
        val chain = created.certificateChain.map(::certificate)
        assertTrue(chain.size > 1)
        val description = chain.first().getExtensionValue(KEY_DESCRIPTION_OID)
        assertNotNull("no attestation extension", description)
        assertTrue("challenge not in the attestation", indexOf(description!!, challenge) >= 0)
        for (i in 0 until chain.size - 1) chain[i].verify(chain[i + 1].publicKey)
    }

    @Test
    fun a_signature_verifies_with_the_public_key() {
        val created = key.create(byteArrayOf(1))
        val data = "nonce|installation|7".toByteArray()
        val signature = key.sign(data)
        val verified = Signature.getInstance("SHA256withECDSA").run {
            initVerify(certificate(created.certificateChain.first()).publicKey)
            update(data)
            verify(signature)
        }
        assertTrue(verified)
    }

    @Test
    fun a_new_key_replaces_the_old_one_and_a_deleted_key_is_gone() {
        val first = key.create(byteArrayOf(1))
        val second = key.create(byteArrayOf(2))
        assertNotEquals(first.publicKeyHash, second.publicKeyHash)
        assertEquals(second.publicKeyHash, key.publicKeyHash())
        key.delete()
        assertNull(key.publicKeyHash())
        assertTrue(runCatching { key.sign(byteArrayOf(1)) }.isFailure)
    }

    @Test
    fun the_keystore_seal_reads_back_its_state_and_sees_an_edit() {
        val state = AnchorState(floor = 1_791_619_202_000, elapsed = 3_600_000, boot = 41, reboots = 0,
            anchoredAt = 1_791_619_200_000, maxSeq = 7)
        LicenseStore(folder, KeystoreSeal(sealAlias)).save(state)
        // Another instance, as after a restart: the same Keystore key.
        assertEquals(state, LicenseStore(folder, KeystoreSeal(sealAlias)).load())

        val file = File(folder, "anchor.state")
        val (body, tag) = file.readLines()
        file.writeText(body.replace("\"max_seq\":7", "\"max_seq\":1") + "\n" + tag + "\n")
        assertNull(LicenseStore(folder, KeystoreSeal(sealAlias)).load())
    }

    @Test
    fun the_app_s_own_manager_starts_without_a_license() {
        // Reads only: no license is installed on the phone before Phase L4.
        val state = LicenseManager.forApp(context).evaluate()
        Log.i(TAG, "forApp: $state")
        assertTrue(state is LicenseState.NoLicense)
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int =
        (0..haystack.size - needle.size).firstOrNull { start ->
            needle.indices.all { haystack[start + it] == needle[it] }
        } ?: -1

    private companion object {
        const val TAG = "DeviceKeyTest"
        /** Android's KeyDescription: the attestation record inside the key's certificate. */
        const val KEY_DESCRIPTION_OID = "1.3.6.1.4.1.11129.2.1.17"
    }
}
