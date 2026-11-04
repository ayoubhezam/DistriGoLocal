package com.distrigo.app.data.backup.cloud

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** One token per run: fetched once, reused, renewed near expiry or when Drive rejects it. */
class CachedTokensTest {

    private var now = 0L
    private var issued = 0
    private val forgotten = mutableListOf<String>()
    private val tokens = CachedTokens(
        fetch = { "token-${++issued}" },
        forget = { forgotten += it },
        clock = { now },
    )

    @Test
    fun oneTokenServesTheWholeRun() = runBlocking {
        repeat(10) { assertEquals("token-1", tokens.accessToken()) }
        assertEquals(1, issued)
    }

    @Test
    fun aTokenNearItsExpiryIsRenewed() = runBlocking {
        tokens.accessToken()
        now += CachedTokens.REUSE_FOR_MILLIS - 1
        assertEquals("token-1", tokens.accessToken())
        now += 1
        assertEquals("token-2", tokens.accessToken())
        assertEquals("expiry needs no forgetting", emptyList<String>(), forgotten)
    }

    @Test
    fun aRejectedTokenIsForgottenAndReplaced() = runBlocking {
        val first = tokens.accessToken()
        assertEquals("token-2", tokens.refresh(first))
        assertEquals(listOf("token-1"), forgotten)
        assertEquals("token-2", tokens.accessToken())
    }

    @Test
    fun requestsRejectedTogetherRefreshOnce() = runBlocking {
        val first = tokens.accessToken()
        val fresh = (1..5).map { async { tokens.refresh(first) } }.awaitAll()
        assertEquals(List(5) { "token-2" }, fresh)
        assertEquals(2, issued)
        assertEquals(listOf("token-1"), forgotten)
    }

    // ---- with the real Drive client: a whole backup asks for one token ----

    private val drive = FakeDriveServer()

    @After
    fun tearDown() = drive.server.shutdown()

    @Test
    fun aBackupRunOverDriveAsksForOneToken() = runBlocking {
        var fetched = 0
        val cached = CachedTokens(fetch = { fetched++; "token-1" })
        val provider = GoogleDriveProvider(DriveClient(OkHttpClient(), cached, drive.baseUrl, DriveClient.CHUNK_UNIT))
        val file = kotlin.io.path.createTempFile().toFile().apply { writeBytes(ByteArray(3 * DriveClient.CHUNK_UNIT + 10)); deleteOnExit() }
        val meta = CloudBackupMeta("biz-1", "phone-A", 1_000, 64, "1.4.0", 10, "key")
        provider.uploadBackup(file, meta)
        provider.listBackups("biz-1")
        provider.writeKeyRing("biz-1", ByteArray(5))
        provider.readKeyRing("biz-1")
        assertEquals("about ten Drive requests, one token", 1, fetched)
    }
}
