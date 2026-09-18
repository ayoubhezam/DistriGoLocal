package com.distrigo.app.data.local.database

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.model.DefaultPerteType
import com.distrigo.app.data.model.defaultChargeSubTypeUuid
import com.distrigo.app.data.model.defaultChargeTypeUuid
import com.distrigo.app.data.repository.ChargeRepository
import com.distrigo.app.data.repository.PerteRepository
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.repository.RetourClientRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two phones seed the same built-in types with the same uuids, and returns find their perte type by
 * that identity. Each "phone" is its own in-memory database.
 */
@RunWith(AndroidJUnit4::class)
class DefaultTypesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun <T> withDatabase(block: suspend (AppDatabase, SupportSQLiteDatabase) -> T): T = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withChangeTracking().build()
        try {
            block(db, db.openHelper.writableDatabase)
        } finally {
            db.close()
        }
    }

    private suspend fun seedAll(db: AppDatabase) {
        ChargeRepository(db.chargeDao(), db).seedDefaultChargeTypesIfNeeded()
        PerteRepository(db).seedDefaultPerteTypesIfNeeded()
    }

    /** name -> uuid for types, "type/subtype" -> uuid for subtypes, name -> uuid for perte types. */
    private fun builtIns(sql: SupportSQLiteDatabase): Triple<Map<String, String>, Map<String, String>, Map<String, String>> {
        fun pairs(query: String) = sql.query(query).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }
        return Triple(
            pairs("SELECT name, uuid FROM charge_types WHERE is_default = 1"),
            pairs("SELECT t.name || '/' || s.name, s.uuid FROM charge_subtypes s JOIN charge_types t ON t.id = s.type_id WHERE s.is_default = 1"),
            pairs("SELECT name, uuid FROM perte_types WHERE is_default = 1"),
        )
    }

    @Test
    fun twoPhonesSeedTheSameBuiltIns() {
        val first = withDatabase { db, sql -> seedAll(db); builtIns(sql) }
        val second = withDatabase { db, sql -> seedAll(db); builtIns(sql) }

        assertEquals(first, second)
        assertEquals(6, first.first.size)
        assertEquals(21, first.second.size)
        assertEquals(6, first.third.size)

        assertEquals(defaultChargeTypeUuid("vehicule"), first.first["Véhicule"])
        assertEquals(defaultChargeSubTypeUuid("achats", "divers"), first.second["Achats/Divers"])
        assertEquals(defaultChargeTypeUuid("divers"), first.first["Divers"])
        for (type in DefaultPerteType.entries) {
            assertEquals(type.seedName, type.uuid, first.third[type.seedName])
        }
    }

    @Test
    fun seedingAgainAddsNothing() = withDatabase { db, sql ->
        seedAll(db)
        seedAll(db)
        assertEquals(6L, sql.long("SELECT COUNT(*) FROM charge_types"))
        assertEquals(21L, sql.long("SELECT COUNT(*) FROM charge_subtypes"))
        assertEquals(6L, sql.long("SELECT COUNT(*) FROM perte_types"))
    }

    /**
     * A return files its loss under the built-in type by identity: here the built-in "Casse" has been
     * re-worded and a custom type has taken the name, which a lookup by name would have picked instead.
     */
    @Test
    fun aReturnFindsTheBuiltInTypeByIdentityNotName() = withDatabase { db, sql ->
        val builtIn = createDefectiveReturn(db, sql) {
            sql.execSQL("UPDATE perte_types SET name = 'Casse (défaut)' WHERE uuid = '${DefaultPerteType.CASSE.uuid}'")
            PerteRepository(db).addPerteType("Casse", "broken_image", "#F04438")
        }
        assertEquals(sql.long("SELECT id FROM perte_types WHERE uuid = '${DefaultPerteType.CASSE.uuid}'"), builtIn)
    }

    /** A device whose built-ins kept random uuids still finds them, by name, as before. */
    @Test
    fun withoutItsFixedUuidTheBuiltInIsStillFoundByName() = withDatabase { db, sql ->
        val found = createDefectiveReturn(db, sql) {
            sql.execSQL("UPDATE perte_types SET uuid = 'legacy-random-uuid' WHERE name = 'Casse'")
        }
        assertEquals(sql.long("SELECT id FROM perte_types WHERE name = 'Casse' AND is_default = 1"), found)
    }

    /** Seeds, runs [prepare], records a "Produit défectueux" return, and gives the perte's type id. */
    private suspend fun createDefectiveReturn(db: AppDatabase, sql: SupportSQLiteDatabase, prepare: suspend () -> Unit): Long {
        PerteRepository(db).seedDefaultPerteTypesIfNeeded()
        prepare()
        val repository = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
        val product = (repository.addProduct(mapOf("name" to "Lait Candia 1L", "selling_price" to 110.0))["id"] as Number).toInt()
        val client = (repository.addClient(mapOf("name" to "Épicerie El Amel"))["id"] as Number).toInt()
        val result = RetourClientRepository(db).createRetour(
            client, null, "2026-09-16", "Produit défectueux", null,
            listOf(mapOf("product_id" to product, "quantity" to 1.0))
        )
        assertTrue(result.toString(), result["error"] == null)
        return sql.long("SELECT type_id FROM pertes")
    }

    private fun SupportSQLiteDatabase.long(query: String): Long =
        query(query).use { assertTrue(query, it.moveToFirst()); it.getLong(0) }
}
