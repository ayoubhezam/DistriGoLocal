package com.distrigo.app.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * A copy of the live database, taken while it is in use, is one consistent, checked file.
 *
 * Everything here is the test's own: the database [TEST_DB] and the folders under `cacheDir/[TEST_DIR]`
 * are deleted before and after every test. The app's database and photos are never opened.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSnapshotTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, TEST_DIR)
    private val images = File(root, "images")
    private val staging = File(root, "staging")
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase

    @Before
    fun open() {
        assertNotEquals("distrigo", TEST_DB)
        cleanUp()
        images.mkdirs()
        db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .withMigrationPolicy()
            .withChangeTracking()
            .withDeviceIdentity { TEST_DEVICE }
            .build()
        sql = db.openHelper.writableDatabase
    }

    @After
    fun cleanUp() {
        if (::db.isInitialized && db.isOpen) db.close()
        context.deleteDatabase(TEST_DB)
        File(context.cacheDir, "$TEST_DB.lck").delete()
        root.deleteRecursively()
    }

    private fun snapshot(checkpointFirst: Boolean = true) =
        DatabaseSnapshot.take(db, context.getDatabasePath(TEST_DB), images, staging, checkpointFirst)

    private fun product(name: String, image: String? = null) = sql.execSQL(
        "INSERT INTO products (name, selling_price, purchase_price, stock, min_stock, unit_type, packages, pack_size, " +
            "has_expiry, camion_stock, image_uri, uuid) VALUES (?, 120.0, 100.0, 0, 0, 'piece', 0, 1, 0, 0, ?, ?)",
        arrayOf(name, image, java.util.UUID.randomUUID().toString())
    )

    private fun photo(seed: Int): String {
        val hash = seed.toString(16).padStart(2, '0').repeat(32)
        File(images, "$hash.jpg").writeBytes(ByteArray(100) { seed.toByte() })
        return hash
    }

    private fun <T> openCopy(file: File, block: (SQLiteDatabase) -> T): T {
        val copy = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try { return block(copy) } finally { copy.close() }
    }

    private fun SQLiteDatabase.count(query: String): Long =
        rawQuery(query, null).use { it.moveToFirst(); it.getLong(0) }

    @Test
    fun aCopyIsOneCheckedFileHoldingWhatTheDatabaseHolds() {
        product("Lait Candia 1L")
        product("Yaourt Soummam")
        sql.execSQL("INSERT INTO clients (name, balance, customer_type, uuid) VALUES ('Épicerie El Amel', 0, 'retail', 'u-client')")

        val before = Instant.now()
        val snapshot = snapshot()

        assertEquals(listOf(BackupFormat.DATABASE_ENTRY), staging.list()!!.toList())
        assertEquals(File(staging, BackupFormat.DATABASE_ENTRY), snapshot.database)
        assertTrue(snapshot.createdAt >= before)
        assertEquals(DATABASE_VERSION, snapshot.schemaVersion)
        assertEquals(TEST_DEVICE, snapshot.deviceId)
        assertEquals(sql.query("SELECT value FROM app_meta WHERE key = 'database_id'").use { it.moveToFirst(); it.getString(0) }, snapshot.databaseId)
        assertEquals(2L, snapshot.rowCounts["products"])
        assertEquals(1L, snapshot.rowCounts["clients"])
        assertTrue("every table is counted, bookkeeping is not", "tombstones" in snapshot.rowCounts && "purchase_drafts" in snapshot.rowCounts)
        assertTrue(snapshot.rowCounts.keys.none { it.startsWith("sqlite_") || it == "room_master_table" || it == "android_metadata" })

        openCopy(snapshot.database) { copy ->
            assertEquals(2L, copy.count("SELECT COUNT(*) FROM products"))
            assertEquals(DATABASE_VERSION, copy.version)
        }
    }

    /** Rows still only in the write-ahead journal reach the copy: its journal is copied and folded in. */
    @Test
    fun rowsNotYetCheckpointedAreInTheCopy() {
        sql.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        repeat(20) { product("Produit $it") }
        val wal = File(context.getDatabasePath(TEST_DB).path + "-wal")
        assertTrue("the rows are in the journal", wal.length() > 0)

        val snapshot = snapshot(checkpointFirst = false)

        assertEquals(20L, snapshot.rowCounts["products"])
        assertEquals(listOf(BackupFormat.DATABASE_ENTRY), staging.list()!!.toList())
        openCopy(snapshot.database) { assertEquals(20L, it.count("SELECT COUNT(*) FROM products")) }
    }

    /**
     * A writer keeps committing pairs of rows while copies are taken. Every copy holds whole pairs, and
     * the writer is only held up, never failed. The rows are large, so the journal checkpoints often.
     */
    @Test
    fun copiesTakenDuringWritesHoldOnlyCommittedWork() {
        val padding = "x".repeat(2000)
        val stop = AtomicBoolean(false)
        val failure = AtomicReference<Throwable>()
        val writer = thread {
            try {
                var i = 0
                while (!stop.get()) {
                    db.runInTransaction {
                        sql.execSQL("INSERT INTO app_meta (key, value) VALUES ('pair.$i.a', '$padding')")
                        Thread.sleep(1)
                        sql.execSQL("INSERT INTO app_meta (key, value) VALUES ('pair.$i.b', '$padding')")
                    }
                    i++
                }
            } catch (t: Throwable) {
                failure.set(t)
            }
        }
        try {
            val seen = mutableListOf<Long>()
            repeat(8) {
                Thread.sleep(40)
                val snapshot = snapshot(checkpointFirst = it % 2 == 0)
                openCopy(snapshot.database) { copy ->
                    val a = copy.count("SELECT COUNT(*) FROM app_meta WHERE key LIKE 'pair.%.a'")
                    val b = copy.count("SELECT COUNT(*) FROM app_meta WHERE key LIKE 'pair.%.b'")
                    assertEquals("copy $it holds whole pairs", a, b)
                    seen += a
                }
            }
            assertTrue("the writer kept going between copies: $seen", seen.last() > seen.first())
        } finally {
            stop.set(true)
            writer.join()
        }
        failure.get()?.let { throw AssertionError("the writer failed", it) }
    }

    @Test
    fun onlyPhotosTheDataRefersToAndThatExistAreListed() {
        val productPhoto = photo(1)
        val logo = photo(2)
        val inDraft = photo(3)
        photo(4) // on the phone, referred to by nothing
        val missing = "ab".repeat(32)

        product("Lait Candia 1L", "img:$productPhoto")
        product("Lait Candia 1L (ancienne photo)", "img:$productPhoto")
        product("Sans photo", "img:$missing")
        sql.execSQL("INSERT OR REPLACE INTO business_settings (id, logo_ref) VALUES (1, 'img:$logo')")
        sql.execSQL(
            "INSERT INTO purchase_drafts (items_json, note, montant_paye, item_count, total, last_step, created_at, updated_at) " +
                "VALUES (?, '', '0', 1, 0, 'items', '2026-09-17T10:00:00Z', '2026-09-17T10:00:00Z')",
            arrayOf("""[{"name":"Yaourt","image":"img:$inDraft"},{"image":"content://media/1"}]""")
        )

        val snapshot = snapshot()

        assertEquals(listOf(productPhoto, logo, inDraft).sorted(), snapshot.imageHashes)
        assertEquals(1, snapshot.missingImages)
    }

    /** The copy is separate: writing to the database afterwards does not change it. */
    @Test
    fun theDatabaseStaysUsableAndTheCopyStaysAsTaken() {
        product("Avant")
        val snapshot = snapshot()
        product("Après")

        assertEquals(2L, sql.query("SELECT COUNT(*) FROM products").use { it.moveToFirst(); it.getLong(0) })
        openCopy(snapshot.database) { assertEquals(1L, it.count("SELECT COUNT(*) FROM products")) }

        val again = snapshot()
        assertEquals(2L, again.rowCounts["products"])
    }

    /** A damaged copy is reported, and left on disk rather than deleted by Android's default handler. */
    @Test
    fun aDamagedCopyFailsItsCheckAndIsKept() {
        repeat(200) { product("Produit numéro $it avec un nom assez long pour remplir plusieurs pages") }
        val copy = snapshot().database
        RandomAccessFile(copy, "rw").use { file ->
            val pageSize = 4096
            for (page in 2 until (file.length() / pageSize).toInt()) {
                file.seek(page.toLong() * pageSize + 8)
                file.write(ByteArray(64) { 0x5A })
            }
        }

        try {
            DatabaseSnapshot.inspect(copy, images, Instant.now())
            fail("a damaged copy passed")
        } catch (expected: SnapshotException) {
        }
        assertTrue(copy.isFile)
    }

    @Test
    fun aDatabaseWithoutItsIdentityIsRefused() {
        val bare = File(root, "bare.db")
        SQLiteDatabase.openOrCreateDatabase(bare, null).use {
            it.execSQL("CREATE TABLE app_meta (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        }
        try {
            DatabaseSnapshot.inspect(bare, images, Instant.now())
            fail("a database without database_id passed")
        } catch (expected: SnapshotException) {
            assertTrue(expected.message!!, "database_id" in expected.message!!)
        }
    }

    private companion object {
        const val TEST_DB = "backup-snapshot-test"
        const val TEST_DIR = "backup-snapshot-test"
        const val TEST_DEVICE = "7e57de71-0000-4000-8000-000000000002"
    }
}
