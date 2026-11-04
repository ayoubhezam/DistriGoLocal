package com.distrigo.app.data.backup.cloud

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.distrigo.app.data.auth.KeystoreSessionCipher
import com.distrigo.app.data.backup.BackupArchive
import com.distrigo.app.data.backup.BackupCreator
import com.distrigo.app.data.backup.Verification
import com.distrigo.app.data.license.DataBusiness
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.local.database.withDeviceIdentity
import com.distrigo.app.data.local.database.withMigrationPolicy
import kotlinx.coroutines.runBlocking
import org.junit.After
import com.distrigo.app.data.backup.BackupFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.KeyStore
import java.time.Instant
import java.util.UUID

/**
 * The cloud backup run on the phone, end to end except Drive: the test's own database [TEST_DB] backed up by the real
 * [BackupCreator] (VACUUM INTO, zip, read back), sealed with a key held by the real Keystore (test alias), sent to an
 * in-memory cloud, then read back and checked as a `.distrigo`. The worker runs as WorkManager runs it, and the
 * schedule is checked on WorkManager's in-memory test instance, so the app's own data, key and jobs are never touched.
 */
@RunWith(AndroidJUnit4::class)
class CloudBackupDeviceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, TEST_DIR)
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var store: CloudBackupStore
    private lateinit var keys: BackupKeyStore
    private lateinit var engine: CloudBackupEngine
    private val cloud = MemoryCloud()
    private var failUploads: CloudBackupException? = null
    private val alerted = mutableListOf<CloudBackupState>()

    /** Drive, in memory: just enough for the engine. */
    private inner class MemoryCloud : CloudBackupProvider {
        override val id = "memory"
        val backups = LinkedHashMap<String, Pair<CloudBackupEntry, ByteArray>>()
        var ring: ByteArray? = null
        private var next = 1

        override suspend fun account() = "owner@gmail.com"
        override suspend fun listBackups(businessId: String) =
            backups.values.map { it.first }.filter { it.meta.businessId == businessId }.sortedByDescending { it.meta.createdAtMillis }

        override suspend fun uploadBackup(
            file: File,
            meta: CloudBackupMeta,
            resume: String?,
            onSession: (String) -> Unit,
            onProgress: (Long, Long) -> Unit,
        ): CloudBackupEntry {
            failUploads?.let { throw it }
            val bytes = file.readBytes()
            val entry = CloudBackupEntry("b${next++}", bytes.size.toLong(), null, meta)
            backups[entry.id] = entry to bytes
            return entry
        }

        override suspend fun openBackup(entry: CloudBackupEntry, fromByte: Long): InputStream =
            ByteArrayInputStream(backups.getValue(entry.id).second)

        override suspend fun deleteBackup(entry: CloudBackupEntry) {
            backups.remove(entry.id)
        }

        override suspend fun readKeyRing(businessId: String) = ring
        override suspend fun writeKeyRing(businessId: String, bytes: ByteArray) {
            ring = bytes
        }

        val blobs = LinkedHashMap<String, Pair<CloudBlob, ByteArray>>()

        override suspend fun listBlobs(businessId: String) = blobs.values.map { it.first }
        override suspend fun putBlob(businessId: String, name: String, file: File): CloudBlob {
            val blob = CloudBlob("p${next++}", name, file.length())
            blobs[blob.id] = blob to file.readBytes()
            return blob
        }

        override suspend fun openBlob(blob: CloudBlob): InputStream = ByteArrayInputStream(blobs.getValue(blob.id).second)
        override suspend fun deleteBlob(blob: CloudBlob) {
            blobs.remove(blob.id)
        }
    }

    private val business = object : DataBusiness {
        override fun read() = "biz-device-test"
        override fun bind(businessId: String) = Unit
    }

    private val alerts = object : CloudBackupAlerts {
        override fun update(state: CloudBackupState) {
            alerted += state
        }
    }

    @Before
    fun setUp() {
        cleanUp()
        db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .withMigrationPolicy()
            .withChangeTracking()
            .withDeviceIdentity { "7e57de71-0000-4000-8000-000000000020" }
            .build()
        sql = db.openHelper.writableDatabase
        val creator = BackupCreator(db, context.getDatabasePath(TEST_DB), File(root, "images"), File(root, "work"), context.contentResolver, "9.9 (99)", "Test Phone")
        store = CloudBackupStore(File(root, "state"))
        keys = BackupKeyStore(File(root, "keys"), KeystoreSessionCipher(ALIAS))
        engine = CloudBackupEngine(
            store, keys, { cloud }, AppCloudBackupSource(db, creator, business, File(root, "images")), File(root, "staging"), "9.9 (99)",
            CloudPhotoIndex(File(root, "index")),
        )
        // As CloudBackupLink leaves a phone after linking (its key derivation is timed in BackupKeyStoreDeviceTest).
        val key = BackupKey.generate()
        keys.saveKey("biz-device-test", key)
        keys.saveKeyRing(BackupKeyRing.create("biz-device-test", key, "Tournee2026!", RecoveryCode.generate(), 0, passwordIterations = 10, recoveryIterations = 10))
        store.update { it.copy(linked = true, account = "owner@gmail.com", businessId = "biz-device-test", providerId = "memory") }
    }

    @After
    fun cleanUp() {
        if (::db.isInitialized && db.isOpen) db.close()
        context.deleteDatabase(TEST_DB)
        root.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
    }

    private fun product(name: String, image: String? = null) = sql.execSQL(
        "INSERT INTO products (name, selling_price, purchase_price, stock, min_stock, unit_type, packages, pack_size, " +
            "has_expiry, camion_stock, image_uri, uuid) VALUES (?, 120.0, 100.0, 0, 0, 'piece', 0, 1, 0, 0, ?, ?)",
        arrayOf(name, image, UUID.randomUUID().toString()),
    )

    /** A photo stored the way ImageStore stores one: named for the SHA-256 of its bytes. */
    private fun photo(seed: Int): String {
        val bytes = ByteArray(150_000) { (it * seed % 251).toByte() }
        val hash = BackupFormat.sha256(bytes.inputStream())
        File(root, "images").apply { mkdirs() }.resolve("$hash.jpg").writeBytes(bytes)
        return hash
    }

    @Test
    fun aProductPhotoGoesUpOnceSealedAndTheBackupLeavesItOut() = runBlocking {
        val hash = photo(3)
        product("Lait Candia 1L", "img:$hash")
        product("Lait Candia 1L (pack)", "img:$hash")
        val saved = engine.run() as CloudBackupOutcome.Saved

        val key = keys.loadKey("biz-device-test")!!
        val (blob, sealed) = cloud.blobs.values.single()
        assertEquals(PhotoNames.name(PhotoNames.nameKey(key), hash), blob.name)
        val original = File(root, "images/$hash.jpg").readBytes()
        assertArrayEquals(original, EncryptedBackupFile.reader(ByteArrayInputStream(sealed), key).use { it.readBytes() })

        val plain = EncryptedBackupFile.reader(ByteArrayInputStream(cloud.backups.getValue(saved.entry.id).second), key).use { it.readBytes() }
        val manifest = (BackupArchive.verify(ByteArrayInputStream(plain)) as Verification.Verified).manifest
        assertTrue("the photo is not inside the backup file", manifest.entries.none { it.name.contains(hash) })

        product("Yaourt Soummam")
        engine.run()
        assertEquals("sent once", 1, cloud.blobs.size)
    }

    private fun worker(manual: Boolean, attempt: Int = 0): CloudBackupWorker =
        TestListenableWorkerBuilder<CloudBackupWorker>(context)
            .setInputData(androidx.work.workDataOf(CloudBackupWorker.KEY_MANUAL to manual))
            .setRunAttemptCount(attempt)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                    CloudBackupWorker(appContext, workerParameters, engine, store, alerts)
            })
            .build()

    /** The worker ended its run (no retry) and reported [expected] as its outcome. */
    private fun assertOutcome(expected: String, result: ListenableWorker.Result) {
        assertTrue("finished, not retried: $result", result is ListenableWorker.Result.Success)
        assertEquals(expected, result.outputData.getString(CloudBackupWorker.KEY_OUTCOME))
    }

    @Test
    fun theDataGoesUpSealedAndComesBackAsAValidBackupWithoutPhotos() = runBlocking {
        product("Lait Candia 1L")
        val started = System.nanoTime()
        val outcome = engine.run() as CloudBackupOutcome.Saved
        Log.i(TAG, "engine run on the phone: ${(System.nanoTime() - started) / 1_000_000} ms, sealed ${outcome.entry.size} bytes")

        val sealed = cloud.backups.getValue(outcome.entry.id).second
        val key = keys.loadKey("biz-device-test")!!
        val plain = EncryptedBackupFile.reader(ByteArrayInputStream(sealed), key).use { it.readBytes() }
        val verified = BackupArchive.verify(ByteArrayInputStream(plain)) as Verification.Verified
        assertFalse("photos go separately (Phase 4)", verified.manifest.photosIncluded)
        assertEquals(verified.manifest.schemaVersion, outcome.entry.meta.schemaVersion)
        assertEquals("7e57de71-0000-4000-8000-000000000020", outcome.entry.meta.deviceId)
        assertTrue("the ring went up", cloud.ring != null)
        assertTrue(File(root, "staging").listFiles().orEmpty().isEmpty())
        assertEquals(null, store.read().pending)
    }

    @Test
    fun theNextRunWithoutChangesIsSkippedAndAChangeIsSent() = runBlocking {
        product("Lait Candia 1L")
        engine.run()
        assertEquals(CloudBackupOutcome.Unchanged, engine.run())
        product("Café Bonal 250g")
        assertTrue(engine.run() is CloudBackupOutcome.Saved)
    }

    @Test
    fun theDailyWorkerBacksUpAndRecordsTheScheduledRun() {
        product("Lait Candia 1L")
        assertOutcome("saved", runBlocking { worker(manual = false).doWork() })
        assertEquals(1, cloud.backups.size)
        val state = store.read()
        assertTrue(state.lastScheduledRunAt != null)
        assertEquals(0, state.problemStreak)
        assertFalse(CloudBackupAlerts.shouldAlert(alerted.last()))
    }

    @Test
    fun theDailyWorkerDoesNothingWhenOffButBackupNowStillRuns() {
        store.update { it.copy(enabled = false) }
        assertEquals(ListenableWorker.Result.success(), runBlocking { worker(manual = false).doWork() })
        assertTrue(cloud.backups.isEmpty())
        assertOutcome("saved", runBlocking { worker(manual = true).doWork() })
        assertEquals(1, cloud.backups.size)
    }

    @Test
    fun aNetworkFailureIsRetriedThenRecordedOnceOnTheLastAttempt() {
        product("Lait Candia 1L")
        failUploads = CloudBackupException(CloudBackupException.Problem.NETWORK, "offline")
        assertEquals(ListenableWorker.Result.retry(), runBlocking { worker(manual = false, attempt = 0).doWork() })
        assertEquals("a retry is not a failure yet", 0, store.read().problemStreak)
        assertTrue(alerted.isEmpty())
        // The sealed copy waits for the retry instead of being built again.
        assertTrue(store.read().pending != null)

        assertOutcome("failed:NO_CONNECTION", runBlocking { worker(manual = false, attempt = CloudBackupWorker.MAX_ATTEMPTS - 1).doWork() })
        assertEquals(1, store.read().problemStreak)
        assertEquals(CloudBackupProblem.NO_CONNECTION, store.read().lastProblem)

        failUploads = null
        assertOutcome("saved", runBlocking { worker(manual = false).doWork() })
        assertEquals(1, cloud.backups.size)
        assertEquals(0, store.read().problemStreak)
    }

    @Test
    fun aFullDriveIsNotRetriedAndAlertsAtOnce() {
        product("Lait Candia 1L")
        failUploads = CloudBackupException(CloudBackupException.Problem.QUOTA_EXCEEDED, "full")
        assertOutcome("failed:QUOTA_EXCEEDED", runBlocking { worker(manual = false).doWork() })
        assertTrue(CloudBackupAlerts.shouldAlert(alerted.last()))
        assertEquals(CloudBackupProblem.QUOTA_EXCEEDED, alerted.last().lastProblem)
    }

    @Test
    fun theScheduleFollowsTheLinkAndTheNetworkSetting() {
        val configuration = Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG).setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        val workManager = WorkManager.getInstance(context)
        val scheduler = CloudBackupScheduler({ workManager }, store, clock = { Instant.parse("2026-11-03T13:00:00Z") })

        scheduler.apply()
        val daily = workManager.getWorkInfosForUniqueWork(CloudBackupScheduler.WORK_NAME).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, daily.state)
        assertEquals(NetworkType.UNMETERED, daily.constraints.requiredNetworkType)
        assertTrue(daily.constraints.requiresBatteryNotLow())
        assertTrue(daily.constraints.requiresStorageNotLow())
        assertEquals(24 * 3_600_000L, daily.periodicityInfo!!.repeatIntervalMillis)

        store.update { it.copy(allowMobileData = true) }
        scheduler.apply()
        val updated = workManager.getWorkInfosForUniqueWork(CloudBackupScheduler.WORK_NAME).get().single()
        assertEquals("UPDATE keeps the same job", daily.id, updated.id)
        assertEquals(NetworkType.CONNECTED, updated.constraints.requiredNetworkType)

        scheduler.backupNow()
        val now = workManager.getWorkInfosForUniqueWork(CloudBackupScheduler.NOW_NAME).get().single()
        assertEquals(NetworkType.CONNECTED, now.constraints.requiredNetworkType)

        store.update { it.copy(linked = false) }
        scheduler.apply()
        assertTrue(workManager.getWorkInfosForUniqueWork(CloudBackupScheduler.WORK_NAME).get().all { it.state == WorkInfo.State.CANCELLED })
        assertTrue(workManager.getWorkInfosForUniqueWork(CloudBackupScheduler.NOW_NAME).get().all { it.state.isFinished })

        // At app start, an unlinked phone schedules nothing.
        scheduler.ensureScheduled()
        assertTrue(workManager.getWorkInfosForUniqueWork(CloudBackupScheduler.WORK_NAME).get().all { it.state == WorkInfo.State.CANCELLED })
    }

    private companion object {
        const val TAG = "CloudBackupDeviceTest"
        const val TEST_DB = "cloud-backup-device-test"
        const val TEST_DIR = "cloud-backup-device-test"
        const val ALIAS = "distrigo_cloud_backup_devicetest_engine"
    }
}
