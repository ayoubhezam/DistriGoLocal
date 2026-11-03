package com.distrigo.app.data.backup.cloud

import com.distrigo.app.data.backup.cloud.CloudBackupException.Problem
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/** Drive's failures, each turned into what the engine acts on: resume, retry later, alert, or reconnect. */
class GoogleDriveProviderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val drive = FakeDriveServer()
    private val tokens = CountingTokens()
    private val http = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    private val chunk = DriveClient.CHUNK_UNIT
    private val provider = GoogleDriveProvider(DriveClient(http, tokens, drive.baseUrl, chunk))

    private val meta = CloudBackupMeta("biz-1", "phone-A", 1_000, 64, "1.4.0", 99, "a2V5LWlkLWZvci10ZXN0")

    @After
    fun tearDown() = drive.server.shutdown()

    private fun file(size: Int) = File(folder.root, "b-$size.dgenc").apply { writeBytes(ByteArray(size) { (it % 251).toByte() }) }

    private fun problemOf(block: suspend () -> Unit): Problem = runBlocking {
        try {
            block()
            fail("No failure")
            throw IllegalStateException()
        } catch (e: CloudBackupException) {
            e.problem
        }
    }

    @Test
    fun cutUploadResumesWhereDriveStopped() = runBlocking {
        val source = file(3 * chunk + 100)
        var session: String? = null
        // The second chunk fails.
        val problem = try {
            provider.uploadBackup(source, meta, onSession = { session = it }, onProgress = { sent, _ ->
                if (sent == chunk.toLong()) drive.faults.addLast(drive.error(503))
            })
            null
        } catch (e: CloudBackupException) {
            e.problem
        }
        assertEquals(Problem.SERVER, problem)
        assertTrue(problem!!.retryable)
        assertEquals(chunk.toLong(), drive.chunkBytesReceived)

        val entry = provider.uploadBackup(source, meta, resume = session)
        // Only what was missing was sent again.
        assertEquals(source.length(), drive.chunkBytesReceived)
        assertArrayEquals(source.readBytes(), provider.openBackup(entry).use { it.readBytes() })
        assertEquals(1, drive.files.size)
    }

    @Test
    fun resumingAFinishedUploadReturnsTheFile() = runBlocking {
        val source = file(chunk + 10)
        var session: String? = null
        val first = provider.uploadBackup(source, meta, onSession = { session = it })
        // The app was killed after Drive finished but before it noted so: Drive still answers for the session.
        val again = provider.uploadBackup(source, meta, resume = session)
        assertEquals(first.id, again.id)
        assertEquals(1, drive.files.size)
    }

    @Test
    fun expiredSessionStartsOver() = runBlocking {
        val source = file(2 * chunk)
        val sessions = mutableListOf<String>()
        try {
            provider.uploadBackup(source, meta, onSession = { sessions += it }, onProgress = { sent, _ ->
                if (sent == chunk.toLong()) drive.faults.addLast(null)
            })
            fail("Connection cut expected")
        } catch (e: CloudBackupException) {
            assertEquals(Problem.NETWORK, e.problem)
        }
        drive.expireSessions()
        val entry = provider.uploadBackup(source, meta, resume = sessions.single(), onSession = { sessions += it })
        assertEquals(2, sessions.size)
        assertArrayEquals(source.readBytes(), provider.openBackup(entry).use { it.readBytes() })
    }

    @Test
    fun sessionExpiringMidUploadStartsOverOnce() = runBlocking {
        val source = file(3 * chunk)
        val sessions = mutableListOf<String>()
        val entry = provider.uploadBackup(source, meta, onSession = { sessions += it }, onProgress = { sent, _ ->
            if (sent == chunk.toLong() && sessions.size == 1) drive.expireSessions()
        })
        assertEquals(2, sessions.size)
        assertArrayEquals(source.readBytes(), provider.openBackup(entry).use { it.readBytes() })
    }

    @Test
    fun shortAcknowledgementIsSentAgain() = runBlocking {
        val source = file(2 * chunk + 7)
        drive.acknowledgeOnlyNext = 1000
        val entry = provider.uploadBackup(source, meta)
        assertArrayEquals(source.readBytes(), provider.openBackup(entry).use { it.readBytes() })
    }

    @Test
    fun corruptedStoreIsRemovedAndRetried() {
        drive.corruptNextUpload = true
        val problem = problemOf { provider.uploadBackup(file(chunk + 5), meta) }
        assertEquals(Problem.UPLOAD_CORRUPTED, problem)
        assertTrue(problem.retryable)
        assertTrue("bad copy deleted", drive.files.isEmpty())
    }

    @Test
    fun fullDriveStopsWithoutRetrying() {
        drive.faults.addLast(drive.error(403, "storageQuotaExceeded"))
        val problem = problemOf { provider.uploadBackup(file(100), meta) }
        assertEquals(Problem.QUOTA_EXCEEDED, problem)
        assertFalse(problem.retryable)
    }

    @Test
    fun rejectedTokenIsRefreshedOnce() = runBlocking {
        drive.acceptedToken = "token-2"
        tokens.next = "token-2"
        assertEquals("owner@gmail.com", provider.account())
        assertEquals(listOf("token-1"), tokens.refreshed)
    }

    @Test
    fun tokenRejectedTwiceNeedsReconnecting() {
        drive.acceptedToken = "token-3"
        tokens.next = "token-2"
        assertEquals(Problem.NEEDS_CONSENT, problemOf { provider.listBackups("biz-1") })
        assertEquals(1, tokens.refreshed.size)
    }

    @Test
    fun missingScopeNeedsReconnecting() {
        drive.faults.addLast(drive.error(403, "insufficientPermissions"))
        assertEquals(Problem.NEEDS_CONSENT, problemOf { provider.listBackups("biz-1") })
    }

    @Test
    fun busyOrBrokenDriveIsRetriedLater() {
        drive.faults.addLast(drive.error(429))
        assertEquals(Problem.RATE_LIMITED, problemOf { provider.listBackups("biz-1") })
        drive.faults.addLast(drive.error(403, "userRateLimitExceeded"))
        assertEquals(Problem.RATE_LIMITED, problemOf { provider.listBackups("biz-1") })
        drive.faults.addLast(drive.error(500))
        assertEquals(Problem.SERVER, problemOf { provider.listBackups("biz-1") })
        drive.faults.addLast(null)
        assertEquals(Problem.NETWORK, problemOf { provider.listBackups("biz-1") })
        drive.faults.addLast(drive.error(400, "badRequest"))
        assertEquals(Problem.REJECTED, problemOf { provider.listBackups("biz-1") })
    }

    @Test
    fun deletedBackupIsNotFound() = runBlocking {
        val entry = provider.uploadBackup(file(100), meta)
        drive.files.clear()
        assertEquals(Problem.NOT_FOUND, problemOf { provider.openBackup(entry) })
    }

    @Test
    fun rangeIgnoredByTheServerStillResumes() = runBlocking {
        val source = file(5_000)
        val entry = provider.uploadBackup(source, meta)
        drive.ignoreRange = true
        assertArrayEquals(source.readBytes().copyOfRange(1_234, 5_000), provider.openBackup(entry, 1_234).use { it.readBytes() })
    }

    @Test
    fun listsEveryPage() = runBlocking {
        drive.pageSize = 2
        repeat(5) { provider.uploadBackup(file(10 + it), meta.copy(createdAtMillis = it.toLong())) }
        assertEquals(5, provider.listBackups("biz-1").size)
    }

    @Test
    fun filesFromElsewhereAreIgnored() = runBlocking {
        provider.uploadBackup(file(10), meta)
        drive.files["x"] = FakeDriveServer.StoredFile("x", "odd", mapOf("kind" to "backup", "biz" to "biz-1"), ByteArray(3))
        assertEquals(1, provider.listBackups("biz-1").size)
    }

    @Test
    fun quotesInIdsCannotBreakTheQuery() = runBlocking {
        val odd = "o'brien\\x"
        provider.uploadBackup(file(10), meta.copy(businessId = odd))
        provider.uploadBackup(file(10), meta)
        assertEquals(odd, provider.listBackups(odd).single().meta.businessId)
        assertNull(provider.readKeyRing(odd))
    }

    @Test
    fun propertiesTooLongForDriveAreRefusedBeforeSending() {
        try {
            GoogleDriveProvider.propertiesOf(meta.copy(appVersion = "x".repeat(200)))
            fail("Accepted")
        } catch (e: IllegalArgumentException) {
            assertTrue(drive.files.isEmpty())
        }
    }

    @Test
    fun largePhotosGoUpResumably() = runBlocking {
        val big = file((DriveClient.SMALL_FILE_LIMIT + 10).toInt())
        val blob = provider.putBlob("biz-1", "big", big)
        assertEquals(big.length(), blob.size)
        assertEquals(big.length(), drive.chunkBytesReceived)
    }
}
