package com.distrigo.app.data.backup.cloud

import com.distrigo.app.data.auth.SoftwareSessionCipher
import com.distrigo.app.data.backup.BackupFailedException
import com.distrigo.app.data.backup.cloud.CloudBackupException.Problem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The cloud run's decisions, day after day, on an in-memory cloud: skip, back up, resume, alert, keep. */
class CloudBackupEngineTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Algiers")
    private var now = Instant.parse("2026-11-03T02:30:00Z")
    private val cloud = FakeCloudBackupProvider()
    private var provider: CloudBackupProvider = cloud
    private val key = BackupKey.generate()

    private lateinit var store: CloudBackupStore
    private lateinit var keys: BackupKeyStore
    private lateinit var staging: File
    private lateinit var engine: CloudBackupEngine
    private val warnings = mutableListOf<String>()

    /** The data, as the engine sees it. */
    private inner class Data : CloudBackupSource {
        var business: String? = "biz-1"
        var fingerprint = "fp-1"
        var failWith: BackupFailedException? = null
        var created = 0

        /** The photos the data refers to, by content hash. */
        var photos = listOf<String>()

        override fun businessId() = business
        override fun fingerprint() = fingerprint
        override fun create(file: File): PlainBackup {
            failWith?.let { throw it }
            created++
            file.writeBytes(plainBytes(fingerprint))
            return PlainBackup(fingerprint, 64, "phone-A", now, photos)
        }

        override fun photoFile(sha256: String) = File(imagesDir, "$sha256.jpg")
    }

    private val imagesDir by lazy { File(folder.root, "images").apply { mkdirs() } }

    /** A photo on the phone, stored as the app stores it: named by the SHA-256 of its bytes. */
    private fun photo(seed: Int, size: Int = 3_000): String {
        val bytes = ByteArray(size) { (it * seed + 11).toByte() }
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        File(imagesDir, "$hash.jpg").writeBytes(bytes)
        return hash
    }

    private val photoIndex by lazy { CloudPhotoIndex(File(folder.root, "index")) }

    private val data = Data()

    private fun plainBytes(fingerprint: String) = ByteArray(5_000) { (it + fingerprint.hashCode()).toByte() }

    @Before
    fun setUp() {
        store = CloudBackupStore(File(folder.root, "state"))
        keys = BackupKeyStore(File(folder.root, "keys"), SoftwareSessionCipher())
        staging = File(folder.root, "staging")
        engine = CloudBackupEngine(
            store, keys, { provider }, data, staging, "1.4.0 (140)", photoIndex,
            clock = { now }, zone = { zone }, segmentSize = 1024, warn = { message, _ -> warnings += message },
        )
        keys.saveKey("biz-1", key)
        keys.saveKeyRing(BackupKeyRing.create("biz-1", key, "Tournee2026!", RecoveryCode.generate(), 0, passwordIterations = 10, recoveryIterations = 10))
        store.update { it.copy(linked = true, account = "owner@gmail.com", businessId = "biz-1", providerId = "fake") }
    }

    private fun run(force: Boolean = false) = runBlocking { engine.run(force) }

    private fun nextDay(changed: Boolean = true) {
        now = now.plus(Duration.ofDays(1))
        if (changed) data.fingerprint = "fp-$now"
    }

    private fun opened(entry: CloudBackupEntry): ByteArray = runBlocking {
        cloud.openBackup(entry).use { EncryptedBackupFile.reader(it, key).readBytes() }
    }

    @Test
    fun notLinkedDoesNothing() {
        store.update { CloudBackupState() }
        assertEquals(CloudBackupOutcome.NotLinked, run())
        assertEquals(0, data.created)
    }

    @Test
    fun firstRunSendsTheDataSealedWithTheBusinessKey() = runBlocking {
        val outcome = run() as CloudBackupOutcome.Saved
        assertFalse(outcome.resumed)
        val entry = cloud.listBackups("biz-1").single()
        assertArrayEquals(plainBytes("fp-1"), opened(entry))
        assertEquals("phone-A", entry.meta.deviceId)
        assertEquals(key.idText, entry.meta.keyId)
        assertEquals(5_000L, entry.meta.plainSize)
        assertEquals("1.4.0 (140)", entry.meta.appVersion)
        val state = store.read()
        assertEquals(entry.id, state.lastEntryId)
        assertEquals("fp-1", state.lastFingerprint)
        assertNull(state.pending)
        assertTrue("staging emptied", staging.listFiles().orEmpty().isEmpty())
        // The ring went up with the first backup: a new phone can open it.
        assertNotNull(cloud.readKeyRing("biz-1"))
        assertFalse("never the plain data in the cloud", cloud.openBackup(entry).use { it.readBytes() }.contentEquals(plainBytes("fp-1")))
    }

    @Test
    fun aDayWithoutChangesIsSkipped() = runBlocking {
        run()
        nextDay(changed = false)
        assertEquals(CloudBackupOutcome.Unchanged, run())
        assertEquals(1, data.created)
        assertEquals(1, cloud.listBackups("biz-1").size)
    }

    @Test
    fun backupNowSendsEvenWithoutChanges() = runBlocking {
        run()
        val second = run(force = true) as CloudBackupOutcome.Saved
        assertEquals(2, data.created)
        // Same day: retention keeps only the newer of the two.
        assertEquals(1, second.removed)
        assertEquals(second.entry.id, cloud.listBackups("biz-1").single().id)
    }

    @Test
    fun deletedFromDriveIsSentAgainAndTheRingPutBack() = runBlocking {
        run()
        cloud.wipe() // « Supprimer les données d'application cachées »
        nextDay(changed = false)
        assertTrue(run() is CloudBackupOutcome.Saved)
        assertEquals(1, cloud.listBackups("biz-1").size)
        assertNotNull(cloud.readKeyRing("biz-1"))
    }

    @Test
    fun ringDeletedAloneIsPutBackOnAnUnchangedDay() = runBlocking {
        run()
        val sent = cloud.readKeyRing("biz-1")!!
        cloud.deleteKeyRing("biz-1")
        nextDay(changed = false)
        assertEquals(CloudBackupOutcome.Unchanged, run())
        assertArrayEquals(sent, cloud.readKeyRing("biz-1"))
    }

    @Test
    fun refusesWithoutBusinessForAnotherBusinessOrWithoutKey() {
        data.business = null
        assertEquals(CloudBackupProblem.NO_BUSINESS, (run() as CloudBackupOutcome.Failed).problem)
        data.business = "biz-2"
        assertEquals(CloudBackupProblem.OTHER_BUSINESS, (run() as CloudBackupOutcome.Failed).problem)
        data.business = "biz-1"
        keys.clear()
        val failed = run() as CloudBackupOutcome.Failed
        assertEquals(CloudBackupProblem.NEEDS_PASSWORD, failed.problem)
        assertFalse(failed.retryable)
        assertEquals(0, data.created)
        assertTrue(runBlocking { cloud.listBackups("biz-1") }.isEmpty())
    }

    /** Fails the first upload after Drive opened a session; later uploads must come back with that session. */
    private inner class CuttingProvider(private val problem: Problem) : CloudBackupProvider by cloud {
        var cut = true
        val resumedWith = mutableListOf<String?>()
        override suspend fun uploadBackup(
            file: File,
            meta: CloudBackupMeta,
            resume: String?,
            onSession: (String) -> Unit,
            onProgress: (Long, Long) -> Unit,
        ): CloudBackupEntry {
            resumedWith += resume
            if (cut) {
                cut = false
                onSession("session-1")
                throw CloudBackupException(problem, "cut")
            }
            return cloud.uploadBackup(file, meta, resume, onSession, onProgress)
        }
    }

    @Test
    fun cutUploadResumesWithoutBuildingAgain() = runBlocking {
        val cutting = CuttingProvider(Problem.NETWORK)
        provider = cutting
        val failed = run() as CloudBackupOutcome.Failed
        assertEquals(CloudBackupProblem.NO_CONNECTION, failed.problem)
        assertTrue(failed.retryable)
        val pending = store.read().pending!!
        assertEquals("session-1", pending.session)
        assertTrue(File(staging, pending.fileName).isFile)

        data.fingerprint = "fp-later" // changed meanwhile: the pending backup still goes first
        val saved = run() as CloudBackupOutcome.Saved
        assertTrue(saved.resumed)
        assertEquals(1, data.created)
        assertEquals(listOf(null, "session-1"), cutting.resumedWith)
        assertEquals("fp-1", store.read().lastFingerprint)
        assertArrayEquals(plainBytes("fp-1"), opened(cloud.listBackups("biz-1").single()))

        // The next run sees the change.
        assertTrue(run() is CloudBackupOutcome.Saved)
        assertEquals(2, data.created)
    }

    @Test
    fun fullDriveIsNotRetried() {
        provider = CuttingProvider(Problem.QUOTA_EXCEEDED)
        val failed = run() as CloudBackupOutcome.Failed
        assertEquals(CloudBackupProblem.QUOTA_EXCEEDED, failed.problem)
        assertFalse(failed.retryable)
        assertTrue(failed.problem.needsTheUser)
    }

    @Test
    fun corruptedCopyStartsAFreshSession() = runBlocking {
        val cutting = CuttingProvider(Problem.UPLOAD_CORRUPTED)
        provider = cutting
        val failed = run() as CloudBackupOutcome.Failed
        assertTrue(failed.retryable)
        assertNull("its finished session would return the bad copy again", store.read().pending!!.session)
        assertTrue(run() is CloudBackupOutcome.Saved)
        assertEquals(listOf(null, null), cutting.resumedWith)
    }

    @Test
    fun pendingSealedWithAnOldKeyIsDropped() = runBlocking {
        provider = CuttingProvider(Problem.NETWORK)
        run()
        // Linked again with a new key: the old sealed file must not go up under it.
        val newKey = BackupKey.generate()
        keys.saveKey("biz-1", newKey)
        provider = cloud
        val saved = run() as CloudBackupOutcome.Saved
        assertFalse(saved.resumed)
        assertEquals(newKey.idText, saved.entry.meta.keyId)
        assertEquals(2, data.created)
    }

    @Test
    fun damagedDataIsReported() {
        data.failWith = BackupFailedException(BackupFailedException.Reason.DATABASE_DAMAGED, "integrity")
        val failed = run() as CloudBackupOutcome.Failed
        assertEquals(CloudBackupProblem.DATABASE_DAMAGED, failed.problem)
        assertNull(store.read().pending)
    }

    @Test
    fun keepsSevenDailiesAndFourWeekliesDayAfterDay() = runBlocking {
        // 2026-11-03 is a Tuesday. Forty nightly runs, each with changes, retention applied after each.
        repeat(40) {
            run()
            if (it < 39) nextDay()
        }
        val today = now.atZone(zone).toLocalDate()
        val kept = cloud.listBackups("biz-1").map { Instant.ofEpochMilli(it.meta.createdAtMillis).atZone(zone).toLocalDate() }
        // The last 7 days, all of them.
        assertEquals((0L..6L).map { today.minusDays(it) }, kept.take(7))
        // Then the newest of each earlier calendar week, back to 4 weeks: the Saturdays that close them.
        val thisWeek = CloudRetention.weekOf(today)
        val expectedWeeklies = (1L..3L).map { thisWeek.minusWeeks(it).plusDays(6) }.filter { it < today.minusDays(6) }
        assertEquals(expectedWeeklies, kept.drop(7))
        // Nothing older than four weeks.
        assertTrue(kept.all { CloudRetention.weekOf(it) >= thisWeek.minusWeeks(3) })
    }

    @Test
    fun recordsOneStreakPerRun() {
        val failed = CloudBackupOutcome.Failed(CloudBackupProblem.NO_CONNECTION, true, "x")
        engine.record(failed, scheduled = true)
        val second = engine.record(failed, scheduled = false)
        assertEquals(2, second.problemStreak)
        assertEquals(CloudBackupProblem.NO_CONNECTION, second.lastProblem)
        assertEquals(now, second.lastScheduledRunAt)
        val good = engine.record(CloudBackupOutcome.Unchanged, scheduled = true)
        assertEquals(0, good.problemStreak)
        assertNull(good.lastProblem)
    }

    // ---- photos (Phase 4) ----

    private fun blobs() = runBlocking { cloud.listBlobs("biz-1") }

    @Test
    fun photosGoOnceEachSealedUnderNamesThatRevealNothing() = runBlocking {
        val first = photo(3)
        val second = photo(5)
        data.photos = listOf(first, second)
        run()
        val stored = blobs()
        val nameKey = PhotoNames.nameKey(key)
        assertEquals(setOf(PhotoNames.name(nameKey, first), PhotoNames.name(nameKey, second)), stored.map { it.name }.toSet())
        assertTrue("no content hash in a name", stored.none { it.name.contains(first) || it.name.contains(second) })
        val blob = stored.single { it.name == PhotoNames.name(nameKey, first) }
        val sealed = cloud.openBlob(blob).use { it.readBytes() }
        val original = File(imagesDir, "$first.jpg").readBytes()
        assertFalse(sealed.contentEquals(original))
        assertArrayEquals(original, EncryptedBackupFile.reader(sealed.inputStream(), key).use { it.readBytes() })

        // The next backup uses the same photos: nothing is sent again.
        nextDay()
        run()
        assertEquals(stored.map { it.id }.toSet(), blobs().map { it.id }.toSet())
    }

    @Test
    fun aPhotoGoneOrDamagedIsLeftOutAndTheBackupStillGoes() {
        val kept = photo(3)
        val gone = photo(5).also { File(imagesDir, "$it.jpg").delete() }
        val damaged = photo(7).also { File(imagesDir, "$it.jpg").writeBytes(ByteArray(10)) }
        data.photos = listOf(kept, gone, damaged)
        assertTrue(run() is CloudBackupOutcome.Saved)
        assertEquals(listOf(PhotoNames.name(PhotoNames.nameKey(key), kept)), blobs().map { it.name })
        assertTrue(warnings.any { "no longer on the phone" in it })
        assertTrue(warnings.any { "no longer matches" in it })
    }

    @Test
    fun withPhotosOffNoneAreSent() {
        store.update { it.copy(includePhotos = false) }
        data.photos = listOf(photo(3))
        assertTrue(run() is CloudBackupOutcome.Saved)
        assertTrue(blobs().isEmpty())
    }

    @Test
    fun aPhotoNoBackupNeedsAnyMoreIsRemoved() = runBlocking {
        val old = photo(3)
        val current = photo(5)
        data.photos = listOf(old, current)
        run()
        assertEquals(2, blobs().size)
        // The photo was removed from the data; a new backup the same day replaces the first one.
        data.photos = listOf(current)
        data.fingerprint = "fp-2"
        val saved = run() as CloudBackupOutcome.Saved
        assertEquals(1, saved.removed)
        assertEquals(listOf(PhotoNames.name(PhotoNames.nameKey(key), current)), blobs().map { it.name })
    }

    @Test
    fun photoCleanupWaitsWhileABackupItDoesNotKnowRemains() = runBlocking {
        val old = photo(3)
        data.photos = listOf(old)
        run()
        // Another phone's backup of the same business (dated ahead, so retention keeps it): its photos are unknown here.
        val other = File(folder.root, "other.dgenc").apply { writeBytes(ByteArray(10)) }
        cloud.uploadBackup(other, CloudBackupMeta("biz-1", "phone-B", now.plus(Duration.ofDays(1)).toEpochMilli(), 64, "1.4.0", 10, key.idText))
        data.photos = emptyList()
        data.fingerprint = "fp-2"
        run()
        assertEquals("kept: the other phone's backup may need it", 1, blobs().size)
        assertTrue(warnings.any { "photo cleanup waits" in it })
    }

    @Test
    fun photosGoBeforeTheBackupAndAResumeDoesNotSendThemAgain() = runBlocking {
        val first = photo(3)
        data.photos = listOf(first)
        val cutting = CuttingProvider(Problem.NETWORK)
        provider = cutting
        assertTrue(run() is CloudBackupOutcome.Failed)
        assertEquals("the photo is up before the backup", 1, blobs().size)
        assertTrue(cloud.listBackups("biz-1").isEmpty())
        assertEquals(listOf(first), store.read().pending!!.photos)

        val before = blobs().map { it.id }
        assertTrue((run() as CloudBackupOutcome.Saved).resumed)
        assertEquals(before, blobs().map { it.id })
        val entry = cloud.listBackups("biz-1").single()
        assertEquals(setOf(PhotoNames.name(PhotoNames.nameKey(key), first)), photoIndex.read(key.idText)[entry.id])
    }

    @Test
    fun aFailedPhotoKeepsTheBackupBack() = runBlocking {
        data.photos = listOf(photo(3))
        cloud.failNext = null
        val failing = object : CloudBackupProvider by cloud {
            override suspend fun putBlob(businessId: String, name: String, file: File): CloudBlob =
                throw CloudBackupException(Problem.NETWORK, "cut")
        }
        provider = failing
        val failed = run() as CloudBackupOutcome.Failed
        assertTrue(failed.retryable)
        assertTrue("no backup without its photos", cloud.listBackups("biz-1").isEmpty())
        assertNotNull(store.read().pending)
    }

    @Test
    fun staleStagingIsCleared() {
        staging.mkdirs()
        File(staging, "old.dgenc").writeBytes(ByteArray(10))
        run()
        assertTrue(staging.listFiles().orEmpty().isEmpty())
    }
}
