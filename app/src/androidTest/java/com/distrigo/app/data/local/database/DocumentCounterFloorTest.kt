package com.distrigo.app.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.repository.recordedSale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Restoring an older backup on the same phone does not issue its document numbers again (BAK-01): the counters go no
 * lower than this phone's marks, kept outside the database.
 *
 * A named file database, not the app's — [TEST_DB] is deleted before and after every test — and marks in their own
 * folder. A restore is played as RestoreInstaller does it: the marks are raised from the database about to go, and
 * its file is replaced by the older one.
 */
@RunWith(AndroidJUnit4::class)
class DocumentCounterFloorTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbFile: File get() = context.getDatabasePath(TEST_DB)
    private val folder = File(context.cacheDir, "counter-floor-test")
    private val marks = DocumentCounterMarks(File(folder, "high-water"))
    private var db: AppDatabase? = null

    @Before
    fun startClean() {
        assertNotEquals("distrigo", TEST_DB)
        cleanUp()
        folder.mkdirs()
    }

    @After
    fun cleanUp() {
        db?.takeIf { it.isOpen }?.close()
        context.deleteDatabase(TEST_DB)
        folder.deleteRecursively()
    }

    /** Sales 1 and 2 backed up, 3 and 4 made after: once the backup is restored, the next sale is 5, not 3. */
    @Test
    fun aRestoredOlderBackupContinuesAfterTheNumbersIssued() = runBlocking {
        open()
        val client = client()
        assertEquals(listOf("V-ABCD-000001", "V-ABCD-000002"), List(2) { sale(client) })
        close()
        val backup = File(folder, "backup.db").also { dbFile.copyTo(it) }

        open()
        assertEquals(listOf("V-ABCD-000003", "V-ABCD-000004"), List(2) { sale(client) })
        close()

        restore(backup)
        open()
        assertEquals("V-ABCD-000005", sale(client))
    }

    /** Documents made since the last open are counted too: the restore reads them from the data it replaces. */
    @Test
    fun theNumbersOfTheReplacedDataCountWithoutAReopen() = runBlocking {
        open()
        val client = client()
        sale(client)
        close()
        val backup = File(folder, "backup.db").also { dbFile.copyTo(it) }

        // One session: open, three sales, close — never reopened before the restore
        open()
        repeat(3) { sale(client) }
        close()
        assertEquals(1L, marks.read()["numbering.ventes"])

        restore(backup)
        assertEquals(4L, marks.read()["numbering.ventes"])
        open()
        assertEquals("V-ABCD-000005", sale(client))
    }

    /** A newer database than the marks — the first open with this fix, or marks lost — keeps its own counters. */
    @Test
    fun countersAboveTheMarksAreKept() = runBlocking {
        open()
        val client = client()
        repeat(3) { sale(client) }
        close()
        File(folder, "high-water").writeText("numbering.ventes=1\n")

        open()
        assertEquals("V-ABCD-000004", sale(client))
        assertEquals(3L, marks.read()["numbering.ventes"])
    }

    // ── helpers ──

    private fun open() {
        db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .withMigrationPolicy()
            .withChangeTracking()
            .withDeviceIdentity { "abcd1234-0000-4000-8000-000000000000" }
            .withDocumentCounterFloor(marks)
            .build()
            .also { it.openHelper.writableDatabase }
    }

    private fun close() {
        db!!.close()
    }

    /** As RestoreInstaller: the marks first, from the data about to go; then the older file in its place. */
    private fun restore(backup: File) {
        marks.rememberFrom(dbFile)
        context.deleteDatabase(TEST_DB)
        backup.copyTo(dbFile)
    }

    private suspend fun client(): Int {
        val products = ProductRepository(db!!.productDao(), db!!.categoryDao(), db!!.supplierDao(), db!!)
        return (products.addClient(mapOf("name" to "Épicerie El Amel"))["id"] as Number).toInt()
    }

    /** A new sale's number. */
    private suspend fun sale(client: Int): String {
        val id = db!!.recordedSale(client, "depot").venteId
        return db!!.venteDao().getVenteById(id)!!.numero!!
    }

    private companion object {
        const val TEST_DB = "counter-floor-test"
    }
}
