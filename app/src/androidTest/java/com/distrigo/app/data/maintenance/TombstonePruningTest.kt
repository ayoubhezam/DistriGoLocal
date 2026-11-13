package com.distrigo.app.data.maintenance

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.repository.recordedSale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tombstones are kept [Housekeeping.TOMBSTONE_HORIZON_DAYS] days, then pruned (DB-02). */
@RunWith(AndroidJUnit4::class)
class TombstonePruningTest {

    private lateinit var db: AppDatabase

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withChangeTracking().build()
    }

    @After
    fun close() = db.close()

    @Test
    fun aDeletesTombstonesGoAfterTheHorizon() = runBlocking {
        val repo = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
        val milk = (repo.addProduct(mapOf("name" to "Lait", "selling_price" to 120.0, "purchase_price" to 90.0, "stock" to 20.0))["id"] as Number).toInt()
        val client = (repo.addClient(mapOf("name" to "MOSTAFA"))["id"] as Number).toInt()
        repo.deleteVente(db.recordedSale(client, "depot", milk to 2.0).venteId)
        val sql = db.openHelper.writableDatabase
        val written = count()
        assertTrue("the delete left no tombstone", written > 0)

        // Stamped in milliseconds, as the horizon is counted
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        assertEquals(0, Housekeeping.pruneTombstones(sql, now))
        assertEquals(0, Housekeeping.pruneTombstones(sql, now + (Housekeeping.TOMBSTONE_HORIZON_DAYS - 1) * day))
        assertEquals(written, count())

        // One older than the horizon goes, a recent one stays
        sql.execSQL("INSERT INTO tombstones (table_name, row_uuid, deleted_at, device_id) VALUES ('clients', 'u-recent', ?, NULL)", arrayOf<Any>(now + 100 * day))
        assertEquals(written, Housekeeping.pruneTombstones(sql, now + (Housekeeping.TOMBSTONE_HORIZON_DAYS + 1) * day))
        assertEquals(1, count())
    }

    private fun count(): Int = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM tombstones").use { it.moveToFirst(); it.getInt(0) }
}
