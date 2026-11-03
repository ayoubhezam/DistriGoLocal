package com.distrigo.app.data.backup.cloud

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** What every [CloudBackupProvider] does, whatever moves the bytes. Run against Drive (fake server) and the fake. */
abstract class CloudBackupProviderContract {

    @get:Rule
    val folder = TemporaryFolder()

    protected abstract val provider: CloudBackupProvider

    @After
    open fun tearDown() = Unit

    protected fun file(size: Int, seed: Int = 1): File =
        File.createTempFile("backup", ".dgenc", folder.root).apply { writeBytes(ByteArray(size) { (it * 13 + seed).toByte() }) }

    protected fun meta(business: String = "biz-1", at: Long = 1_000, device: String = "phone-A") =
        CloudBackupMeta(business, device, at, 64, "1.4.0", 12_345, "a2V5LWlkLWZvci10ZXN0")

    @Test
    fun backupGoesUpAndComesBackWhole() = runBlocking {
        val source = file(700_000)
        val entry = provider.uploadBackup(source, meta())
        assertEquals(700_000L, entry.size)
        assertEquals(meta(), entry.meta)
        val listed = provider.listBackups("biz-1").single()
        assertEquals(entry.id, listed.id)
        assertEquals(meta(), listed.meta)
        assertArrayEquals(source.readBytes(), provider.openBackup(listed).use { it.readBytes() })
    }

    @Test
    fun downloadResumesFromAByte() = runBlocking {
        val source = file(10_000)
        val entry = provider.uploadBackup(source, meta())
        val tail = provider.openBackup(entry, fromByte = 6_000).use { it.readBytes() }
        assertArrayEquals(source.readBytes().copyOfRange(6_000, 10_000), tail)
    }

    @Test
    fun listsOnlyTheBusinessNewestFirst() = runBlocking {
        provider.uploadBackup(file(100), meta(at = 1_000))
        provider.uploadBackup(file(100), meta(at = 3_000, device = "phone-B"))
        provider.uploadBackup(file(100), meta(at = 2_000))
        provider.uploadBackup(file(100), meta(business = "biz-2", at = 9_000))
        provider.uploadBackup(file(100), meta(at = 4_000))
        val listed = provider.listBackups("biz-1")
        assertEquals(listOf(4_000L, 3_000L, 2_000L, 1_000L), listed.map { it.meta.createdAtMillis })
        assertEquals("phone-B", listed[1].meta.deviceId)
        assertEquals(1, provider.listBackups("biz-2").size)
        assertTrue(provider.listBackups("biz-3").isEmpty())
    }

    @Test
    fun deletingTwiceIsFine() = runBlocking {
        val entry = provider.uploadBackup(file(100), meta())
        provider.deleteBackup(entry)
        provider.deleteBackup(entry)
        assertTrue(provider.listBackups("biz-1").isEmpty())
    }

    @Test
    fun keyRingIsCreatedThenReplaced() = runBlocking {
        assertNull(provider.readKeyRing("biz-1"))
        provider.writeKeyRing("biz-1", "first".toByteArray())
        provider.writeKeyRing("biz-1", "second".toByteArray())
        provider.writeKeyRing("biz-2", "other".toByteArray())
        assertEquals("second", String(provider.readKeyRing("biz-1")!!))
        assertEquals("other", String(provider.readKeyRing("biz-2")!!))
    }

    @Test
    fun blobsAreKeptPerBusinessByName() = runBlocking {
        val photo = file(3_000, seed = 5)
        val blob = provider.putBlob("biz-1", "h4sh", photo)
        provider.putBlob("biz-2", "other", file(10))
        val listed = provider.listBlobs("biz-1").single()
        assertEquals("h4sh", listed.name)
        assertEquals(3_000L, listed.size)
        assertArrayEquals(photo.readBytes(), provider.openBlob(listed).use { it.readBytes() })
        provider.deleteBlob(blob)
        provider.deleteBlob(blob)
        assertTrue(provider.listBlobs("biz-1").isEmpty())
        assertEquals(1, provider.listBlobs("biz-2").size)
    }

    @Test
    fun knowsTheAccount() = runBlocking {
        assertEquals("owner@gmail.com", provider.account())
    }
}

class GoogleDriveProviderContractTest : CloudBackupProviderContract() {
    private val drive = FakeDriveServer()
    override val provider: CloudBackupProvider by lazy {
        // Small chunks: a 700 KB backup goes up in three requests, as a large one would in many.
        GoogleDriveProvider(DriveClient(okhttp3.OkHttpClient(), CountingTokens(), drive.baseUrl, DriveClient.CHUNK_UNIT))
    }

    override fun tearDown() = drive.server.shutdown()
}

class FakeCloudBackupProviderContractTest : CloudBackupProviderContract() {
    override val provider: CloudBackupProvider = FakeCloudBackupProvider()
}
