package com.distrigo.app.data.backup.auto

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.backup.BackupArchive
import com.distrigo.app.data.backup.BackupCreator
import com.distrigo.app.data.backup.Verification
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
import java.time.Instant
import java.util.UUID

/**
 * Automatic backups on the test's own database [TEST_DB], into folders under `cacheDir/[TEST_DIR]`: one stands
 * in for the folder the user picked, and can be made unavailable as a revoked permission or a deleted folder
 * would; the other is the app's private fallback.
 */
@RunWith(AndroidJUnit4::class)
class AutoBackupRunnerTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, TEST_DIR)
    private val pickedDir = File(root, "picked")
    private val privateDir = File(root, "private")
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private var pickedAvailable = true
    private var now = Instant.parse("2026-09-18T02:00:00Z")

    /** The picked folder, as the runner sees it: a real directory whose availability the test controls. */
    private inner class Picked : BackupFolder {
        private val delegate = PrivateFolder(pickedDir, displayName = "DistriGo")
        override val displayName get() = delegate.displayName
        override fun isAvailable() = pickedAvailable && delegate.isAvailable()
        override fun create(name: String) = delegate.create(name)
        override fun list() = delegate.list()
        override fun delete(file: FolderFile) = delegate.delete(file)
    }

    private lateinit var runner: AutoBackupRunner

    @Before
    fun setUp() {
        cleanUp()
        pickedDir.mkdirs()
        db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .withMigrationPolicy()
            .withChangeTracking()
            .withDeviceIdentity { "7e57de71-0000-4000-8000-000000000009" }
            .build()
        sql = db.openHelper.writableDatabase
        val creator = BackupCreator(db, context.getDatabasePath(TEST_DB), File(root, "images"), File(root, "work"), context.contentResolver, "9.9 (99)", "Test Phone")
        val store = AutoBackupStore(File(root, "state"))
        store.update { it.copy(folderUri = "content://test/tree/DistriGo") }
        runner = AutoBackupRunner(db, creator, store, { Picked() }, PrivateFolder(privateDir), clock = { now })
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

    /** Runs the next backup a day later. */
    private fun nextDay(force: Boolean = false): AutoBackupOutcome {
        now = now.plusSeconds(86_400)
        return runner.run(force)
    }

    private fun names(dir: File) = dir.list().orEmpty().sorted()

    private fun meta(key: String) = db.appMetaDao().get(key)

    @Test
    fun theFirstRunSavesAVerifiedBackupToThePickedFolder() {
        product("Lait Candia 1L")

        val outcome = nextDay() as AutoBackupOutcome.Saved

        assertEquals("DistriGo-auto-2026-09-19-030000.distrigo", outcome.fileName)
        assertFalse(outcome.inPrivateStorage)
        assertEquals("DistriGo", outcome.folderName)
        val file = File(pickedDir, outcome.fileName)
        assertEquals(listOf(outcome.fileName), names(pickedDir))
        assertEquals(file.length(), outcome.size)
        val verified = file.inputStream().use { BackupArchive.verify(it) }
        assertTrue("$verified", verified is Verification.Verified)
        assertEquals(1L, (verified as Verification.Verified).manifest.rowCounts["products"])
        assertEquals("recorded as the data's last backup", outcome.fileName, meta(BackupCreator.KEY_LAST_NAME))
        assertEquals(outcome.fileName, runner.store.read().lastFileName)
    }

    @Test
    fun aDayWithoutChangesSavesNothing() {
        product("Lait Candia 1L")
        nextDay()

        assertEquals(AutoBackupOutcome.Unchanged, nextDay())
        assertEquals(AutoBackupOutcome.Unchanged, nextDay())
        assertEquals(1, names(pickedDir).size)
    }

    @Test
    fun aChangeOrAForcedRunSavesAgain() {
        product("Lait Candia 1L")
        nextDay()
        product("Yaourt Soummam")
        assertTrue(nextDay() is AutoBackupOutcome.Saved)
        assertTrue("forced, although nothing changed", nextDay(force = true) is AutoBackupOutcome.Saved)
        assertEquals(3, names(pickedDir).size)
    }

    /** If the last backup was deleted from the folder, the data is no longer backed up there. */
    @Test
    fun aDeletedLastBackupIsSavedAgain() {
        product("Lait Candia 1L")
        val first = nextDay() as AutoBackupOutcome.Saved
        File(pickedDir, first.fileName).delete()

        assertTrue(nextDay() is AutoBackupOutcome.Saved)
        assertEquals(1, names(pickedDir).size)
    }

    @Test
    fun onlyTheNewestSevenAutomaticBackupsAreKeptAndNothingElseIsTouched() {
        val others = listOf("DistriGo-2026-09-17-1311.distrigo", "notes.txt", "DistriGo-auto-2026-09-01-030000 (1).distrigo")
        for (name in others) File(pickedDir, name).writeText("not ours to delete")

        val saved = (1..10).map { day ->
            product("Produit $day")
            nextDay() as AutoBackupOutcome.Saved
        }

        val autos = names(pickedDir).filter(AutoBackupNames::isAutomatic)
        assertEquals(saved.takeLast(AutoBackupRunner.KEEP).map { it.fileName }, autos)
        assertTrue(names(pickedDir).containsAll(others))
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, 1, 1, 1), saved.map { it.removed })
    }

    /** A revoked permission or a deleted folder: the backup still happens, in the app's storage, and says so. */
    @Test
    fun whenThePickedFolderCannotBeUsedTheBackupGoesToPrivateStorage() {
        product("Lait Candia 1L")
        pickedAvailable = false

        val outcome = nextDay() as AutoBackupOutcome.Saved

        assertTrue(outcome.inPrivateStorage)
        assertEquals(listOf(outcome.fileName), names(privateDir))
        assertEquals(emptyList<String>(), names(pickedDir))
        assertNull("a copy only in the app is not recorded as the data's last backup", meta(BackupCreator.KEY_LAST_NAME))

        assertEquals("nothing changed, and it is still there", AutoBackupOutcome.Unchanged, nextDay())

        pickedAvailable = true
        val back = nextDay() as AutoBackupOutcome.Saved
        assertFalse("the folder is back: the same data is saved where the user keeps backups", back.inPrivateStorage)
        assertEquals(listOf(back.fileName), names(pickedDir))
    }

    @Test
    fun withNoFolderChosenBackupsGoToPrivateStorage() {
        runner.store.update { it.copy(folderUri = null) }
        product("Lait Candia 1L")
        val outcome = nextDay() as AutoBackupOutcome.Saved
        assertTrue(outcome.inPrivateStorage)
    }

    /** A folder that refuses the file fails the run and leaves the state as it was, so the next run tries again. */
    @Test
    fun aFailedRunChangesNothingAndTheNextRunTriesAgain() {
        product("Lait Candia 1L")
        val refusing = AutoBackupRunner(
            db, BackupCreator(db, context.getDatabasePath(TEST_DB), File(root, "images"), File(root, "work"), context.contentResolver, "", ""),
            runner.store,
            {
                object : BackupFolder by Picked() {
                    override fun create(name: String): Uri = throw java.io.IOException("storage full")
                }
            },
            PrivateFolder(privateDir), clock = { now },
        )

        val failed = refusing.run()

        assertTrue("$failed", failed is AutoBackupOutcome.Failed)
        assertNull(runner.store.read().lastFingerprint)
        assertTrue(nextDay() is AutoBackupOutcome.Saved)
    }

    @Test
    fun theStateSurvivesBeingReadAgain() {
        product("Lait Candia 1L")
        val saved = nextDay() as AutoBackupOutcome.Saved
        val reread = AutoBackupStore(File(root, "state")).read()
        assertEquals(saved.fileName, reread.lastFileName)
        assertEquals(BackupLocation.PICKED_FOLDER, reread.lastLocation)
        assertEquals("content://test/tree/DistriGo", reread.folderUri)
        assertNotEquals(null, reread.lastFingerprint)
    }

    private companion object {
        const val TEST_DB = "auto-backup-runner-test"
        const val TEST_DIR = "auto-backup-runner-test"
    }
}
