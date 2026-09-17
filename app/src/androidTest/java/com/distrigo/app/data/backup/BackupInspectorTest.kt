package com.distrigo.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.Room
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
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * A picked file is previewed only when it is whole and this app can restore it; otherwise the user is
 * told why. On the test's own database and folders, as in BackupCreatorTest; `content://` files go
 * through the app's FileProvider, from a test file under `cache/receipts` deleted after every test.
 */
@RunWith(AndroidJUnit4::class)
class BackupInspectorTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, TEST_DIR)
    private val images = File(root, "images")
    private val saved = File(root, "saved")
    private val shared = File(context.cacheDir, "receipts/$TEST_DIR.distrigo")
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var creator: BackupCreator
    private lateinit var inspector: BackupInspector

    @Before
    fun open() {
        assertNotEquals("distrigo", TEST_DB)
        cleanUp()
        images.mkdirs()
        saved.mkdirs()
        db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .withMigrationPolicy()
            .withChangeTracking()
            .withDeviceIdentity { TEST_DEVICE }
            .build()
        sql = db.openHelper.writableDatabase
        creator = BackupCreator(db, context.getDatabasePath(TEST_DB), images, File(root, "work"), context.contentResolver, "9.9 (99)", "Test Phone")
        inspector = BackupInspector(db, context.contentResolver)
    }

    @After
    fun cleanUp() {
        if (::db.isInitialized && db.isOpen) db.close()
        context.deleteDatabase(TEST_DB)
        File(context.cacheDir, "$TEST_DB.lck").delete()
        root.deleteRecursively()
        shared.delete()
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

    private fun backup(name: String = "DistriGo-test.distrigo"): File {
        val file = File(saved, name)
        creator.create(Uri.fromFile(file))
        return file
    }

    private fun inspect(file: File) = inspector.inspect(Uri.fromFile(file))

    private fun preview(file: File) = (inspect(file) as BackupInspection.Restorable).preview

    private fun problem(file: File) = (inspect(file) as BackupInspection.NotRestorable).problem

    /** The same backup with its manifest changed, as a later or earlier app would have written it. */
    private fun withManifest(file: File, change: (BackupManifest) -> BackupManifest): File {
        val parts = File(root, "parts").apply { deleteRecursively(); mkdirs() }
        ZipInputStream(file.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                File(parts, entry.name).apply { parentFile!!.mkdirs() }.outputStream().use { zip.copyTo(it) }
            }
        }
        val manifest = (BackupManifest.parse(File(parts, BackupFormat.MANIFEST_ENTRY).readText()) as ManifestResult.Valid).manifest
        val out = File(saved, "changed-" + file.name)
        out.outputStream().use { stream ->
            BackupArchive.write(change(manifest), manifest.entries.associate { it.name to File(parts, it.name) }, stream)
        }
        return out
    }

    @Test
    fun anIntactBackupIsPreviewedNextToTheDataOnThePhone() {
        product("Lait Candia 1L", "img:${photo(3)}")
        product("Yaourt Soummam")
        val file = backup()
        product("Fromage Président")

        val preview = preview(file)

        assertEquals(2L, preview.manifest.rowCounts["products"])
        assertEquals(3L, preview.currentCounts["products"])
        assertEquals("products" to (2L to 3L), preview.headline.first())
        assertEquals(BackupPreview.HEADLINE_TABLES, preview.headline.map { it.first })
        assertEquals(1, preview.photoCount)
        assertTrue(preview.sameDatabase)
        assertTrue(preview.fromThisPhone)
        assertFalse(preview.needsUpgrade)
        assertEquals("DistriGo-test.distrigo", preview.fileName)
        assertEquals(file.length(), preview.fileSize)
    }

    /** Through a content provider, as a picked file arrives: the name and size come from the provider. */
    @Test
    fun aFileFromAContentProviderIsReadWithItsNameAndSize() {
        product("Lait Candia 1L")
        shared.parentFile!!.mkdirs()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shared)
        assertEquals("content", uri.scheme)
        creator.create(uri)

        val preview = (inspector.inspect(uri) as BackupInspection.Restorable).preview

        assertEquals(shared.name, preview.fileName)
        assertEquals(shared.length(), preview.fileSize)
        assertEquals(1L, preview.manifest.rowCounts["products"])
    }

    @Test
    fun aBackupOfOtherDataOrFromAnotherPhoneIsMarkedSo() {
        product("Lait Candia 1L")
        val file = backup()
        sql.execSQL("UPDATE app_meta SET value = 'another-database' WHERE key = 'database_id'")
        sql.execSQL("UPDATE app_meta SET value = 'another-phone' WHERE key = 'device_id'")

        val preview = preview(file)

        assertFalse(preview.sameDatabase)
        assertFalse(preview.fromThisPhone)
    }

    @Test
    fun aChangedFileIsDamaged() {
        repeat(50) { product("Produit $it") }
        val file = backup()
        RandomAccessFile(file, "rw").use {
            val middle = it.length() / 2
            it.seek(middle)
            val byte = it.read()
            it.seek(middle)
            it.write(byte xor 0xFF)
        }

        assertTrue(problem(file) is BackupProblem.Damaged)
    }

    @Test
    fun aBackupFromANewerOrTooOldAppIsRefused() {
        product("Lait Candia 1L")
        val file = backup()

        assertEquals(BackupProblem.NewerDatabase(DATABASE_VERSION + 1, DATABASE_VERSION), problem(withManifest(file) { it.copy(schemaVersion = DATABASE_VERSION + 1) }))
        assertEquals(BackupProblem.DatabaseTooOld(31, 32), problem(withManifest(file) { it.copy(schemaVersion = 31) }))
    }

    @Test
    fun aBackupFromAnEarlierAppIsRestorableAndWillBeUpgraded() {
        product("Lait Candia 1L")
        val preview = preview(withManifest(backup()) { it.copy(schemaVersion = 45) })

        assertTrue(preview.needsUpgrade)
    }

    @Test
    fun whatIsNotABackupOrCannotBeOpenedSaysSo() {
        val text = File(saved, "notes.txt").apply { writeText("Liste de courses") }
        val empty = File(saved, "empty.distrigo").apply { writeBytes(ByteArray(0)) }

        assertEquals(BackupProblem.NotABackup, problem(text))
        assertEquals(BackupProblem.NotABackup, problem(empty))
        assertEquals(BackupProblem.CannotOpen, problem(File(saved, "gone.distrigo")))
        assertEquals(
            BackupInspection.NotRestorable(BackupProblem.CannotOpen),
            inspector.inspect(Uri.parse("content://${context.packageName}.fileprovider/nowhere/gone.distrigo"))
        )
    }

    /** Previewing reads the file and the database, and changes neither. */
    @Test
    fun previewingChangesNothing() {
        product("Lait Candia 1L")
        val file = backup()
        val bytes = file.readBytes()
        val lastBackup = db.appMetaDao().get(BackupCreator.KEY_LAST_AT)

        preview(file)

        assertTrue(bytes.contentEquals(file.readBytes()))
        assertEquals(lastBackup, db.appMetaDao().get(BackupCreator.KEY_LAST_AT))
        assertEquals(1L, sql.query("SELECT COUNT(*) FROM products").use { it.moveToFirst(); it.getLong(0) })
    }

    private companion object {
        const val TEST_DB = "backup-inspector-test"
        const val TEST_DIR = "backup-inspector-test"
        const val TEST_DEVICE = "7e57de71-0000-4000-8000-000000000004"
    }
}
