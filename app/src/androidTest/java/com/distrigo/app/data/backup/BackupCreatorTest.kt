package com.distrigo.app.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Making a backup end to end: copy, build, save, read back, record.
 *
 * On the test's own database [TEST_DB] and folders under `cacheDir/[TEST_DIR]`, saved to a `file://`
 * location there, which goes through the same ContentResolver calls a picked document does.
 */
@RunWith(AndroidJUnit4::class)
class BackupCreatorTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, TEST_DIR)
    private val images = File(root, "images")
    private val work = File(root, "work")
    private val saved = File(root, "saved")
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var creator: BackupCreator

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
        creator = BackupCreator(db, context.getDatabasePath(TEST_DB), images, work, context.contentResolver, "9.9 (99)", "Test Phone")
    }

    @After
    fun cleanUp() {
        if (::db.isInitialized && db.isOpen) db.close()
        context.deleteDatabase(TEST_DB)
        File(context.cacheDir, "$TEST_DB.lck").delete()
        root.deleteRecursively()
    }

    private fun product(name: String, image: String? = null) = sql.execSQL(
        "INSERT INTO products (name, selling_price, purchase_price, stock, min_stock, unit_type, packages, pack_size, " +
            "has_expiry, camion_stock, image_uri, uuid) VALUES (?, 120.0, 100.0, 0, 0, 'piece', 0, 1, 0, 0, ?, ?)",
        arrayOf(name, image, UUID.randomUUID().toString())
    )

    /** A photo stored the way ImageStore stores one: named for the SHA-256 of its bytes. */
    private fun photo(seed: Int): String {
        val bytes = ByteArray(5_000) { (it * seed % 251).toByte() }
        val hash = BackupFormat.sha256(bytes.inputStream())
        File(images, "$hash.jpg").writeBytes(bytes)
        return hash
    }

    private fun destination(name: String = "DistriGo-test.distrigo") = Uri.fromFile(File(saved, name))

    private fun meta(key: String): String? = db.appMetaDao().get(key)

    @Test
    fun aDataOnlyBackupHoldsNoPhotosAndSaysSo() {
        product("Lait Candia 1L", "img:${photo(3)}")

        val created = creator.create(destination(), record = false, includePhotos = false)

        assertFalse(created.manifest.photosIncluded)
        assertTrue(created.manifest.images.isEmpty())
        assertEquals(0, created.missingPhotos)
        // The flag survives the file: what is written is what is read back.
        val parsed = BackupManifest.parse(created.manifest.toJson()) as ManifestResult.Valid
        assertFalse(parsed.manifest.photosIncluded)
        // Older files, without the field, hold their photos.
        val older = BackupManifest.parse(created.manifest.toJson().replace("\"photos_included\": false,", "")) as ManifestResult.Valid
        assertTrue(older.manifest.photosIncluded)
    }

    @Test
    fun aBackupIsSavedVerifiedAndRecorded() {
        product("Lait Candia 1L", "img:${photo(3)}")
        product("Yaourt Soummam", "img:${photo(5)}")
        sql.execSQL("INSERT OR REPLACE INTO business_settings (id, logo_ref) VALUES (1, 'img:${photo(7)}')")
        val steps = mutableListOf<BackupCreator.Step>()

        val created = creator.create(destination()) { steps += it }

        val file = File(saved, "DistriGo-test.distrigo")
        assertEquals(BackupCreator.Step.entries, steps)
        assertEquals(file.length(), created.size)
        assertEquals("DistriGo-test.distrigo", created.fileName)
        assertEquals(Verification.Verified(created.manifest), file.inputStream().use { BackupArchive.verify(it) })

        val manifest = created.manifest
        assertEquals(DATABASE_VERSION, manifest.schemaVersion)
        assertEquals("9.9 (99)", manifest.appVersion)
        assertEquals("Test Phone", manifest.deviceModel)
        assertEquals(TEST_DEVICE, manifest.deviceId)
        assertEquals(meta("database_id"), manifest.databaseId)
        assertEquals(2L, manifest.rowCounts["products"])
        assertEquals(3, manifest.images.size)
        assertEquals(0, created.missingPhotos + created.damagedPhotos)

        assertEquals(manifest.createdAt.toString(), meta(BackupCreator.KEY_LAST_AT))
        assertEquals(created.size.toString(), meta(BackupCreator.KEY_LAST_SIZE))
        assertEquals("DistriGo-test.distrigo", meta(BackupCreator.KEY_LAST_NAME))
        assertFalse("the scratch space is emptied", work.exists())
    }

    /** The database inside the file is the data: unpacked, it opens and holds the rows. */
    @Test
    fun theDatabaseInsideTheFileOpensWithTheData() {
        product("Lait Candia 1L")
        creator.create(destination())

        val unpacked = File(root, "unpacked.db")
        ZipInputStream(File(saved, "DistriGo-test.distrigo").inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == BackupFormat.DATABASE_ENTRY) unpacked.outputStream().use { zip.copyTo(it) }
            }
        }
        val copy = SQLiteDatabase.openDatabase(unpacked.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            copy.rawQuery("SELECT name FROM products", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("Lait Candia 1L", it.getString(0))
            }
            assertEquals(DATABASE_VERSION, copy.version)
            copy.rawQuery("SELECT value FROM app_meta WHERE key = ?", arrayOf(BackupCreator.KEY_LAST_AT)).use {
                assertFalse("the file describes the data as it was before this backup was recorded", it.moveToFirst())
            }
        } finally {
            copy.close()
        }
    }

    @Test
    fun missingAndDamagedPhotosAreLeftOutAndCounted() {
        val good = photo(3)
        val damaged = photo(5)
        File(images, "$damaged.jpg").writeBytes(ByteArray(10))
        product("Bon", "img:$good")
        product("Abîmée", "img:$damaged")
        product("Absente", "img:${"cd".repeat(32)}")

        val created = creator.create(destination())

        assertEquals(listOf(BackupFormat.imageEntry(good)), created.manifest.images.map { it.name })
        assertEquals(1, created.missingPhotos)
        assertEquals(1, created.damagedPhotos)
    }

    /** A location that cannot be written leaves nothing behind and records nothing. */
    @Test
    fun aFailedSaveLeavesNoFileAndNoRecord() {
        product("Lait Candia 1L")
        val unwritable = Uri.fromFile(File(saved, "no-such-folder/DistriGo-test.distrigo"))

        try {
            creator.create(unwritable)
            fail("saving into a folder that does not exist succeeded")
        } catch (e: BackupFailedException) {
            assertEquals(BackupFailedException.Reason.WRITE_FAILED, e.reason)
        }
        assertNull(meta(BackupCreator.KEY_LAST_AT))
        assertFalse(work.exists())
    }

    /** The picker creates the file before the backup starts; a backup that fails removes it. */
    @Test
    fun aBackupThatFailsRemovesTheFileThePickerCreated() {
        product("Lait Candia 1L")
        val picked = File(saved, "DistriGo-test.distrigo").apply { writeBytes(ByteArray(0)) }
        val noDatabaseFile = BackupCreator(db, File(root, "absent.db"), images, work, context.contentResolver, "9.9 (99)", "Test Phone")

        try {
            noDatabaseFile.create(Uri.fromFile(picked))
            fail("a backup without its database file succeeded")
        } catch (expected: Exception) {
        }
        assertFalse(picked.exists())
        assertFalse(work.exists())
    }

    /** Saving over an older, longer file leaves exactly the new backup. */
    @Test
    fun savingOverALongerFileLeavesOnlyTheNewBackup() {
        val existing = File(saved, "DistriGo-test.distrigo").apply { writeBytes(ByteArray(3_000_000) { 7 }) }
        product("Lait Candia 1L")

        val created = creator.create(Uri.fromFile(existing))

        assertEquals(created.size, existing.length())
        assertEquals(Verification.Verified(created.manifest), existing.inputStream().use { BackupArchive.verify(it) })
    }

    @Test
    fun aSecondBackupRecordsItself() {
        product("Lait Candia 1L")
        val first = creator.create(destination("one.distrigo"))
        product("Yaourt Soummam")
        val second = creator.create(destination("two.distrigo"))

        assertEquals(1L, first.manifest.rowCounts["products"])
        assertEquals(2L, second.manifest.rowCounts["products"])
        assertTrue(second.manifest.createdAt >= first.manifest.createdAt)
        assertEquals("two.distrigo", meta(BackupCreator.KEY_LAST_NAME))
        assertEquals(first.manifest.databaseId, second.manifest.databaseId)
    }

    private companion object {
        const val TEST_DB = "backup-creator-test"
        const val TEST_DIR = "backup-creator-test"
        const val TEST_DEVICE = "7e57de71-0000-4000-8000-000000000003"
    }
}
