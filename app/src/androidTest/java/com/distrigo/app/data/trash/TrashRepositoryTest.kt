package com.distrigo.app.data.trash

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.local.database.withDeviceIdentity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** The bin, on an in-memory database with the app's triggers, so deleting, restoring and tombstones behave as in the app. */
@RunWith(AndroidJUnit4::class)
class TrashRepositoryTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var trash: TrashRepository

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .withChangeTracking()
            .withDeviceIdentity { "7e57de71-0000-4000-8000-000000000012" }
            .build()
        sql = db.openHelper.writableDatabase
        trash = TrashRepository(db)
    }

    @After
    fun close() {
        db.close()
    }

    private var clock = 1_789_600_000_000L
    private fun uuid() = UUID.randomUUID().toString()
    private fun exec(statement: String, vararg args: Any?) = sql.execSQL(statement, args)
    private fun long(query: String, vararg args: Any?): Long = sql.query(query, args).use { it.moveToFirst(); it.getLong(0) }
    private fun text(query: String, vararg args: Any?): String? = sql.query(query, args).use { if (it.moveToFirst()) it.getString(0) else null }

    /** Moves a row to the bin as the app's delete does, each a moment after the last. */
    private fun bin(table: String, id: Long) {
        clock += 1_000
        exec("UPDATE $table SET deleted_at = ? WHERE id = ?", clock, id)
    }

    private fun product(id: Long, name: String, barcode: String? = null) = exec(
        "INSERT INTO products (id, name, barcode, selling_price, purchase_price, stock, min_stock, unit_type, packages, pack_size, has_expiry, camion_stock, category_name, uuid) " +
            "VALUES (?, ?, ?, 120, 100, 0, 0, 'pièce', 0, 1, 0, 0, 'Laitiers', ?)", id, name, barcode, uuid()
    )

    private fun client(id: Long, name: String, phone: String? = null) =
        exec("INSERT INTO clients (id, name, phone, balance, customer_type, commune_name, uuid) VALUES (?, ?, ?, 0, 'retail', 'Souk Ahras', ?)", id, name, phone, uuid())

    private fun category(id: Long, name: String) = exec("INSERT INTO categories (id, name, sort_order, uuid) VALUES (?, ?, 0, ?)", id, name, uuid())

    private fun sousCategorie(id: Long, categoryId: Long, name: String) =
        exec("INSERT INTO sous_categories (id, category_id, name, sort_order, uuid) VALUES (?, ?, ?, 0, ?)", id, categoryId, name, uuid())

    private fun deletedAt(table: String, id: Long): Long? =
        sql.query("SELECT deleted_at FROM $table WHERE id = ?", arrayOf(id)).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }

    @Test
    fun theBinListsWhatWasDeletedNewestFirstWithWhatTellsItemsApart() {
        product(1, "Lait Candia 1L", "0613000000017")
        product(2, "Yaourt Soummam", "0613000000024")
        product(3, "Fromage")
        client(1, "Épicerie El Amel", "0555123456")
        bin("products", 1)
        bin("products", 2)
        bin("clients", 1)

        val products = trash.items(TrashKind.PRODUCTS)
        assertEquals(listOf("Yaourt Soummam", "Lait Candia 1L"), products.map { it.name })
        assertEquals("0613000000024 · Laitiers", products.first().detail)
        assertEquals("0555123456 · Souk Ahras", trash.items(TrashKind.CLIENTS).single().detail)
        assertEquals(2, trash.counts().getValue(TrashKind.PRODUCTS))
        assertEquals(1, trash.counts().getValue(TrashKind.CLIENTS))
        assertEquals(0, trash.counts().getValue(TrashKind.MARQUES))
    }

    @Test
    fun aRestoredItemIsBackAndItsChangeIsTracked() {
        client(1, "Épicerie El Amel")
        bin("clients", 1)
        val version = long("SELECT version FROM clients WHERE id = 1")

        assertEquals(TrashOutcome.Done, trash.restore(TrashKind.CLIENTS, 1))

        assertNull(deletedAt("clients", 1))
        assertTrue("the change-tracking triggers record the restore", long("SELECT version FROM clients WHERE id = 1") > version)
        assertEquals(0, trash.items(TrashKind.CLIENTS).size)
    }

    @Test
    fun clientsAndSuppliersAlwaysComeBackEvenUnderAnActiveOnesName() {
        client(1, "Épicerie El Amel")
        client(2, "Épicerie El Amel")
        bin("clients", 1)
        assertEquals(TrashOutcome.Done, trash.restore(TrashKind.CLIENTS, 1))
    }

    /** The product form's rules: no two active products share a name, whatever its case, or a barcode. */
    @Test
    fun aProductDoesNotComeBackUnderAnActiveProductsNameOrBarcode() {
        product(1, "Lait Candia 1L", "0613000000017")
        bin("products", 1)
        product(2, "LAIT CANDIA 1L ", "0000000000001")
        val byName = trash.restore(TrashKind.PRODUCTS, 1)
        assertTrue("$byName", byName is TrashOutcome.Refused && "s'appelle déjà" in byName.reason)
        assertTrue("nothing changed", deletedAt("products", 1) != null)

        exec("UPDATE products SET name = 'Lait Candia 1L (nouveau)' WHERE id = 2")
        product(3, "Autre lait", "0613000000017")
        val byBarcode = trash.restore(TrashKind.PRODUCTS, 1)
        assertTrue("$byBarcode", byBarcode is TrashOutcome.Refused && "0613000000017" in byBarcode.reason)

        bin("products", 3)
        assertEquals("once the other is gone too, it can come back", TrashOutcome.Done, trash.restore(TrashKind.PRODUCTS, 1))
    }

    @Test
    fun aSousCategorieWaitsForItsCategoryAndKeepsItsNameUniqueThere() {
        category(1, "Laitiers")
        sousCategorie(10, 1, "Yaourts")
        bin("sous_categories", 10)
        bin("categories", 1)

        val waiting = trash.restore(TrashKind.SOUS_CATEGORIES, 10)
        assertTrue("$waiting", waiting is TrashOutcome.Refused && "d'abord" in waiting.reason)

        assertEquals(TrashOutcome.Done, trash.restore(TrashKind.CATEGORIES, 1))
        sousCategorie(11, 1, "yaourts")
        val taken = trash.restore(TrashKind.SOUS_CATEGORIES, 10)
        assertTrue("$taken", taken is TrashOutcome.Refused && "existe déjà" in taken.reason)
    }

    @Test
    fun aCategoryDoesNotComeBackUnderAnActiveOnesName() {
        category(1, "Laitiers")
        bin("categories", 1)
        category(2, "laitiers")
        assertTrue(trash.restore(TrashKind.CATEGORIES, 1) is TrashOutcome.Refused)
    }

    /** An unused item can go for good; the tombstone trigger records it, so a future sync will know. */
    @Test
    fun anUnusedItemIsDeletedForGoodAndLeavesATombstone() {
        client(1, "Client jamais servi")
        val uuid = text("SELECT uuid FROM clients WHERE id = 1")
        bin("clients", 1)

        assertEquals(emptyList<Usage>(), trash.usages(TrashKind.CLIENTS, 1))
        assertEquals(TrashOutcome.Done, trash.deletePermanently(TrashKind.CLIENTS, 1))

        assertEquals(0, long("SELECT COUNT(*) FROM clients WHERE id = 1"))
        assertEquals(1, long("SELECT COUNT(*) FROM tombstones WHERE table_name = 'clients' AND row_uuid = ?", uuid))
    }

    @Test
    fun aUsedItemIsRefusedWithWhatUsesIt() {
        client(1, "Épicerie El Amel")
        exec("INSERT INTO ventes (client_id, client_name, source, total, montant_paye, status, created_at, uuid) VALUES (1, 'Épicerie El Amel', 'depot', 100, 0, 'pending', '2026-09-17T10:00:00Z', ?)", uuid())
        exec("INSERT INTO client_payments (client_id, amount, created_at, uuid) VALUES (1, 50, '2026-09-17T11:00:00Z', ?)", uuid())
        exec("INSERT INTO client_payments (client_id, amount, created_at, uuid) VALUES (1, 20, '2026-09-17T12:00:00Z', ?)", uuid())
        bin("clients", 1)

        assertEquals(listOf(Usage("vente", "ventes", 1), Usage("paiement", "paiements", 2)), trash.usages(TrashKind.CLIENTS, 1))
        val refused = trash.deletePermanently(TrashKind.CLIENTS, 1)
        assertEquals(TrashOutcome.Refused("Impossible de le supprimer définitivement : il est utilisé par 1 vente et 2 paiements."), refused)
        assertEquals("nothing changed", 1, long("SELECT COUNT(*) FROM clients WHERE id = 1"))
    }

    @Test
    fun aProductUsedOnlyByADraftIsStillUsed() {
        product(1, "Lait Candia 1L")
        product(12, "Autre")
        exec(
            "INSERT INTO vente_drafts (client_id, items_json, note, montant_paye, user_name, item_count, total, last_step, created_at, updated_at) " +
                "VALUES (NULL, ?, '', '0', 'Ayoub', 1, 120, 'items', '2026-09-17T10:00:00Z', '2026-09-17T10:00:00Z')",
            """[{"product_id":1,"product_name":"Lait Candia 1L","quantity":1.0}]"""
        )
        bin("products", 1)
        bin("products", 12)

        assertEquals(listOf(Usage("brouillon de vente", "brouillons de vente", 1)), trash.usages(TrashKind.PRODUCTS, 1))
        assertEquals("product 12 is not product 1 of the draft", emptyList<Usage>(), trash.usages(TrashKind.PRODUCTS, 12))
    }

    @Test
    fun aProductGoesWithItsOwnPhotosAndPriceHistoryWhenNothingElseUsesIt() {
        product(1, "Lait Candia 1L")
        exec("INSERT INTO product_images (product_id, image_ref, position, created_at, uuid) VALUES (1, 'img:abc', 0, '2026-09-17T08:00:00Z', ?)", uuid())
        exec("INSERT INTO price_history (product_id, unit_cost, date, created_at, supplier_name, uuid) VALUES (1, 100, '2026-09-17', '2026-09-17T08:00:00Z', 'Laiterie Soummam', ?)", uuid())
        bin("products", 1)

        assertEquals(TrashOutcome.Done, trash.deletePermanently(TrashKind.PRODUCTS, 1))
        assertEquals(0, long("SELECT COUNT(*) FROM product_images WHERE product_id = 1"))
        assertEquals(0, long("SELECT COUNT(*) FROM price_history WHERE product_id = 1"))
    }

    @Test
    fun aProductWithStockMovementsIsUsed() {
        product(1, "Lait Candia 1L")
        exec(
            "INSERT INTO stock_movements (product_id, product_name, type, direction, quantity, emplacement, source_label, source_type, source_id, total_value, created_at, uuid) " +
                "VALUES (1, 'Lait Candia 1L', 'achat', 'entree', 5, 'depot', 'Bon #1', 'purchase_order', 1, 500, '2026-09-17T08:00:00Z', ?)", uuid()
        )
        bin("products", 1)
        assertEquals(listOf(Usage("mouvement de stock", "mouvements de stock", 1)), trash.usages(TrashKind.PRODUCTS, 1))
        assertTrue(trash.deletePermanently(TrashKind.PRODUCTS, 1) is TrashOutcome.Refused)
    }

    /** Children in the bin count: restoring one later would leave it pointing at nothing. */
    @Test
    fun aCategoryWithASousCategorieInTheBinIsStillUsed() {
        category(1, "Laitiers")
        sousCategorie(10, 1, "Yaourts")
        bin("sous_categories", 10)
        bin("categories", 1)
        assertEquals(listOf(Usage("sous-catégorie", "sous-catégories", 1)), trash.usages(TrashKind.CATEGORIES, 1))
        assertTrue(trash.deletePermanently(TrashKind.CATEGORIES, 1) is TrashOutcome.Refused)
    }

    /** The bin only ever acts on what is in it: an active item is never restored or deleted through it. */
    @Test
    fun anActiveItemIsNeverTouched() {
        client(1, "Épicerie El Amel")
        assertEquals(TrashOutcome.Refused("Cet élément n'est plus dans la corbeille."), trash.deletePermanently(TrashKind.CLIENTS, 1))
        assertEquals(TrashOutcome.Refused("Cet élément n'est plus dans la corbeille."), trash.restore(TrashKind.CLIENTS, 1))
        assertEquals(1, long("SELECT COUNT(*) FROM clients WHERE id = 1 AND deleted_at IS NULL"))
    }

    @Test
    fun usagesReadAsASentence() {
        assertEquals("1 vente", TrashRepository.words(listOf(Usage("vente", "ventes", 1))))
        assertEquals(
            "3 ventes, 1 paiement et 2 tournées",
            TrashRepository.words(listOf(Usage("vente", "ventes", 3), Usage("paiement", "paiements", 1), Usage("tournée", "tournées", 2)))
        )
    }
}
