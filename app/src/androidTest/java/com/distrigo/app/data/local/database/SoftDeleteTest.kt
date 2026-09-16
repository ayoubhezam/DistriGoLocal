package com.distrigo.app.data.local.database

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.repository.ChargeRepository
import com.distrigo.app.data.repository.PerteRepository
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deleting master data keeps the row but hides it from every read, and deleting anything else leaves
 * a tombstone.
 *
 * "Hidden" is checked against each DAO read of the table, because that is where the rule holds or
 * breaks: a query that forgets `deleted_at IS NULL` brings a deleted client back into a picker.
 * Deletes go through the repositories, the way the app makes them.
 *
 * In memory, so nothing touches the app's database on the device.
 */
@RunWith(AndroidJUnit4::class)
class SoftDeleteTest {

    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var repository: ProductRepository

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withChangeTracking().build()
        sql = db.openHelper.writableDatabase
        repository = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun deletedClientIsGoneFromEveryReadButKept() = runBlocking {
        val kept = repository.addClient(mapOf("name" to "Supérette Nour", "wilaya_name" to "Oran")).newId()
        repository.addClient(mapOf("name" to "Alimentation Bouzid", "wilaya_name" to "Oran"))
        val deleted = repository.addClient(mapOf("name" to "Épicerie El Amel", "wilaya_name" to "Alger")).newId()
        repository.addClient(mapOf("name" to "Épicerie El Amel 2", "wilaya_name" to "Alger"))
        repository.addClient(mapOf("name" to "Épicerie El Amel 3", "wilaya_name" to "Alger"))
        repository.createVente(
            clientId = deleted, tourneeId = null, source = "depot", items = emptyList(),
            note = null, montantPaye = 0.0
        )
        pin("clients", deleted, OLD)

        // Two of the three Alger clients go, leaving Oran the most common wilaya.
        repository.deleteClient(deleted)
        repository.deleteClient(deleted + 1)

        val dao = db.clientDao()
        assertTrue(dao.getAllClients().none { it.id == deleted })
        assertTrue(dao.observeAllClients().first().none { it.id == deleted })
        assertNull(dao.getClientById(deleted))
        assertEquals(listOf(kept), dao.getClientsByIds(listOf(kept, deleted)).map { it.id })
        assertEquals("Oran", dao.getMostCommonWilaya())
        // The sale is still there, and shows no live name, as it did when the client row was gone.
        val vente = db.venteDao().getVentesWithDetails(deleted).single()
        assertNull(vente.live_client_name)

        // The row itself is kept, marked, and stamped as changed; and no tombstone, since it still exists.
        val deletedAt = sql.long("SELECT deleted_at FROM clients WHERE id = $deleted")
        assertTrue(deletedAt > 0)
        assertTrue(sql.long("SELECT updated_at FROM clients WHERE id = $deleted") > OLD)
        assertEquals(0L, sql.long("SELECT COUNT(*) FROM tombstones"))

        // Deleting again changes nothing.
        repository.deleteClient(deleted)
        assertEquals(deletedAt, sql.long("SELECT deleted_at FROM clients WHERE id = $deleted"))
    }

    @Test
    fun deletedProductAndSupplierAreGoneFromEveryRead() = runBlocking {
        val supplier = repository.addSupplier(mapOf("name" to "Laiterie Soummam")).newId()
        val product = repository.addProduct(mapOf("name" to "Lait Candia 1L", "selling_price" to 110.0)).newId()
        val other = repository.addProduct(mapOf("name" to "Lben Soummam", "selling_price" to 90.0)).newId()
        repository.linkProductToSupplier(supplier, product, 95.0)
        repository.linkProductToSupplier(supplier, other, 80.0)

        repository.deleteProduct(product)

        val products = db.productDao()
        assertTrue(products.getAllProducts().none { it.id == product })
        assertTrue(products.observeAllProducts().first().none { it.id == product })
        assertNull(products.getProductById(product))
        assertEquals(listOf(other), products.getProductsBySupplier(supplier).map { it.id })
        assertEquals(listOf(other), repository.getSupplierProducts(supplier).map { it.id })
        assertEquals(1L, sql.long("SELECT COUNT(*) FROM products WHERE id = $product AND deleted_at IS NOT NULL"))

        repository.deleteSupplier(supplier)

        val suppliers = db.supplierDao()
        assertTrue(suppliers.getAllSuppliers().isEmpty())
        assertTrue(suppliers.observeAllSuppliers().first().isEmpty())
        assertNull(suppliers.getSupplierById(supplier))
        assertEquals(1L, sql.long("SELECT COUNT(*) FROM suppliers WHERE deleted_at IS NOT NULL"))
    }

    @Test
    fun deletedCatalogueGroupsAreGoneFromEveryRead() = runBlocking {
        val category = repository.addCategory(mapOf("name" to "Laitiers")).newId()
        val keptCategory = repository.addCategory(mapOf("name" to "Boissons")).newId()
        val sous = repository.addSousCategorie(mapOf("category_id" to keptCategory, "name" to "Jus")).newId()
        repository.addSousCategorie(mapOf("category_id" to keptCategory, "name" to "Sodas"))
        val marque = repository.addMarque(mapOf("name" to "Candia")).newId()

        repository.deleteCategory(category)
        repository.deleteSousCategorie(sous)
        repository.deleteMarque(marque)

        assertEquals(listOf(keptCategory), db.categoryDao().getAllCategories().map { it.id })
        assertNull(db.categoryDao().getCategoryById(category))
        assertEquals(listOf("Sodas"), db.sousCategorieDao().getAllSousCategories().map { it.name })
        assertEquals(listOf("Sodas"), db.sousCategorieDao().getSousCategoriesForCategory(keptCategory).map { it.name })
        assertNull(db.sousCategorieDao().getSousCategorieById(sous))
        assertTrue(db.marqueDao().getAllMarques().isEmpty())
        assertNull(db.marqueDao().getMarqueById(marque))
        for (table in listOf("categories", "sous_categories", "marques")) {
            assertEquals(table, 1L, sql.long("SELECT COUNT(*) FROM `$table` WHERE deleted_at IS NOT NULL"))
        }
    }

    @Test
    fun deletedChargeAndPerteTypesAreGoneFromEveryRead() = runBlocking {
        val charges = ChargeRepository(db.chargeDao())
        val dao = db.chargeDao()
        val type = charges.addChargeType("Location", "home", "#3F51B5").toInt()
        val typeSubtype = charges.addSubType(type, "Dépôt", "home", false).toInt()
        val keptType = charges.addChargeType("Véhicule", "directions_car", "#3F51B5").toInt()
        val subtype = charges.addSubType(keptType, "Lavage", "local_car_wash", false).toInt()
        charges.addSubType(keptType, "Carburant", "local_gas_station", true)

        charges.deleteChargeType(type)   // takes its subtype with it
        charges.deleteSubType(subtype)

        assertEquals(listOf(keptType), dao.getAllChargeTypes().map { it.id })
        assertNull(dao.getChargeTypeById(type))
        assertNull(dao.getSubTypeById(typeSubtype))
        assertNull(dao.getSubTypeById(subtype))
        assertEquals(listOf("Carburant"), dao.getSubTypesForType(keptType).map { it.name })
        assertEquals(listOf("Carburant"), dao.getAllSubTypes().map { it.name })
        assertEquals(mapOf(keptType to 1), dao.getSubTypeCountsByType().associate { it.type_id to it.count })
        assertEquals(1L, sql.long("SELECT COUNT(*) FROM charge_types WHERE deleted_at IS NOT NULL"))
        assertEquals(2L, sql.long("SELECT COUNT(*) FROM charge_subtypes WHERE deleted_at IS NOT NULL"))

        val pertes = PerteRepository(db)
        val perteType = pertes.addPerteType("Rongeurs", "pest_control", "#F04438").toInt()

        pertes.deletePerteType(perteType)

        assertTrue(db.perteDao().getAllPerteTypes().none { it.id == perteType })
        assertNull(db.perteDao().getPerteTypeById(perteType))
        assertEquals(1L, sql.long("SELECT COUNT(*) FROM perte_types WHERE deleted_at IS NOT NULL"))
    }

    /** Every business table has its tombstone trigger, and no other table does. */
    @Test
    fun everyBusinessTableRecordsHardDeletes() {
        val tracked = UpdatedAtTriggers.trackedTables(sql)
        assertEquals(35, tracked.size)
        for (table in tracked) {
            assertEquals(table, TombstoneTriggers.triggerSql(table), storedTriggerSql(sql, TombstoneTriggers.triggerName(table)))
        }
        val tombstoneTriggers = sql.query("SELECT name FROM sqlite_master WHERE type = 'trigger' AND name LIKE '%_tombstone'")
            .use { it.count }
        assertEquals(35, tombstoneTriggers)
    }

    /**
     * Editing a vente replaces its lines and movements, each replaced row leaving its uuid behind;
     * deleting the vente leaves the vente's too.
     */
    @Test
    fun editsAndDeletesOfDocumentsLeaveTombstones() = runBlocking {
        val product = repository.addProduct(
            mapOf("name" to "Lait Candia 1L", "selling_price" to 110.0, "stock" to 40.0)
        ).newId()
        val client = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()
        repository.createVente(
            clientId = client, tourneeId = null, source = "depot",
            items = listOf(mapOf("product_id" to product, "quantity" to 10.0, "unit_price" to 110.0)),
            note = null, montantPaye = 0.0
        )
        val vente = db.venteDao().getVentesWithDetails(client).single().vente
        val firstLine = sql.text("SELECT uuid FROM vente_items WHERE vente_id = ${vente.id}")
        val firstMovement = sql.text("SELECT uuid FROM stock_movements WHERE source_type = 'vente' AND source_id = ${vente.id}")

        repository.updateVente(
            id = vente.id, clientId = client,
            items = listOf(mapOf("product_id" to product, "quantity" to 12.0, "unit_price" to 110.0)),
            note = null, montantPaye = 0.0
        )
        assertEquals(
            setOf("vente_items" to firstLine, "stock_movements" to firstMovement),
            tombstones()
        )

        val secondLine = sql.text("SELECT uuid FROM vente_items WHERE vente_id = ${vente.id}")
        val secondMovement = sql.text("SELECT uuid FROM stock_movements WHERE source_type = 'vente' AND source_id = ${vente.id}")
        repository.deleteVente(vente.id)

        assertEquals(
            setOf(
                "vente_items" to firstLine, "stock_movements" to firstMovement,
                "vente_items" to secondLine, "stock_movements" to secondMovement,
                "ventes" to vente.uuid,
            ),
            tombstones()
        )
        assertTrue(sql.long("SELECT MIN(deleted_at) FROM tombstones") > 0)
        // The product and the client were only rewritten, never deleted.
        assertNotNull(db.productDao().getProductById(product))
        assertNotNull(db.clientDao().getClientById(client))
    }

    private fun tombstones(): Set<Pair<String, String>> =
        sql.query("SELECT table_name, row_uuid FROM tombstones").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
        }

    private fun pin(table: String, id: Int, value: Long) {
        sql.execSQL("UPDATE `$table` SET updated_at = $value WHERE id = $id")
    }

    private fun SupportSQLiteDatabase.long(query: String): Long =
        query(query).use { assertTrue(query, it.moveToFirst()); it.getLong(0) }

    private fun SupportSQLiteDatabase.text(query: String): String =
        query(query).use { assertTrue(query, it.moveToFirst()); it.getString(0) }

    private fun Map<String, Any>.newId(): Int = (this["id"] as Number).toInt()

    private companion object {
        const val OLD = 1_000L
    }
}
