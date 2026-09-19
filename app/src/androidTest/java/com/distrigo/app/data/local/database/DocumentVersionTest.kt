package com.distrigo.app.data.local.database

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.repository.InventoryRepository
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A document's `version` and `updated_at` move whenever any part of it changes: its own row, its
 * lines, its stock movements — and never for a cache refresh.
 *
 * Most cases go through the repositories, the way the app changes documents. A few drive the
 * triggers with SQL, where no repository path isolates the case.
 *
 * In memory, so nothing touches the app's database on the device.
 */
@RunWith(AndroidJUnit4::class)
class DocumentVersionTest {

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

    /** Standalone rows carry a version, lines don't, and every line's parent is one that does. */
    @Test
    fun versionsSitOnStandaloneRowsOnly() {
        val tracked = UpdatedAtTriggers.trackedTables(sql)
        val versioned = tracked.filter { "version" in UpdatedAtTriggers.columns(sql, it) }
        val children = DocumentTriggers.PARENTS.keys - "pertes"   // pertes stand alone too

        assertEquals(25, versioned.size)
        assertEquals(12, children.size)
        assertEquals(tracked.toSet(), versioned.toSet() + children)
        assertTrue(children.none { it in versioned })
        for (link in DocumentTriggers.PARENTS.values.flatten()) {
            assertTrue(link.parent, link.parent in versioned)
        }
    }

    @Test
    fun everyChildTableHasItsParentTriggers() {
        for ((child, links) in DocumentTriggers.PARENTS) {
            val triggers = DocumentTriggers.triggers(child, links, UpdatedAtTriggers.comparedColumns(sql, child))
            assertEquals(3, triggers.size)
            for ((name, expected) in triggers) {
                assertEquals(name, expected, storedTriggerSql(sql, name))
            }
        }
    }

    /** A row's own edit moves its version by exactly one; saving it unchanged or moving its balance doesn't. */
    @Test
    fun aRowsOwnEditsMoveItsVersion() = runBlocking {
        val client = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()
        assertEquals(1L, version("clients", client))

        repository.updateClient(client, mapOf("name" to "Épicerie El Amel 2"))
        assertEquals(2L, version("clients", client))

        repository.updateClient(client, mapOf("name" to "Épicerie El Amel 2"))
        repository.addClientPayment(client, 300.0, null)
        assertEquals(2L, version("clients", client))
    }

    /**
     * The case the `ventes` row alone could not see: 10 × 110 becomes 11 × 100. Total, note and
     * payment stay the same, so the row's own columns never change — only its line and movement do.
     */
    @Test
    fun editingOnlyTheLinesOfAVenteVersionsTheVente() = runBlocking {
        val product = repository.addProduct(mapOf("name" to "Lait Candia 1L", "selling_price" to 110.0, "stock" to 40.0)).newId()
        val client = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()
        repository.createVente(
            clientId = client, tourneeId = null, source = "depot",
            items = listOf(mapOf("product_id" to product, "quantity" to 10.0, "unit_price" to 110.0)),
            note = null, montantPaye = 500.0
        )
        val vente = db.venteDao().getVentesWithDetails(client).single().vente
        pin("ventes", vente.id)
        val before = version("ventes", vente.id)

        repository.updateVente(
            id = vente.id, clientId = client,
            items = listOf(mapOf("product_id" to product, "quantity" to 11.0, "unit_price" to 100.0)),
            note = null, montantPaye = 500.0
        )

        val after = db.venteDao().getVenteById(vente.id)!!
        assertEquals(vente.total, after.total, 0.0)
        assertTrue("version $before -> ${after.version}", after.version > before)
        assertTrue(after.updated_at > OLD)
    }

    @Test
    fun receivingAndReopeningABonVersionIt() = runBlocking {
        val supplier = repository.addSupplier(mapOf("name" to "Laiterie Soummam")).newId()
        val product = repository.addProduct(mapOf("name" to "Lait Candia 1L", "selling_price" to 110.0)).newId()
        repository.createPurchaseOrder(
            mapOf(
                "supplier_id" to supplier, "montant_paye" to 0.0,
                "items" to listOf(mapOf("product_id" to product, "quantity" to 24.0, "unit_cost" to 95.0)),
            )
        )
        val order = db.purchaseDao().getAllOrders().single().id

        val created = version("purchase_orders", order)
        repository.receivePurchaseOrder(order)
        val received = version("purchase_orders", order)
        repository.reopenPurchaseOrder(order)
        val reopened = version("purchase_orders", order)

        assertTrue("$created -> $received -> $reopened", created < received && received < reopened)
        assertEquals(0L, sql.long("SELECT COUNT(*) FROM stock_movements WHERE source_type = 'purchase_order'"))
    }

    /** An inventory movement names a line, not the session; the session is still what moves. */
    @Test
    fun inventoryScansVersionTheSession() = runBlocking {
        val inventory = InventoryRepository(db)
        val product = repository.addProduct(mapOf("name" to "Lait Candia 1L", "selling_price" to 110.0, "stock" to 40.0)).newId()
        val session = inventory.getOrCreateActiveSession().id

        val versions = mutableListOf(version("inventory_sessions", session))
        inventory.recordScan(session, product, 38.0)
        versions += version("inventory_sessions", session)
        val item = db.inventoryDao().getItemsForSession(session).single().id
        inventory.updateScan(item, 35.0)
        versions += version("inventory_sessions", session)
        inventory.deleteScan(item)
        versions += version("inventory_sessions", session)

        assertEquals(versions.sorted().distinct(), versions)
    }

    @Test
    fun tourneeClientsVersionTheTournee() = runBlocking {
        repository.createTournee("Souk Ahras centre", "Souk Ahras", "Souk Ahras", null)
        val tournee = db.tourneeDao().getOpenTournee()!!.id
        val client = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()

        val versions = mutableListOf(version("tournees", tournee))
        repository.addClientsToTournee(tournee, listOf(client))
        versions += version("tournees", tournee)
        repository.markTourneeClientVisited(tournee, client)
        versions += version("tournees", tournee)
        repository.removeClientFromTournee(tournee, client)
        versions += version("tournees", tournee)

        assertEquals(versions.sorted().distinct(), versions)
    }

    /** Photos version their product, tiers their policy, and a perte recorded by a return that return. */
    @Test
    fun otherLinesVersionTheirParents() = runBlocking {
        val product = repository.addProduct(mapOf("name" to "Lait Candia 1L", "selling_price" to 110.0)).newId()
        sql.execSQL(
            "INSERT INTO product_images (product_id, image_ref, position, created_at, uuid) " +
                "VALUES ($product, 'img:${"a".repeat(64)}', 0, '2026-09-16T10:00:00Z', 'u-image')"
        )
        assertEquals(2L, version("products", product))

        sql.execSQL(
            "INSERT INTO target_policies (id, nom, incentive_type, period_type, calculation_source, target_value, " +
                "created_at, uuid) VALUES (1, 'Politique Q4', 'FIXED_RATE', 'MONTHLY', 'INVOICED_SALES', 100000, " +
                "'2026-09-16T10:00:00Z', 'u-policy')"
        )
        sql.execSQL(
            "INSERT INTO policy_tiers (policy_id, min_threshold, tier_order, uuid) VALUES (1, 0, 1, 'u-tier')"
        )
        assertEquals(2L, version("target_policies", 1))

        sql.execSQL(
            "INSERT INTO retour_client (id, client_id, date, total, created_at, uuid) " +
                "VALUES (1, 1, '2026-09-16', 220.0, '2026-09-16T10:00:00Z', 'u-retour')"
        )
        sql.execSQL(
            "INSERT INTO pertes (type_id, type_name, product_id, product_name, quantity, unit, source, " +
                "purchase_price_snapshot, valeur_totale, date_time, created_at, source_type, source_id, uuid) " +
                "VALUES (1, 'Casse', $product, 'Lait Candia 1L', 2.0, 'pièce', 'depot', 95.0, 190.0, " +
                "'2026-09-16T10:00:00Z', '2026-09-16T10:00:00Z', 'retour_client', 1, 'u-perte')"
        )
        assertEquals(2L, version("retour_client", 1))
        // A perte of its own belongs to nobody
        sql.execSQL(
            "INSERT INTO pertes (type_id, type_name, product_id, product_name, quantity, unit, source, " +
                "purchase_price_snapshot, valeur_totale, date_time, created_at, uuid) " +
                "VALUES (1, 'Casse', $product, 'Lait Candia 1L', 1.0, 'pièce', 'depot', 95.0, 95.0, " +
                "'2026-09-16T10:00:00Z', '2026-09-16T10:00:00Z', 'u-perte-2')"
        )
        assertEquals(2L, version("retour_client", 1))
    }

    /** A line moved from one document to another changes both. */
    @Test
    fun aMovedLineVersionsBothDocuments() = runBlocking {
        val client = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()
        for (id in 1..2) {
            sql.execSQL(
                "INSERT INTO ventes (id, client_id, source, total, montant_paye, status, created_at, uuid) " +
                    "VALUES ($id, $client, 'depot', 0.0, 0.0, 'delivered', '2026-09-16T10:00:00Z', 'u-vente-$id')"
            )
        }
        sql.execSQL(
            "INSERT INTO stock_movements (product_id, product_name, type, direction, quantity, emplacement, " +
                "source_label, source_type, source_id, total_value, created_at, uuid) " +
                "VALUES (1, 'Lait Candia 1L', 'vente', 'sortie', 1.0, 'depot', 'x', 'vente', 1, 110.0, " +
                "'2026-09-16T10:00:00Z', 'u-movement')"
        )
        assertEquals(2L, version("ventes", 1))
        assertEquals(1L, version("ventes", 2))

        sql.execSQL("UPDATE stock_movements SET source_id = 2 WHERE uuid = 'u-movement'")

        assertEquals(3L, version("ventes", 1))
        assertEquals(2L, version("ventes", 2))
    }

    private fun version(table: String, id: Int): Long = sql.long("SELECT version FROM `$table` WHERE id = $id")

    private fun pin(table: String, id: Int) {
        sql.execSQL("UPDATE `$table` SET updated_at = $OLD WHERE id = $id")
    }

    private fun SupportSQLiteDatabase.long(query: String): Long =
        query(query).use { assertTrue(query, it.moveToFirst()); it.getLong(0) }

    private fun Map<String, Any>.newId(): Int = (this["id"] as Number).toInt()

    private companion object {
        const val OLD = 1_000L
    }
}
