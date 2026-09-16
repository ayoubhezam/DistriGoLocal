package com.distrigo.app.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.repository.RetourClientRepository
import com.distrigo.app.data.repository.RetourFournisseurRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * New ventes, bons and returns are numbered `{type}-{device}-{counter}` as they are created.
 *
 * A named file database, so it can be reopened as another device; not the app's — [TEST_DB] is
 * deleted before and after every test. Documents are created through the repositories.
 */
@RunWith(AndroidJUnit4::class)
class DocumentNumberTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var repository: ProductRepository

    @Before
    fun startClean() {
        assertNotEquals("distrigo", TEST_DB)
        deleteTestDatabase()
    }

    @After
    fun deleteTestDatabase() {
        if (::db.isInitialized && db.isOpen) db.close()
        context.deleteDatabase(TEST_DB)
        File(context.getDatabasePath(TEST_DB).path + ".lck").delete()
    }

    private fun open(device: String) {
        db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .withMigrationPolicy()
            .withChangeTracking()
            .withDeviceIdentity { device }
            .build()
        sql = db.openHelper.writableDatabase
        repository = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
    }

    @Test
    fun eachTypeCountsOnItsOwn() = runBlocking {
        open(DEVICE_A)
        val product = product()
        val client = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()
        val supplier = repository.addSupplier(mapOf("name" to "Laiterie Soummam")).newId()

        sale(client, product)
        sale(client, product)
        repository.createPurchaseOrder(
            mapOf("supplier_id" to supplier, "items" to listOf(mapOf("product_id" to product, "quantity" to 5.0, "unit_cost" to 95.0)))
        )
        RetourClientRepository(db).createRetour(client, null, "2026-09-16", "Client insatisfait", null, listOf(line(product)))
        RetourFournisseurRepository(db).createRetour(supplier, "2026-09-16", "Erreur de commande", null, listOf(line(product)))

        assertEquals(listOf("V-A0F1-000001", "V-A0F1-000002"), numbers("ventes"))
        assertEquals(listOf("BA-A0F1-000001"), numbers("purchase_orders"))
        assertEquals(listOf("RC-A0F1-000001"), numbers("retour_client"))
        assertEquals(listOf("RF-A0F1-000001"), numbers("retour_fournisseur"))
    }

    /** The same database opened by another phone carries on counting, under that phone's code. */
    @Test
    fun anotherDeviceContinuesTheCounterWithItsOwnCode() = runBlocking {
        open(DEVICE_A)
        val product = product()
        val client = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()
        sale(client, product)
        db.close()

        open(DEVICE_B)
        sale(client, product)

        assertEquals(listOf("V-A0F1-000001", "V-B7C2-000002"), numbers("ventes"))
    }

    /** A sale that rolls back takes its number with it: the next one does not skip. */
    @Test
    fun aRolledBackDocumentDoesNotUseANumber() = runBlocking {
        open(DEVICE_A)
        val product = product()
        val client = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()

        // A line for a product that does not exist fails after the vente row is inserted, inside the
        // sale's transaction.
        try {
            repository.createVente(client, null, "depot", listOf(line(product), line(9_999)), null, 0.0)
            error("A sale of an unknown product should fail")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().startsWith("Produit introuvable"))
        }
        sale(client, product)

        assertEquals(listOf("V-A0F1-000001"), numbers("ventes"))
    }

    /** Being numbered is not an edit, and a document that arrives with a number keeps it. */
    @Test
    fun numberingIsNotAnEditAndAGivenNumberIsKept() {
        open(DEVICE_A)
        sql.execSQL(
            "INSERT INTO ventes (client_id, source, total, montant_paye, status, created_at, uuid) " +
                "VALUES (1, 'depot', 0.0, 0.0, 'pending', '2026-09-16T10:00:00Z', 'u-local')"
        )
        sql.execSQL(
            "INSERT INTO ventes (client_id, source, total, montant_paye, status, created_at, uuid, numero) " +
                "VALUES (1, 'depot', 0.0, 0.0, 'pending', '2026-09-16T10:00:00Z', 'u-remote', 'V-B7C2-000040')"
        )

        assertEquals("V-A0F1-000001", sql.text("SELECT numero FROM ventes WHERE uuid = 'u-local'"))
        assertEquals(1L, sql.long("SELECT version FROM ventes WHERE uuid = 'u-local'"))
        assertEquals("V-B7C2-000040", sql.text("SELECT numero FROM ventes WHERE uuid = 'u-remote'"))
        // The given number used no counter.
        assertEquals("1", sql.text("SELECT value FROM app_meta WHERE key = 'numbering.ventes'"))
    }

    /** Opened without an identity, as in-memory test databases are, documents get no number and show #id. */
    @Test
    fun withoutAnIdentityThereIsNoNumber() = runBlocking {
        val memory = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withChangeTracking().build()
        try {
            val repo = ProductRepository(memory.productDao(), memory.categoryDao(), memory.supplierDao(), memory)
            val product = repo.addProduct(mapOf("name" to "Lait Candia 1L", "selling_price" to 110.0, "stock" to 40.0)).newId()
            val client = repo.addClient(mapOf("name" to "Épicerie El Amel")).newId()
            repo.createVente(client, null, "depot", listOf(line(product)), null, 0.0)
            val vente = repo.getVentes(client).single()
            assertEquals(null, vente.numero)
            assertEquals("#${vente.id}", repo.documentLabel("vente", vente.id))
        } finally {
            memory.close()
        }
    }

    @Test
    fun numbersReachTheModelsTheLedgerAndItsSearch() = runBlocking {
        open(DEVICE_A)
        val product = product()
        val client = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()
        sale(client, product)
        val id = sql.long("SELECT id FROM ventes").toInt()

        assertEquals("V-A0F1-000001", repository.getVente(id).numero)
        assertEquals("V-A0F1-000001", repository.getClientLedgerPreview(client, 5).latest.single { it.type == "vente" }.numero)
        assertEquals("V-A0F1-000001", repository.documentLabel("vente", id))
        assertEquals("#$id", repository.documentLabel("perte", id))
        assertEquals(1, repository.countClientLedger(client, com.distrigo.app.data.model.FactureFilter.TOUTES, "A0F1-0000"))
        assertEquals(0, repository.countClientLedger(client, com.distrigo.app.data.model.FactureFilter.TOUTES, "#$id"))
    }

    /** A defective return's perte is labelled with the return's number. */
    @Test
    fun aReturnsPerteNamesItByNumber() = runBlocking {
        open(DEVICE_A)
        val product = product()
        val client = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()
        RetourClientRepository(db).createRetour(client, null, "2026-09-16", "Produit défectueux", null, listOf(line(product)))

        assertEquals("Retour client RC-A0F1-000001", sql.text("SELECT motif FROM pertes"))
    }

    @Test
    fun numberingTriggersAndCountersAreInstalled() {
        open(DEVICE_A)
        for (table in DocumentNumberTriggers.PREFIXES.keys) {
            assertEquals(table, DocumentNumberTriggers.triggerSql(table), storedTriggerSql(sql, DocumentNumberTriggers.triggerName(table)))
            assertEquals(table, "0", sql.text("SELECT value FROM app_meta WHERE key = '${DocumentNumberTriggers.counterKey(table)}'"))
        }
    }

    // ── helpers ──

    private suspend fun product(): Int =
        repository.addProduct(mapOf("name" to "Lait Candia 1L", "selling_price" to 110.0, "purchase_price" to 95.0, "stock" to 40.0)).newId()

    private fun line(product: Int) = mapOf("product_id" to product, "quantity" to 1.0, "unit_price" to 110.0)

    private suspend fun sale(client: Int, product: Int) {
        repository.createVente(client, null, "depot", listOf(line(product)), null, 0.0)
    }

    private fun numbers(table: String): List<String> =
        sql.query("SELECT numero FROM `$table` ORDER BY id").use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    private fun SupportSQLiteDatabase.long(query: String): Long =
        query(query).use { assertTrue(query, it.moveToFirst()); it.getLong(0) }

    private fun SupportSQLiteDatabase.text(query: String): String =
        query(query).use { assertTrue(query, it.moveToFirst()); it.getString(0) }

    private fun Map<String, Any>.newId(): Int = (this["id"] as Number).toInt()

    private companion object {
        const val TEST_DB = "document-number-test"
        const val DEVICE_A = "a0f10000-0000-4000-8000-00000000000a"
        const val DEVICE_B = "b7c20000-0000-4000-8000-00000000000b"
    }
}
