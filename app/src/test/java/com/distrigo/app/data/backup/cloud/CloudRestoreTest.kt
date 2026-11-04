package com.distrigo.app.data.backup.cloud

import com.distrigo.app.data.auth.SoftwareSessionCipher
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Restore on a new phone, over the fake Drive server (real HTTP, Range downloads): a backup made on a phone linked with
 * the password, and one made on a phone that joined with the recovery code, both restored on a third phone with
 * either secret, give back exactly the same data and photos.
 */
class CloudRestoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val drive = FakeDriveServer()
    private val http = OkHttpClient.Builder().retryOnConnectionFailure(false).build()
    private val account = "owner@gmail.com"
    private val password = "Tournee2026!"
    private val zone = ZoneId.of("Africa/Algiers")
    private var now = Instant.parse("2026-11-04T02:30:00Z")

    /** Every phone talks to the same Drive account; [wrap] lets a test cut a phone's downloads. */
    private var wrap: (CloudBackupProvider) -> CloudBackupProvider = { it }
    private val providers: (String) -> CloudBackupProvider = {
        wrap(GoogleDriveProvider(DriveClient(http, CountingTokens(), drive.baseUrl, DriveClient.CHUNK_UNIT)))
    }

    /** The business's data: the same bytes and photos on every phone that backs it up. */
    private val data = ByteArray(700_000) { (it * 7 + it / 1000).toByte() }
    private val photoBytes = listOf(ByteArray(120_000) { (it * 3).toByte() }, ByteArray(90_000) { (it * 5 + 1).toByte() })
    private val photoHashes = photoBytes.map(::sha256)

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** A phone: its own key store, state, photos and staging, the same business data. */
    private inner class Phone(name: String, val device: String) {
        val root = File(folder.root, name)
        val images = File(root, "images").apply { mkdirs() }
        val store = CloudBackupStore(File(root, "state"))
        val keys = BackupKeyStore(File(root, "keys"), SoftwareSessionCipher())
        val link = CloudBackupLink(store, keys, providers, clock = { now }, passwordIterations = 1_000, recoveryIterations = 100)
        val restore = CloudRestore(providers, File(root, "restore"), images)

        fun withPhotos() = apply { photoBytes.zip(photoHashes).forEach { (bytes, hash) -> File(images, "$hash.jpg").writeBytes(bytes) } }

        val engine = CloudBackupEngine(
            store, keys, providers,
            object : CloudBackupSource {
                override fun businessId() = "biz-1"
                override fun fingerprint() = "fp-$device-$now"
                override fun create(file: File): PlainBackup {
                    file.writeBytes(data)
                    return PlainBackup("fp-$device-$now", 64, device, now, photoHashes)
                }
                override fun photoFile(sha256: String) = File(images, "$sha256.jpg")
            },
            File(root, "staging"), "1.4.0 (140)", CloudPhotoIndex(File(root, "index")),
            clock = { now }, zone = { zone }, segmentSize = 64 * 1024, warn = { _, _ -> },
        )
    }

    private lateinit var recoveryCode: String
    private lateinit var backupByPassword: CloudBackupEntry
    private lateinit var backupByCode: CloudBackupEntry

    @Before
    fun twoPhonesBackUp() = runBlocking {
        // Phone A links with the password: the key and its recovery code are made there.
        val phoneA = Phone("phone-a", "device-A").withPhotos()
        recoveryCode = phoneA.link.createKey(account, "biz-1", password)
        backupByPassword = (phoneA.engine.run() as CloudBackupOutcome.Saved).entry

        // Phone B joins with the recovery code only, and backs up the same data the next day.
        now = now.plus(Duration.ofDays(1))
        val phoneB = Phone("phone-b", "device-B").withPhotos()
        val ring = phoneB.link.existingRing(account, "biz-1")!!
        assertTrue(phoneB.link.join(account, ring, recoveryCode))
        backupByCode = (phoneB.engine.run() as CloudBackupOutcome.Saved).entry
        assertEquals(2, drive.files.values.count { it.properties["kind"] == "backup" })
    }

    @After
    fun tearDown() = drive.server.shutdown()

    private fun restored(phone: Phone, entry: CloudBackupEntry, secret: String): ByteArray = runBlocking {
        val ring = phone.restore.keyRing(account, "biz-1")
        val key = phone.restore.unlock(ring, secret)!!
        phone.restore.download(account, entry, key).readBytes()
    }

    @Test
    fun backupsMadeWithThePasswordAndWithTheCodeRestoreToIdenticalData() = runBlocking {
        val phoneC = Phone("phone-c", "device-C")
        val ring = phoneC.restore.keyRing(account, "biz-1")
        val listed = phoneC.restore.backups(account, ring)
        assertEquals(listOf("device-B", "device-A"), listed.map { it.first.meta.deviceId })
        assertTrue("both open with this ring", listed.all { it.second })

        val byPassword = restored(phoneC, backupByPassword, password)
        val byCode = restored(phoneC, backupByCode, recoveryCode)
        assertArrayEquals("the password backup restores the data", data, byPassword)
        assertArrayEquals("the recovery code backup restores the data", data, byCode)
        assertArrayEquals(byPassword, byCode)

        // Crosswise, and the code typed the way people type it.
        val typed = RecoveryCode.normalize(recoveryCode)!!.lowercase().chunked(4).joinToString(" ")
        assertArrayEquals(data, restored(phoneC, backupByPassword, typed))
        assertArrayEquals(data, restored(phoneC, backupByCode, password))
    }

    @Test
    fun photosComeBackIdenticalWhicheverSecretOpenedTheKey() = runBlocking {
        for ((name, secret) in listOf("by-password" to password, "by-code" to recoveryCode)) {
            val phone = Phone(name, name)
            val key = phone.restore.unlock(phone.restore.keyRing(account, "biz-1"), secret)!!
            val fetch = phone.restore.fetchPhotos(account, "biz-1", key)
            assertEquals(PhotoFetch(fetched = 2, alreadyHere = 0, rejected = 0), fetch)
            photoBytes.zip(photoHashes).forEach { (bytes, hash) ->
                assertArrayEquals("$name: $hash", bytes, File(phone.images, "$hash.jpg").readBytes())
            }
            assertTrue("no temporary files left", phone.images.listFiles()!!.all { it.name.endsWith(".jpg") && !it.name.startsWith(".") })
            // Again: nothing to download.
            assertEquals(PhotoFetch(0, 2, 0), phone.restore.fetchPhotos(account, "biz-1", key))
        }
    }

    @Test
    fun wrongSecretsOpenNothing() = runBlocking {
        val phone = Phone("phone-c", "device-C")
        val ring = phone.restore.keyRing(account, "biz-1")
        assertNull(phone.restore.unlock(ring, "Tournee2026?"))
        assertNull(phone.restore.unlock(ring, RecoveryCode.generate()))
    }

    @Test
    fun aCutDownloadContinuesWhereItStopped() = runBlocking {
        val phone = Phone("phone-c", "device-C")
        val key = phone.restore.unlock(phone.restore.keyRing(account, "biz-1"), password)!!
        var cutOnce = true
        val starts = mutableListOf<Long>()
        wrap = { real ->
            object : CloudBackupProvider by real {
                override suspend fun openBackup(entry: CloudBackupEntry, fromByte: Long): InputStream {
                    starts += fromByte
                    val stream = real.openBackup(entry, fromByte)
                    if (!cutOnce) return stream
                    cutOnce = false
                    return object : FilterInputStream(stream) {
                        var read = 0L
                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            if (read > 300_000) throw IOException("Wi-Fi lost")
                            return super.read(b, off, len).also { if (it > 0) read += it }
                        }
                    }
                }
            }
        }
        try {
            phone.restore.download(account, backupByPassword, key)
            fail("cut expected")
        } catch (e: CloudBackupException) {
            assertEquals(CloudBackupException.Problem.NETWORK, e.problem)
        }
        val plain = phone.restore.download(account, backupByPassword, key)
        assertArrayEquals(data, plain.readBytes())
        assertEquals(0L, starts[0])
        assertTrue("resumed past the bytes already here", starts[1] > 300_000)
    }

    @Test
    fun anAlteredBackupIsRefused() = runBlocking {
        val stored = drive.files.getValue(backupByPassword.id)
        stored.bytes = stored.bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        val phone = Phone("phone-c", "device-C")
        val key = phone.restore.unlock(phone.restore.keyRing(account, "biz-1"), password)!!
        // The entry as listed now (Drive reports the MD5 of what it holds).
        val entry = phone.restore.backups(account, phone.restore.keyRing(account, "biz-1")).first { it.first.id == backupByPassword.id }.first
        try {
            phone.restore.download(account, entry, key)
            fail("altered backup accepted")
        } catch (e: CloudRestoreException) {
            assertEquals(CloudRestoreException.Problem.DAMAGED, e.problem)
        }
        assertFalse(File(phone.root, "restore/restore.distrigo").exists())
    }

    @Test
    fun aSwappedPhotoIsRefused() = runBlocking {
        val blobs = drive.files.values.filter { it.properties["kind"] == "blob" }
        val first = blobs[0].bytes
        blobs[0].bytes = blobs[1].bytes
        blobs[1].bytes = first
        val phone = Phone("phone-c", "device-C")
        val key = phone.restore.unlock(phone.restore.keyRing(account, "biz-1"), recoveryCode)!!
        assertEquals(PhotoFetch(0, 0, 2), phone.restore.fetchPhotos(account, "biz-1", key))
        assertTrue(phone.images.listFiles()!!.isEmpty())
    }

    @Test
    fun aBackupSealedWithAnotherKeyIsNamed() = runBlocking {
        val phone = Phone("phone-c", "device-C")
        val key = phone.restore.unlock(phone.restore.keyRing(account, "biz-1"), password)!!
        val older = backupByPassword.copy(meta = backupByPassword.meta.copy(keyId = BackupKey.generate().idText))
        try {
            phone.restore.download(account, older, key)
            fail("other key accepted")
        } catch (e: CloudRestoreException) {
            assertEquals(CloudRestoreException.Problem.OTHER_KEY, e.problem)
        }
    }

    @Test
    fun anAccountWithoutBackupsForTheBusinessSaysSo() = runBlocking {
        val phone = Phone("phone-c", "device-C")
        try {
            phone.restore.keyRing(account, "biz-2")
            fail("no ring expected")
        } catch (e: CloudRestoreException) {
            assertEquals(CloudRestoreException.Problem.NO_KEY_RING, e.problem)
        }
    }

    @Test
    fun afterRestoringThePhoneCarriesOnUnderTheSameKey() = runBlocking {
        val phone = Phone("phone-c", "device-C").withPhotos()
        val ring = phone.restore.keyRing(account, "biz-1")
        assertTrue(phone.link.join(account, ring, recoveryCode))
        now = now.plus(Duration.ofDays(1))
        val saved = phone.engine.run() as CloudBackupOutcome.Saved
        assertEquals(ring.keyId, saved.entry.meta.keyId)
        assertEquals("photos shared, none sent again", 2, drive.files.values.count { it.properties["kind"] == "blob" })
    }
}
