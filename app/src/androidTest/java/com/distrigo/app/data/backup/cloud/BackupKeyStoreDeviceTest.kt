package com.distrigo.app.data.backup.cloud

import android.content.Context
import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.auth.KeystoreSessionCipher
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/**
 * The backup key sealed by the phone's real Android Keystore, under a test alias ([ALIAS]) and in a test folder, so
 * the app's own key (`distrigo_cloud_backup_v1`) and files are never touched. Also times the real password derivation.
 */
@RunWith(AndroidJUnit4::class)
class BackupKeyStoreDeviceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "cloud-key-store-device-test")

    private fun store() = BackupKeyStore(dir, KeystoreSessionCipher(ALIAS))

    @Before
    @After
    fun cleanUp() {
        dir.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
    }

    @Test
    fun theKeyIsSealedByTheKeystoreAndReadBackByANewInstance() {
        val key = BackupKey.generate()
        store().saveKey("biz-1", key)

        assertTrue("another instance, as after an app restart", store().loadKey("biz-1")!!.sameAs(key))
        assertNull(store().loadKey("biz-2"))
        val onDisk = File(dir, "key.bin").readBytes()
        assertFalse(String(onDisk, Charsets.ISO_8859_1).contains(base64Url(key.material)))
        assertFalse(onDisk.toList().windowed(BackupKey.SIZE).any { it.toByteArray().contentEquals(key.material) })
    }

    @Test
    fun theKeystoreKeyIsHardwareBackedAndNeverLeavesIt() {
        store().saveKey("biz-1", BackupKey.generate())
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val secret = keystore.getKey(ALIAS, null) as SecretKey
        assertNull("the key material is not exportable", secret.encoded)
        val info = SecretKeyFactory.getInstance(secret.algorithm, "AndroidKeyStore").getKeySpec(secret, KeyInfo::class.java) as KeyInfo
        val level = if (Build.VERSION.SDK_INT >= 31) info.securityLevel else -1
        Log.i(TAG, "Keystore key on ${Build.MODEL}: securityLevel=$level (1 TEE, 2 StrongBox, 0 software), keySize=${info.keySize}")
        // An emulator's Keystore is software only; a real phone's must be hardware.
        org.junit.Assume.assumeFalse("emulator", Build.HARDWARE.contains("ranchu") || Build.MODEL.startsWith("sdk_gphone"))
        if (Build.VERSION.SDK_INT >= 31) {
            assertTrue("in secure hardware, not software", level == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT || level == KeyProperties.SECURITY_LEVEL_STRONGBOX)
        }
    }

    @Test
    fun aLostKeystoreKeyReadsAsNoKey() {
        store().saveKey("biz-1", BackupKey.generate())
        // What a factory reset of the Keystore, or a key invalidated by Android, leaves behind.
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
        assertNull(store().loadKey("biz-1"))
    }

    @Test
    fun ringIsKeptAndEverythingIsForgottenOnUnlink() {
        val key = BackupKey.generate()
        val ring = BackupKeyRing.create("biz-1", key, "Tournee2026!", RecoveryCode.generate(), 0, passwordIterations = 10, recoveryIterations = 10)
        store().saveKey("biz-1", key)
        store().saveKeyRing(ring)
        assertNotNull(store().loadKeyRing("biz-1"))
        store().clear()
        assertNull(store().loadKey("biz-1"))
        assertNull(store().loadKeyRing("biz-1"))
    }

    /** The real cost of a password check (600 000 iterations), on this phone: linking and restore only. */
    @Test
    fun passwordDerivationTimeOnThisPhone() {
        val key = BackupKey.generate()
        val code = RecoveryCode.generate()
        val created = System.nanoTime()
        val ring = BackupKeyRing.create("biz-1", key, "Tournee2026!", code, 0)
        val createMs = (System.nanoTime() - created) / 1_000_000
        val opened = System.nanoTime()
        val unlocked = ring.unlockWithPassword("Tournee2026!")
        val unlockMs = (System.nanoTime() - opened) / 1_000_000
        val coded = System.nanoTime()
        ring.unlockWithRecoveryCode(code)
        val codeMs = (System.nanoTime() - coded) / 1_000_000
        Log.i(TAG, "PBKDF2 on ${Build.MODEL}: create ring ${createMs} ms, unlock with password ${unlockMs} ms, with code ${codeMs} ms")
        assertTrue(unlocked!!.sameAs(key))
        assertTrue("a password check must stay bearable", unlockMs < 15_000)
    }

    private companion object {
        const val TAG = "CloudKeyDeviceTest"
        const val ALIAS = "distrigo_cloud_backup_devicetest"
    }
}
