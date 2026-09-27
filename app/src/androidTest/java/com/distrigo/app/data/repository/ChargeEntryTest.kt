package com.distrigo.app.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the one-screen expense form reads and writes: a subtype's recent amounts and suppliers for its
 * one-tap chips, the subtype the quick entry starts on, and a new charge's id for "Annuler".
 *
 * In memory, so nothing touches the app's database on the device.
 */
@RunWith(AndroidJUnit4::class)
class ChargeEntryTest {

    private lateinit var db: AppDatabase
    private lateinit var charges: ChargeRepository

    @Before
    fun open() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withChangeTracking().build()
        charges = ChargeRepository(db.chargeDao(), db)
        charges.seedDefaultChargeTypesIfNeeded()
    }

    @After
    fun close() = db.close()

    @Test
    fun recentAmountsAreDistinctNewestFirst() = runBlocking {
        val sub = charges.getAllSubTypes().first().id
        listOf(2000.0 to "01", 3500.0 to "02", 2000.0 to "03", 5000.0 to "04", 1200.0 to "05")
            .forEach { (amount, day) -> charges.addCharge(sub, amount, "2026-09-${day}T10:00:00Z", null, null) }

        assertEquals(listOf(1200.0, 5000.0, 2000.0), charges.getRecentAmounts(sub))
    }

    @Test
    fun recentSuppliersIgnoreCaseAndBlanks() = runBlocking {
        val sub = charges.getAllSubTypes().first { it.has_fournisseur }.id
        charges.addCharge(sub, 100.0, "2026-09-01T10:00:00Z", "Naftal Hydra", null)
        charges.addCharge(sub, 100.0, "2026-09-02T10:00:00Z", "  ", null)
        charges.addCharge(sub, 100.0, "2026-09-03T10:00:00Z", "Naftal Kouba", null)
        charges.addCharge(sub, 100.0, "2026-09-04T10:00:00Z", "naftal hydra ", null)

        assertEquals(2, charges.getRecentSuppliers(sub).size)
        assertEquals("naftal hydra", charges.getRecentSuppliers(sub).first().lowercase())
    }

    @Test
    fun theQuickEntryStartsOnTheSubtypeUsedLast() = runBlocking {
        assertNull(charges.getLastUsedSubtypeId())
        val (a, b) = charges.getAllSubTypes().take(2)
        charges.addCharge(a.id, 100.0, "2026-09-01T10:00:00Z", null, null)
        charges.addCharge(b.id, 100.0, "2026-08-01T10:00:00Z", null, null)   // entered last, dated earlier
        assertEquals(b.id, charges.getLastUsedSubtypeId())

        db.chargeDao().softDeleteSubTypeById(b.id)   // a subtype since deleted is not offered
        assertEquals(a.id, charges.getLastUsedSubtypeId())
    }

    @Test
    fun annulerDeletesTheChargeJustAdded() = runBlocking {
        val sub = charges.getAllSubTypes().first().id
        val id = charges.addCharge(sub, 3500.0, "2026-09-27T10:00:00Z", null, "plein")
        assertEquals(3500.0, charges.getCharge(id)!!.montant, 0.0)

        charges.deleteCharge(id)
        assertNull(charges.getCharge(id))
    }
}
