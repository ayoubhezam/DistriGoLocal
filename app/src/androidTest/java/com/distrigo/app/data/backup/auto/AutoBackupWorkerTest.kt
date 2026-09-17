package com.distrigo.app.data.backup.auto

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.distrigo.app.data.backup.BackupCreator
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.local.database.withDeviceIdentity
import com.distrigo.app.data.local.database.withMigrationPolicy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * The daily job, run the way WorkManager runs it, on the test's own database [TEST_DB] and folders under
 * `cacheDir/[TEST_DIR]`; alerts are recorded instead of shown. The schedule is checked on WorkManager's in-memory
 * test instance, so the app's real schedule is never touched.
 */
@RunWith(AndroidJUnit4::class)
class AutoBackupWorkerTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, TEST_DIR)
    private val pickedDir = File(root, "picked")
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var store: AutoBackupStore
    private lateinit var runner: AutoBackupRunner
    private var pickedAvailable = true
    private var now = Instant.parse("2026-09-18T02:00:00Z")
    private val alerted = mutableListOf<AutoBackupState>()

    private inner class Picked : BackupFolder {
        private val delegate = PrivateFolder(pickedDir, displayName = "DistriGo")
        override val displayName get() = delegate.displayName
        override fun isAvailable() = pickedAvailable && delegate.isAvailable()
        override fun create(name: String) = delegate.create(name)
        override fun list() = delegate.list()
        override fun delete(file: FolderFile) = delegate.delete(file)
    }

    private val alerts = object : AutoBackupAlerts {
        override fun update(state: AutoBackupState) {
            alerted += state
        }
    }

    @Before
    fun setUp() {
        cleanUp()
        pickedDir.mkdirs()
        db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .withMigrationPolicy()
            .withChangeTracking()
            .withDeviceIdentity { "7e57de71-0000-4000-8000-000000000010" }
            .build()
        sql = db.openHelper.writableDatabase
        store = AutoBackupStore(File(root, "state"))
        store.update { it.copy(enabled = true, folderUri = "content://test/tree/DistriGo") }
        val creator = BackupCreator(db, context.getDatabasePath(TEST_DB), File(root, "images"), File(root, "work"), context.contentResolver, "9.9 (99)", "Test Phone")
        runner = AutoBackupRunner(db, creator, store, { Picked() }, PrivateFolder(File(root, "private")), clock = { now })
    }

    @After
    fun cleanUp() {
        if (::db.isInitialized && db.isOpen) db.close()
        context.deleteDatabase(TEST_DB)
        File(context.cacheDir, "$TEST_DB.lck").delete()
        root.deleteRecursively()
    }

    private fun product(name: String) = sql.execSQL(
        "INSERT INTO products (name, selling_price, purchase_price, stock, min_stock, unit_type, packages, pack_size, " +
            "has_expiry, camion_stock, uuid) VALUES (?, 120.0, 100.0, 0, 0, 'piece', 0, 1, 0, 0, ?)",
        arrayOf(name, UUID.randomUUID().toString())
    )

    /** One night's run, as WorkManager starts it. */
    private fun night(): ListenableWorker.Result {
        now = now.plusSeconds(86_400)
        val worker = TestListenableWorkerBuilder<AutoBackupWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                    AutoBackupWorker(appContext, workerParameters, runner, alerts)
            })
            .build()
        return runBlocking { worker.doWork() }
    }

    @Test
    fun aNightWithChangesSavesABackupAndClearsAnyAlert() {
        product("Lait Candia 1L")

        assertEquals(ListenableWorker.Result.success(), night())

        assertEquals(1, pickedDir.list()!!.count(AutoBackupNames::isAutomatic))
        val state = store.read()
        assertEquals(0, state.problemStreak)
        assertNull(state.lastProblem)
        assertEquals(now, state.lastAttemptAt)
        assertTrue("the scheduled run is recorded apart from manual ones", state.lastScheduledRunAt != null)
        assertFalse(AutoBackupAlerts.shouldAlert(alerted.last()))
    }

    @Test
    fun whenTurnedOffTheJobDoesNothing() {
        store.update { it.copy(enabled = false) }
        product("Lait Candia 1L")

        assertEquals(ListenableWorker.Result.success(), night())

        assertTrue(pickedDir.list()!!.isEmpty())
        assertNull(store.read().lastAttemptAt)
        assertTrue(alerted.isEmpty())
    }

    /** The folder is lost: the first night is quiet, the second alerts, and the night it is back clears it. */
    @Test
    fun aLostFolderAlertsOnTheSecondNightAndClearsWhenItIsBack() {
        product("Lait Candia 1L")
        pickedAvailable = false

        night()
        assertEquals(1, store.read().problemStreak)
        assertFalse(AutoBackupAlerts.shouldAlert(alerted.last()))

        night() // nothing changed, but the only backup is still in the app
        val second = alerted.last()
        assertEquals(2, second.problemStreak)
        assertEquals(AutoBackupProblem.FOLDER_UNAVAILABLE, second.lastProblem)
        assertTrue(AutoBackupAlerts.shouldAlert(second))

        pickedAvailable = true
        night()
        assertFalse(AutoBackupAlerts.shouldAlert(alerted.last()))
        assertEquals(0, store.read().problemStreak)
        assertEquals(1, pickedDir.list()!!.count(AutoBackupNames::isAutomatic))
    }

    /** A job that crashes still counts, and still reports success so WorkManager keeps its daily schedule. */
    @Test
    fun aCrashCountsAsAProblemAndKeepsTheSchedule() {
        db.close() // the next fingerprint read fails
        runner = AutoBackupRunner(
            db, BackupCreator(db, context.getDatabasePath(TEST_DB), File(root, "images"), File(root, "work"), context.contentResolver, "", ""),
            store, { throw IllegalStateException("broken for the test") }, PrivateFolder(File(root, "private")), clock = { now },
        )

        assertEquals(ListenableWorker.Result.success(), night())
        assertEquals(AutoBackupProblem.UNEXPECTED, store.read().lastProblem)
        assertEquals(1, store.read().problemStreak)
    }

    @Test
    fun turningOnSchedulesOneDailyJobAndTurningOffCancelsIt() {
        val configuration = Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG).setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        val workManager = WorkManager.getInstance(context)
        val scheduler = AutoBackupScheduler({ workManager }, store, clock = { Instant.parse("2026-09-17T13:00:00Z") })

        scheduler.setEnabled(true)
        assertEquals(Instant.parse("2026-09-17T13:00:00Z"), store.read().enabledAt)
        assertTrue("WorkManager knows when it runs next", scheduler.nextRunAt() != null)
        val scheduled = workManager.getWorkInfosForUniqueWork(AutoBackupScheduler.WORK_NAME).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, scheduled.state)
        assertTrue(store.read().enabled)
        assertTrue(scheduled.constraints.requiresBatteryNotLow())
        assertTrue(scheduled.constraints.requiresStorageNotLow())
        assertEquals(24 * 3_600_000L, scheduled.periodicityInfo!!.repeatIntervalMillis)

        scheduler.ensureScheduled()
        assertEquals("starting the app keeps the same schedule", scheduled.id,
            workManager.getWorkInfosForUniqueWork(AutoBackupScheduler.WORK_NAME).get().single().id)

        scheduler.setEnabled(false)
        assertFalse(store.read().enabled)
        assertEquals(null, scheduler.nextRunAt())
        assertTrue(workManager.getWorkInfosForUniqueWork(AutoBackupScheduler.WORK_NAME).get().all { it.state == WorkInfo.State.CANCELLED })
    }

    private companion object {
        const val TEST_DB = "auto-backup-worker-test"
        const val TEST_DIR = "auto-backup-worker-test"
    }
}
