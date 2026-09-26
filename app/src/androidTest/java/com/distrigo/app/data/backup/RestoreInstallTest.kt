package com.distrigo.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.local.database.withDeviceIdentity
import com.distrigo.app.data.local.database.withMigrationPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * From a picked backup to the restored data in place: the safety backup, the install at startup, and what
 * happens when a start is killed part-way or the restored database fails its check.
 *
 * "The phone's data" here is the test's own database [TEST_DB] and photo folder under `cacheDir/[TEST_DIR]`;
 * a "start" is closing that database and calling [RestoreInstaller.installPending], as the app does before
 * opening it. Nothing touches the app's own database, photos or `no_backup/restore`.
 */
@RunWith(AndroidJUnit4::class)
class RestoreInstallTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, TEST_DIR)
    private val images = File(root, "images")
    private val saved = File(root, "saved")
    private val restoreDir = File(root, "restore")
    private val databaseFile = context.getDatabasePath(TEST_DB)
    private var db: AppDatabase? = null
    private var backfillCleared = 0

    private val installer get() = RestoreInstaller(databaseFile, images, restoreDir) { backfillCleared++ }

    @Before
    fun setUp() {
        assertNotEquals("distrigo", TEST_DB)
        cleanUp()
        images.mkdirs()
        saved.mkdirs()
    }

    @After
    fun cleanUp() {
        db?.takeIf { it.isOpen }?.close()
        db = null
        context.deleteDatabase(TEST_DB)
        File(context.cacheDir, "$TEST_DB.lck").delete()
        root.deleteRecursively()
    }

    private fun open(): AppDatabase = db?.takeIf { it.isOpen } ?: Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
        .withMigrationPolicy()
        .withChangeTracking()
        .withDeviceIdentity { DEVICE }
        .build()
        .also { db = it }

    /** What the app does on a start: nothing may hold the database while a restore is installed. */
    private fun start(installer: RestoreInstaller = this.installer) {
        db?.close()
        db = null
        installer.installPending()
    }

    private fun product(name: String, image: String? = null) = open().openHelper.writableDatabase.execSQL(
        "INSERT INTO products (name, selling_price, purchase_price, stock, min_stock, unit_type, packages, pack_size, " +
            "has_expiry, camion_stock, image_uri, uuid) VALUES (?, 120.0, 100.0, 0, 0, 'piece', 0, 1, 0, 0, ?, ?)",
        arrayOf(name, image, UUID.randomUUID().toString())
    )

    private fun productNames(): List<String> =
        open().openHelper.writableDatabase.query("SELECT name FROM products ORDER BY name").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    private fun meta(key: String) = open().appMetaDao().get(key)

    private fun photo(seed: Int): String {
        val bytes = ByteArray(5_000) { (it * seed % 251).toByte() }
        val hash = BackupFormat.sha256(bytes.inputStream())
        File(images, "$hash.jpg").writeBytes(bytes)
        return hash
    }

    private fun creator() = BackupCreator(open(), databaseFile, images, File(root, "work"), context.contentResolver, "9.9 (99)", "Test Phone")

    private fun coordinator() = RestoreCoordinator(
        RestorePreparer(context, context.contentResolver, restoreDir, { DEVICE }),
        creator(),
        installer,
        zone = { ZoneOffset.UTC },
    )

    private val restoredPhoto = 3
    private val currentPhoto = 5

    /**
     * The scene every test starts from: a backup holding "Restauré" and one photo, then the phone moved on
     * to "Actuel" with another photo, and that backup scheduled for restore.
     */
    private fun scheduleRestore(): RestoreOutcome.Scheduled {
        product("Restauré", "img:${photo(restoredPhoto)}")
        val backup = File(saved, "backup.distrigo").also { creator().create(Uri.fromFile(it)) }
        open().openHelper.writableDatabase.execSQL("DELETE FROM products")
        product("Actuel", "img:${photo(currentPhoto)}")
        return coordinator().restore(Uri.fromFile(backup), expected = null, fileName = "backup.distrigo", fileSize = backup.length()) as RestoreOutcome.Scheduled
    }

    private fun photoFiles() = images.list().orEmpty().sorted()

    @Test
    fun aStartInstallsTheRestoreOnItsOwnThreadWhileTheAppWaits() {
        scheduleRestore()
        db?.close()
        db = null
        RestoreStartup.reset()
        try {
            RestoreStartup.begin({ installer })
            assertTrue(RestoreStartup.started)
            RestoreStartup.await()
            assertFalse(RestoreStartup.inProgress)
            assertTrue(RestoreStartup.result!!.installed)
            assertRestored()
            // Nothing waits any more: a start installs nothing.
            RestoreStartup.reset()
            RestoreStartup.begin({ installer })
            RestoreStartup.await()
            assertNull(RestoreStartup.result)
        } finally {
            RestoreStartup.reset()
        }
    }

    @Test
    fun aDataOnlyBackupRestoresTheDataAndKeepsThePhonesPhotos() {
        product("Restauré", "img:${photo(restoredPhoto)}")
        val backup = File(saved, "data-only.distrigo").also { creator().create(Uri.fromFile(it), record = false, includePhotos = false) }
        open().openHelper.writableDatabase.execSQL("DELETE FROM products")
        product("Actuel", "img:${photo(currentPhoto)}")
        coordinator().restore(Uri.fromFile(backup), expected = null) as RestoreOutcome.Scheduled

        start()

        assertEquals(listOf("Restauré"), productNames())
        // Both photos are still there: the restore did not touch the folder.
        assertEquals(listOf(hashOf(restoredPhoto), hashOf(currentPhoto)).map { "$it.jpg" }.sorted(), photoFiles())
        assertTrue(installer.lastResult()!!.installed)
        assertTidy()
    }

    private fun hashOf(seed: Int) = BackupFormat.sha256(ByteArray(5_000) { (it * seed % 251).toByte() }.inputStream())

    private fun assertRestored() {
        assertEquals(listOf("Restauré"), productNames())
        assertEquals(listOf("${hashOf(restoredPhoto)}.jpg"), photoFiles())
        assertTrue(installer.lastResult()!!.installed)
        assertTrue(meta(RestoreInstaller.KEY_RESTORED_AT) != null)
        assertTidy()
    }

    private fun assertKept() {
        assertEquals(listOf("Actuel"), productNames())
        assertEquals(listOf(hashOf(restoredPhoto), hashOf(currentPhoto)).map { "$it.jpg" }.sorted(), photoFiles())
        assertNull(meta(RestoreInstaller.KEY_RESTORED_AT))
        assertTidy()
    }

    private fun assertTidy() {
        assertEquals(setOf("safety", "last-result"), restoreDir.list().orEmpty().toSet())
        assertFalse(installer.hasPending)
    }

    @Test
    fun aScheduledRestoreIsInstalledOnTheNextStart() {
        val scheduled = scheduleRestore()
        assertTrue(installer.hasPending)
        assertEquals("nothing changes before the restart", listOf("Actuel"), productNames())

        start()

        assertRestored()
        assertEquals(1, backfillCleared)
        val result = installer.lastResult()!!
        assertEquals(scheduled.manifest.createdAt, result.backupCreatedAt)
        assertEquals(scheduled.safetyBackup.name, result.safetyBackup)
        assertEquals(scheduled.manifest.createdAt.toString(), meta(RestoreInstaller.KEY_RESTORED_BACKUP_AT))
        assertEquals("the device is still this phone", DEVICE, meta("device_id"))
        assertEquals("the restored backup is the data's last backup", scheduled.manifest.createdAt.toString(), meta(BackupCreator.KEY_LAST_AT))
        assertEquals("backup.distrigo", meta(BackupCreator.KEY_LAST_NAME))
        assertEquals(File(saved, "backup.distrigo").length().toString(), meta(BackupCreator.KEY_LAST_SIZE))
    }

    /** The safety backup is a full backup of the data being replaced, and restoring it undoes the restore. */
    @Test
    fun theSafetyBackupHoldsTheReplacedDataAndUndoesTheRestore() {
        val scheduled = scheduleRestore()
        val safety = scheduled.safetyBackup
        val verified = safety.inputStream().use { BackupArchive.verify(it) }
        assertTrue("$verified", verified is Verification.Verified)
        assertEquals("it holds the data being replaced", 1L, (verified as Verification.Verified).manifest.rowCounts["products"])
        assertEquals(1, verified.manifest.images.size)
        start()
        assertRestored()
        assertEquals("the safety backup is not recorded as the last backup", scheduled.manifest.createdAt.toString(), meta(BackupCreator.KEY_LAST_AT))

        val undo = coordinator().restore(Uri.fromFile(safety), expected = null)
        assertTrue("$undo", undo is RestoreOutcome.Scheduled)
        start()

        assertEquals(listOf("Actuel"), productNames())
        assertTrue(photoFiles().contains("${hashOf(currentPhoto)}.jpg"))
    }

    @Test
    fun onlyTheNewestSafetyBackupsAreKept() {
        product("Restauré")
        val backup = File(saved, "backup.distrigo").also { creator().create(Uri.fromFile(it)) }
        repeat(RestoreCoordinator.KEEP_SAFETY_BACKUPS + 2) {
            assertTrue(coordinator().restore(Uri.fromFile(backup), expected = null) is RestoreOutcome.Scheduled)
            Thread.sleep(1_100) // names are to the second
        }
        assertEquals(RestoreCoordinator.KEEP_SAFETY_BACKUPS, coordinator().safetyBackups().size)
        val newest = coordinator().safetyBackups().first().name
        assertTrue("the waiting restore's own safety backup is kept", newest in File(installer.pendingDir, "READY").readText())
    }

    /** No safety backup, no restore: the phone keeps its data and nothing waits. */
    @Test
    fun aRestoreWithoutASafetyBackupDoesNotGoAhead() {
        product("Restauré")
        val backup = File(saved, "backup.distrigo").also { creator().create(Uri.fromFile(it)) }
        restoreDir.mkdirs()
        File(restoreDir, "safety").writeText("a file where the folder should be")

        val outcome = coordinator().restore(Uri.fromFile(backup), expected = null)

        assertTrue("$outcome", outcome is RestoreOutcome.SafetyBackupFailed)
        assertFalse(installer.hasPending)
        assertFalse(File(restoreDir, "staging").exists())
    }

    @Test
    fun aStartWithNothingWaitingChangesNothing() {
        product("Actuel", "img:${photo(currentPhoto)}")
        start()
        assertEquals(listOf("Actuel"), productNames())
        assertNull(installer.lastResult())
    }

    /** A kill at any point of an install is finished or undone by the next start; the data is never lost. */
    @Test
    fun aStartKilledAtAnyPointIsCompletedByTheNext() {
        val points = listOf("moved-old-database", "moved-old", "moved-new-database", "moved-new", "checked", "recorded", "marker-deleted")
        for (point in points) {
            cleanUp(); setUp()
            scheduleRestore()
            val killed = installer.apply { onPoint = { if (it == point) throw RestoreInstaller.Interrupted() } }
            try {
                start(killed)
                throw AssertionError("not killed at $point")
            } catch (expected: RestoreInstaller.Interrupted) {
            }

            start()

            try {
                assertRestored()
            } catch (e: AssertionError) {
                throw AssertionError("killed at $point: ${e.message}", e)
            }
        }
    }

    /** Killed on every start, the install gives up and the phone keeps its data. */
    @Test
    fun anInstallKilledEveryTimeGivesUpAndKeepsTheData() {
        scheduleRestore()
        repeat(RestoreInstaller.MAX_ATTEMPTS) {
            try {
                start(installer.apply { onPoint = { if (it == "moved-new-database") throw RestoreInstaller.Interrupted() } })
                throw AssertionError("not killed")
            } catch (expected: RestoreInstaller.Interrupted) {
            }
        }

        start()

        assertKept()
        assertFalse(installer.lastResult()!!.installed)
    }

    /** A restored database damaged between scheduling and the start is not installed, and not retried. */
    @Test
    fun aRestoredDatabaseThatFailsItsCheckIsNotInstalled() {
        repeat(300) { product("Produit numéro $it avec un nom assez long pour remplir plusieurs pages") }
        scheduleRestore()
        RandomAccessFile(File(installer.pendingDir, BackupFormat.DATABASE_ENTRY), "rw").use { raf ->
            for (page in 2 until (raf.length() / 4096).toInt()) {
                raf.seek(page * 4096L + 8)
                raf.write(ByteArray(64) { 0x5A })
            }
        }

        start()

        assertKept()
        val result = installer.lastResult()!!
        assertFalse(result.installed)
        assertTrue(result.detail, result.detail!!.isNotEmpty())
    }

    /** A schedule interrupted before its ready mark leaves nothing to install, and is cleared. */
    @Test
    fun aHalfScheduledRestoreIsIgnoredAndCleared() {
        product("Actuel")
        installer.pendingDir.mkdirs()
        File(installer.pendingDir, BackupFormat.DATABASE_ENTRY).writeBytes(ByteArray(100))

        start()

        assertEquals(listOf("Actuel"), productNames())
        assertFalse(installer.pendingDir.exists())
        assertNull(installer.lastResult())
    }

    @Test
    fun aWaitingRestoreCanBeCancelled() {
        scheduleRestore()
        installer.cancelPending()
        start()
        assertEquals(listOf("Actuel"), productNames())
        assertNull(installer.lastResult())
    }

    private companion object {
        const val TEST_DB = "restore-install-test"
        const val TEST_DIR = "restore-install-test"
        const val DEVICE = "7e57de71-0000-4000-8000-000000000007"
    }
}
