package com.distrigo.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.local.database.withDeviceIdentity
import com.distrigo.app.data.local.entity.MAX_BARCODES_PER_PRODUCT
import com.distrigo.app.data.trash.TrashKind
import com.distrigo.app.data.trash.TrashOutcome
import com.distrigo.app.data.trash.TrashRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A product's several barcodes: the list, its primary mirrored on `products.barcode`, and one owner per code. */
@RunWith(AndroidJUnit4::class)
class ProductBarcodesTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var repo: ProductRepository

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .withChangeTracking()
            .withDeviceIdentity { "7e57de71-0000-4000-8000-000000000053" }
            .build()
        repo = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
    }

    @After
    fun close() = db.close()

    private fun fields(name: String, vararg codes: String) =
        mapOf("name" to name, "barcodes" to codes.toList(), "selling_price" to 100.0, "purchase_price" to 80.0)

    private fun add(name: String, vararg codes: String): Int = runBlocking {
        (repo.addProduct(fields(name, *codes))["id"] as Number).toInt()
    }

    private fun codes(id: Int): List<String> = runBlocking { db.productBarcodeDao().getForProduct(id).map { it.code } }
    private fun mirror(id: Int): String? = runBlocking { db.productDao().getProductById(id)!!.barcode }

    private fun refused(expected: String, block: () -> Unit) {
        try {
            block()
            fail("should have been refused: $expected")
        } catch (e: IllegalStateException) {
            assertTrue("« ${e.message} » should mention « $expected »", e.message!!.contains(expected))
        }
    }

    @Test
    fun theFirstCodeIsThePrimaryAndIsMirrored() {
        val milk = add("Lait Candia 1L", " 111 ", "222", "111", "")
        // Trimmed, blanks and repeats dropped, order kept.
        assertEquals(listOf("111", "222"), codes(milk))
        assertEquals("111", mirror(milk))
        val product = runBlocking { repo.observeProducts().first() }.single()
        assertEquals(listOf("111", "222"), product.barcodes)
    }

    @Test
    fun anEditReplacesTheListAndMovesThePrimary() {
        val milk = add("Lait Candia 1L", "111", "222", "333")
        val kept = runBlocking { db.productBarcodeDao().getForProduct(milk) }.first { it.code == "333" }
        runBlocking { repo.updateProduct(milk, mapOf("barcodes" to listOf("333", "111", "444"))) }
        assertEquals(listOf("333", "111", "444"), codes(milk))
        assertEquals("333", mirror(milk))
        // A code the product keeps keeps its row, and so its uuid.
        assertEquals(kept.uuid, runBlocking { db.productBarcodeDao().getForProduct(milk) }.first { it.code == "333" }.uuid)
        // The removed code left a tombstone.
        val tombstones = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM tombstones WHERE table_name = 'product_barcodes'")
            .use { it.moveToFirst(); it.getInt(0) }
        assertEquals(1, tombstones)
    }

    @Test
    fun anEditWithoutCodesLeavesThemAlone() {
        val milk = add("Lait Candia 1L", "111", "222")
        runBlocking { repo.updateProduct(milk, mapOf("selling_price" to 130.0)) }
        assertEquals(listOf("111", "222"), codes(milk))
    }

    @Test
    fun aSingleBarcodeBecomesThePrimaryAndKeepsTheOthers() {
        val milk = add("Lait Candia 1L", "111", "222")
        runBlocking { repo.updateProduct(milk, mapOf("barcode" to "222")) }
        assertEquals(listOf("222", "111"), codes(milk))
        runBlocking { repo.updateProduct(milk, mapOf("barcode" to "999")) }
        assertEquals(listOf("999", "222", "111"), codes(milk))
        assertEquals("999", mirror(milk))
    }

    @Test
    fun aCodeBelongsToOneLiveProduct() {
        val milk = add("Lait Candia 1L", "111", "222")
        refused("Le code-barres 222 est déjà enregistré") { runBlocking { repo.addProduct(fields("Selecto", "999", "222")) } }
        val soda = add("Selecto", "999")
        refused("Le code-barres 111") { runBlocking { repo.updateProduct(soda, mapOf("barcodes" to listOf("999", "111"))) } }
        assertEquals(ProductDuplicate.BARCODE, runBlocking { repo.duplicateOf("Autre", listOf("x", "222"), -1) })
        assertEquals("222", runBlocking { repo.takenBarcode(listOf("x", "222"), -1) })
        // Its own codes are not taken from itself.
        assertEquals(null, runBlocking { repo.takenBarcode(listOf("111", "222"), milk) })
        // A product in the bin frees its codes, and cannot come back while another has one.
        runBlocking { repo.deleteProduct(milk) }
        add("Lait Lahda 1L", "222")
        val outcome = TrashRepository(db).restore(TrashKind.PRODUCTS, milk.toLong())
        assertEquals(TrashOutcome.Refused("Le code-barres 222 est déjà utilisé par un produit actif."), outcome)
    }

    @Test
    fun thirtyCodesAtMost() {
        val codes = (1..MAX_BARCODES_PER_PRODUCT).map { "c$it" }.toTypedArray()
        val milk = add("Lait Candia 1L", *codes)
        assertEquals(MAX_BARCODES_PER_PRODUCT, codes(milk).size)
        refused(TOO_MANY_BARCODES) { runBlocking { repo.updateProduct(milk, mapOf("barcodes" to codes.toList() + "c31")) } }
        assertEquals(MAX_BARCODES_PER_PRODUCT, codes(milk).size)
    }
}
