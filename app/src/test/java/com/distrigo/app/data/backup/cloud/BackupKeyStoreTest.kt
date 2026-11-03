package com.distrigo.app.data.backup.cloud

import com.distrigo.app.data.auth.SoftwareSessionCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The phone keeps the key sealed, for its own business only, and forgets it on « Délier ». */
class BackupKeyStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dir by lazy { File(folder.root, "cloud_backup") }
    private val store by lazy { BackupKeyStore(dir, SoftwareSessionCipher()) }
    private val key = BackupKey.generate()

    @Test
    fun keepsTheKeySealed() {
        store.saveKey("biz-1", key)
        assertTrue(store.loadKey("biz-1")!!.sameAs(key))
        val onDisk = File(dir, "key.bin").readBytes()
        assertFalse(String(onDisk, Charsets.ISO_8859_1).contains(base64Url(key.material)))
    }

    @Test
    fun neverHandsTheKeyToAnotherBusiness() {
        store.saveKey("biz-1", key)
        assertNull(store.loadKey("biz-2"))
    }

    @Test
    fun aLostKeystoreKeyReadsAsNoKey() {
        store.saveKey("biz-1", key)
        assertNull(BackupKeyStore(dir, SoftwareSessionCipher(seed = 2)).loadKey("biz-1"))
        File(dir, "key.bin").writeBytes(byteArrayOf(1, 2, 3))
        assertNull(store.loadKey("biz-1"))
    }

    @Test
    fun keepsTheRingForItsBusiness() {
        val ring = BackupKeyRing.create("biz-1", key, "Tournee2026!", RecoveryCode.generate(), 5, passwordIterations = 10, recoveryIterations = 10)
        store.saveKeyRing(ring)
        val read = store.loadKeyRing("biz-1")!!
        assertEquals(ring.toJson(), read.toJson())
        assertNull(store.loadKeyRing("biz-2"))
    }

    @Test
    fun forgetsEverythingOnUnlink() {
        store.saveKey("biz-1", key)
        store.saveKeyRing(BackupKeyRing.create("biz-1", key, "Tournee2026!", RecoveryCode.generate(), 5, passwordIterations = 10, recoveryIterations = 10))
        store.clear()
        assertNull(store.loadKey("biz-1"))
        assertNull(store.loadKeyRing("biz-1"))
        assertNull(BackupKeyStore(dir, SoftwareSessionCipher()).loadKey("biz-1"))
    }

    @Test
    fun nothingSavedReadsAsNothing() {
        assertNull(store.loadKey("biz-1"))
        assertNull(store.loadKeyRing("biz-1"))
    }
}
