package com.distrigo.app.data.local.database

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.repository.ChargeRepository
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Queries by day and by month select what happened on the local day and in the local month.
 *
 * Instants are built in the device's own zone, so the cases are the local midnights wherever this runs;
 * in Algeria they are the 00:00–01:00 hour that used to land on the previous day.
 */
@RunWith(AndroidJUnit4::class)
class LocalDayQueriesTest {

    private val zone = ZoneId.systemDefault()
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withChangeTracking().build()
        sql = db.openHelper.writableDatabase
    }

    @After
    fun close() {
        db.close()
    }

    private fun at(day: LocalDate, hour: Int, minute: Int): String =
        day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toString()

    /** Filtering movements from one day to the same day gives that whole local day — including the last day, which used to be left out. */
    @Test
    fun movementsFilteredByDayAreTheLocalDayAndIncludeTheEndDay() = runBlocking {
        val day = LocalDate.of(2026, 9, 13)
        val stamps = mapOf(
            "the evening before" to at(day.minusDays(1), 23, 50),
            "just after midnight" to at(day, 0, 30),
            "late that night" to at(day, 23, 30),
            "just after the next midnight" to at(day.plusDays(1), 0, 10),
        )
        for ((label, createdAt) in stamps) {
            sql.execSQL(
                "INSERT INTO stock_movements (product_id, product_name, type, direction, quantity, emplacement, source_label, " +
                    "source_type, source_id, total_value, created_at, uuid) VALUES (1, 'Lait Candia 1L', 'vente', 'sortie', 1.0, " +
                    "'depot', '$label', 'vente', 1, 0.0, '$createdAt', 'u-$label')"
            )
        }
        val repository = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)

        val sameDay = repository.getFilteredMovements(dateFrom = day.toString(), dateTo = day.toString())
        assertEquals(setOf("just after midnight", "late that night"), sameDay.map { it.source_label }.toSet())

        val untilThatDay = repository.getFilteredMovements(dateTo = day.toString())
        assertEquals(3, untilThatDay.size)
    }

    /** A charge made at 00:30 on the 1st counts in its own month, not the previous one. */
    @Test
    fun aChargeJustAfterMidnightOnTheFirstCountsInItsMonth() = runBlocking {
        val charges = ChargeRepository(db.chargeDao())
        charges.seedDefaultChargeTypesIfNeeded()
        val type = db.chargeDao().getAllChargeTypes().first()
        val subtype = db.chargeDao().getSubTypesForType(type.id).first()
        fun charge(amount: Double, dateTime: String) = sql.execSQL(
            "INSERT INTO charges (type_id, type_name, subtype_id, subtype_name, montant, date_time, created_at, uuid) " +
                "VALUES (${type.id}, '${type.name.replace("'", "''")}', ${subtype.id}, '${subtype.name.replace("'", "''")}', " +
                "$amount, '$dateTime', '$dateTime', 'u-$amount')"
        )
        charge(1000.0, at(LocalDate.of(2026, 10, 1), 0, 30))
        charge(200.0, at(LocalDate.of(2026, 9, 30), 23, 30))

        fun totalFor(month: String) = runBlocking {
            charges.getChargeTypesWithStats(month).single { it.id == type.id }.total_this_month
        }
        assertEquals(1000.0, totalFor("2026-10"), 0.0)
        assertEquals(200.0, totalFor("2026-09"), 0.0)
        assertEquals(listOf(1000.0), charges.getCharges(subtype.id, "2026-10").map { it.montant })
    }
}
