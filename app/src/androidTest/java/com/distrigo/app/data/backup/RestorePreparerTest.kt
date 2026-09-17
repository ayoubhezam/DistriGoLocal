package com.distrigo.app.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.DATABASE_VERSION
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.local.database.withDeviceIdentity
import com.distrigo.app.data.local.database.withMigrationPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * A backup is unpacked and proven restorable in a staging folder, or refused with nothing left behind.
 *
 * The backups are of the test's own database [TEST_DB], or of [OLD_DB] built at version 48 from the
 * exported schema; every folder is under `cacheDir/[TEST_DIR]`, and all of it is deleted after each test.
 */
@RunWith(AndroidJUnit4::class)
class RestorePreparerTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, TEST_DIR)
    private val images = File(root, "images")
    private val saved = File(root, "saved")
    private val restoreDir = File(root, "restore")
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var creator: BackupCreator
    private var freeSpace = Long.MAX_VALUE
    private lateinit var preparer: RestorePreparer

    @Before
    fun open() {
        assertNotEquals("distrigo", TEST_DB)
        cleanUp()
        images.mkdirs()
        saved.mkdirs()
        db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .withMigrationPolicy()
            .withChangeTracking()
            .withDeviceIdentity { BACKED_UP_DEVICE }
            .build()
        sql = db.openHelper.writableDatabase
        creator = BackupCreator(db, context.getDatabasePath(TEST_DB), images, File(root, "work"), context.contentResolver, "9.9 (99)", "Test Phone")
        preparer = RestorePreparer(context, context.contentResolver, restoreDir, { RESTORING_DEVICE }, { freeSpace })
    }

    @After
    fun cleanUp() {
        if (::db.isInitialized && db.isOpen) db.close()
        for (name in listOf(TEST_DB, OLD_DB)) {
            context.deleteDatabase(name)
            File(context.cacheDir, "$name.lck").delete()
            File(context.getDatabasePath(name).path + ".lck").delete()
        }
        root.deleteRecursively()
    }

    private fun product(name: String, image: String? = null) = sql.execSQL(
        "INSERT INTO products (name, selling_price, purchase_price, stock, min_stock, unit_type, packages, pack_size, " +
            "has_expiry, camion_stock, image_uri, uuid) VALUES (?, 120.0, 100.0, 0, 0, 'piece', 0, 1, 0, 0, ?, ?)",
        arrayOf(name, image, UUID.randomUUID().toString())
    )

    private fun photo(seed: Int): String {
        val bytes = ByteArray(5_000) { (it * seed % 251).toByte() }
        val hash = BackupFormat.sha256(bytes.inputStream())
        File(images, "$hash.jpg").writeBytes(bytes)
        return hash
    }

    private fun backup(name: String = "DistriGo-test.distrigo"): File =
        File(saved, name).also { creator.create(Uri.fromFile(it)) }

    private fun prepare(file: File, expected: BackupManifest? = null) = preparer.prepare(Uri.fromFile(file), expected)

    private fun ready(file: File) = when (val result = prepare(file)) {
        is Preparation.Ready -> result.restore
        is Preparation.Failed -> throw AssertionError("not prepared: ${result.problem}")
    }

    private fun problem(file: File, expected: BackupManifest? = null) = (prepare(file, expected) as Preparation.Failed).problem

    private fun assertDamaged(file: File, detail: String, expected: BackupManifest? = null) {
        val problem = problem(file, expected)
        assertTrue("$detail: $problem", problem is BackupProblem.Damaged && detail in problem.detail)
    }

    private fun assertNothingStaged() {
        assertFalse("the staging folder is gone", preparer.stagingDir.exists())
    }

    /**
     * Rewrites a backup: [changeDatabase] edits its database file, then the manifest is described afresh
     * for it, and [changeManifest] edits the result. Checksums stay valid, so only the deeper checks can
     * tell.
     */
    private fun repacked(
        file: File,
        changeDatabase: (File) -> Unit = {},
        changeManifest: (BackupManifest) -> BackupManifest = { it },
    ): File {
        val parts = File(root, "parts").apply { deleteRecursively(); mkdirs() }
        ZipInputStream(file.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                File(parts, entry.name).apply { parentFile!!.mkdirs() }.outputStream().use { zip.copyTo(it) }
            }
        }
        val original = (BackupManifest.parse(File(parts, BackupFormat.MANIFEST_ENTRY).readText()) as ManifestResult.Valid).manifest
        val database = File(parts, BackupFormat.DATABASE_ENTRY)
        changeDatabase(database)
        val described = original.copy(entries = original.entries.map { if (it.name == BackupFormat.DATABASE_ENTRY) BackupArchive.describe(it.name, database) else it })
        val manifest = changeManifest(described)
        return File(saved, "repacked-${UUID.randomUUID()}.distrigo").also { out ->
            out.outputStream().use { BackupArchive.write(manifest, manifest.entries.associate { it.name to File(parts, it.name) }, it) }
        }
    }

    private fun onDatabase(file: File, block: (SQLiteDatabase) -> Unit) {
        val copy = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        try { block(copy) } finally { copy.close() }
    }

    private fun <T> query(file: File, query: String, read: (android.database.Cursor) -> T): T {
        val copy = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try { return copy.rawQuery(query, null).use { it.moveToFirst(); read(it) } } finally { copy.close() }
    }

    @Test
    fun aBackupIsStagedAsOneCheckedDatabaseAndItsPhotos() {
        val photos = listOf(photo(3), photo(5))
        product("Lait Candia 1L", "img:${photos[0]}")
        product("Yaourt Soummam")
        sql.execSQL("INSERT OR REPLACE INTO business_settings (id, logo_ref) VALUES (1, 'img:${photos[1]}')")
        val file = backup()
        val databaseId = db.appMetaDao().get("database_id")

        val restore = ready(file)

        assertEquals(preparer.stagingDir, restore.dir)
        assertEquals(setOf(BackupFormat.DATABASE_ENTRY, "images"), restore.dir.list()!!.toSet())
        assertEquals(photos.map { "$it.jpg" }.sorted(), restore.images.list()!!.sorted())
        for (hash in photos) assertEquals(hash, File(restore.images, "$hash.jpg").inputStream().use(BackupFormat::sha256))

        assertEquals(DATABASE_VERSION, query(restore.database, "PRAGMA user_version") { it.getInt(0) })
        assertEquals(2, query(restore.database, "SELECT COUNT(*) FROM products") { it.getInt(0) })
        assertEquals(databaseId, query(restore.database, "SELECT value FROM app_meta WHERE key = 'database_id'") { it.getString(0) })
        assertEquals("this phone's identity is set now", RESTORING_DEVICE, query(restore.database, "SELECT value FROM app_meta WHERE key = 'device_id'") { it.getString(0) })

        // Only this database's lock and the folders made for it: other code, such as WorkManager, keeps its own there.
        val lock = File(context.cacheDir, restore.database.absolutePath + ".lck")
        assertFalse("Room's lock for the staged database is removed", lock.exists())
        assertFalse("with the folders made for it", lock.parentFile!!.exists())
        assertEquals("the data on the phone is untouched", BACKED_UP_DEVICE, db.appMetaDao().get("device_id"))
        assertEquals(2L, sql.query("SELECT COUNT(*) FROM products").use { it.moveToFirst(); it.getLong(0) })
    }

    /** A backup made by an earlier app is migrated while staged, keeping its data and its database id. */
    @Test
    fun aBackupFromAnEarlierVersionIsMigratedWhileStaged() {
        helper.createDatabase(OLD_DB, 48).apply {
            execSQL("INSERT INTO app_meta (key, value) VALUES ('database_id', 'old-database'), ('device_id', 'old-phone')")
            execSQL(
                "INSERT INTO clients (name, balance, customer_type, uuid, updated_at, version) " +
                    "VALUES ('Épicerie El Amel', 0, 'retail', 'u-client', 0, 1)"
            )
            close()
        }
        val parts = File(root, "old").apply { mkdirs() }
        val database = File(parts, BackupFormat.DATABASE_ENTRY)
        context.getDatabasePath(OLD_DB).copyTo(database)
        File(context.getDatabasePath(OLD_DB).path + "-wal").takeIf { it.isFile }?.copyTo(File(database.path + "-wal"))
        val snapshot = DatabaseSnapshot.inspect(database, images, Instant.now())
        assertEquals(48, snapshot.schemaVersion)
        val manifest = BackupManifest(1, 48, "0.9 (1)", snapshot.createdAt, snapshot.databaseId, snapshot.deviceId, "Old Phone",
            snapshot.rowCounts, listOf(BackupArchive.describe(BackupFormat.DATABASE_ENTRY, database)))
        val file = File(saved, "old.distrigo").also { out ->
            out.outputStream().use { BackupArchive.write(manifest, mapOf(BackupFormat.DATABASE_ENTRY to database), it) }
        }

        val restore = ready(file)

        assertEquals(DATABASE_VERSION, query(restore.database, "PRAGMA user_version") { it.getInt(0) })
        assertEquals("Épicerie El Amel", query(restore.database, "SELECT name FROM clients") { it.getString(0) })
        assertEquals("old-database", query(restore.database, "SELECT value FROM app_meta WHERE key = 'database_id'") { it.getString(0) })
        assertEquals(RESTORING_DEVICE, query(restore.database, "SELECT value FROM app_meta WHERE key = 'device_id'") { it.getString(0) })
    }

    @Test
    fun aDatabaseAtAnotherVersionThanItsManifestSaysIsDamaged() {
        product("Lait Candia 1L")
        assertDamaged(repacked(backup(), changeManifest = { it.copy(schemaVersion = DATABASE_VERSION - 1) }), "user_version")
        assertNothingStaged()
    }

    @Test
    fun rowCountsOtherThanTheManifestsAreDamaged() {
        product("Lait Candia 1L")
        assertDamaged(repacked(backup(), changeManifest = { it.copy(rowCounts = it.rowCounts + ("products" to 5L)) }), "row counts")
        assertNothingStaged()
    }

    /** A database damaged before it was packed has checksums that match; its integrity check does not. */
    @Test
    fun aDamagedDatabaseWithMatchingChecksumsIsDamaged() {
        repeat(300) { product("Produit numéro $it avec un nom assez long pour remplir plusieurs pages") }
        val file = repacked(backup(), changeDatabase = { database ->
            RandomAccessFile(database, "rw").use { raf ->
                for (page in 2 until (raf.length() / 4096).toInt()) {
                    raf.seek(page * 4096L + 8)
                    raf.write(ByteArray(64) { 0x5A })
                }
            }
        })
        assertTrue(problem(file) is BackupProblem.Damaged)
        assertNothingStaged()
    }

    /** At this app's own version Room trusts the file's identity hash; the fingerprint does not. */
    @Test
    fun aDatabaseEditedToCarryItsOwnTriggerIsDamaged() {
        product("Lait Candia 1L")
        val file = repacked(backup(), changeDatabase = { database ->
            onDatabase(database) { it.execSQL("CREATE TRIGGER evil AFTER INSERT ON products BEGIN DELETE FROM clients; END") }
        })
        assertDamaged(file, "extra trigger evil")
        assertNothingStaged()
    }

    @Test
    fun aDatabaseMissingAnIndexIsDamaged() {
        product("Lait Candia 1L")
        val file = repacked(backup(), changeDatabase = { database ->
            onDatabase(database) { it.execSQL("DROP INDEX index_products_uuid") }
        })
        assertDamaged(file, "indices of products differ")
        assertNothingStaged()
    }

    @Test
    fun aFileChangedSincePreviewIsRefusedBeforeUnpacking() {
        product("Lait Candia 1L")
        val previewed = backup("previewed.distrigo")
        val manifest = (BackupInspector(db, context.contentResolver).inspect(Uri.fromFile(previewed)) as BackupInspection.Restorable).preview.manifest
        product("Yaourt Soummam")
        backup("previewed.distrigo")

        assertDamaged(previewed, "changed since it was previewed", manifest)
        assertNothingStaged()
    }

    @Test
    fun aPhoneWithoutRoomIsToldHowMuchIsNeeded() {
        product("Lait Candia 1L")
        val file = backup()
        freeSpace = 1_000

        val problem = problem(file)

        assertTrue("$problem", problem is BackupProblem.NotEnoughSpace && problem.neededBytes > file.length())
        assertNothingStaged()
    }

    @Test
    fun aBackupFromANewerAppIsRefusedWithoutUnpacking() {
        product("Lait Candia 1L")
        val file = repacked(backup(), changeManifest = { it.copy(schemaVersion = DATABASE_VERSION + 1) })
        assertEquals(BackupProblem.NewerDatabase(DATABASE_VERSION + 1, DATABASE_VERSION), problem(file))
        assertNothingStaged()
    }

    @Test
    fun aTamperedFileIsDamagedAndNothingIsLeft() {
        repeat(50) { product("Produit $it") }
        val file = backup()
        RandomAccessFile(file, "rw").use {
            it.seek(it.length() / 2)
            val byte = it.read()
            it.seek(it.length() / 2)
            it.write(byte xor 0xFF)
        }
        val problem = problem(file)
        assertTrue("a read error is the file's, not the phone's: $problem", problem is BackupProblem.Damaged)
        assertNothingStaged()
    }

    /** Preparing again replaces whatever an earlier, interrupted preparation left. */
    @Test
    fun preparingAgainStartsFromAnEmptyFolder() {
        product("Lait Candia 1L")
        val file = backup()
        File(preparer.stagingDir, "images").mkdirs()
        File(preparer.stagingDir, "images/leftover.jpg").writeBytes(ByteArray(10))
        File(preparer.stagingDir, "distrigo.db-wal").writeBytes(ByteArray(10))

        val restore = ready(file)

        assertEquals(setOf(BackupFormat.DATABASE_ENTRY, "images"), restore.dir.list()!!.toSet())
        assertEquals(0, restore.images.list()!!.size)
    }

    private companion object {
        const val TEST_DB = "restore-preparer-test"
        const val OLD_DB = "restore-preparer-test-v48"
        const val TEST_DIR = "restore-preparer-test"
        const val BACKED_UP_DEVICE = "7e57de71-0000-4000-8000-000000000005"
        const val RESTORING_DEVICE = "7e57de71-0000-4000-8000-000000000006"
    }
}
