package com.distrigo.app.data.importer

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.backup.BackupFailedException
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.local.database.withDeviceIdentity
import com.distrigo.app.data.local.entity.ClientEntity
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Applying an import on an in-memory database with the app's triggers: what gets written, and what stops it. */
@RunWith(AndroidJUnit4::class)
class ImportRepositoryTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var products: ProductRepository
    private lateinit var importer: ImportRepository
    private val safetyFile = File(context.cacheDir, "avant-import-test.distrigo")
    private var safetyFails = false

    private val geo = object : ImportGeo {
        private val communes = mapOf("Souk Ahras" to listOf("Souk Ahras", "Sedrata"))
        override fun wilaya(name: String) = communes.keys.firstOrNull { it.equals(name.trim(), ignoreCase = true) }
        override fun commune(wilaya: String, name: String) = communes[wilaya]?.firstOrNull { it.equals(name.trim(), ignoreCase = true) }
    }

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .withChangeTracking()
            .withDeviceIdentity { "7e57de71-0000-4000-8000-000000000013" }
            .build()
        products = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
        importer = ImportRepository(db, products, geo, ImportSafety {
            if (safetyFails) throw BackupFailedException(BackupFailedException.Reason.entries.first(), "no space")
            safetyFile.apply { writeText("safety") }
        })
    }

    @After
    fun close() {
        db.close()
        safetyFile.delete()
    }

    // ── Sheets ──

    private fun sheet(name: String, header: List<String>, vararg rows: List<Any?>): XlsxSheet {
        val all = mutableListOf(XlsxRow(1, header.withIndex().associate { (i, h) -> i to XlsxValue.Text(h) }))
        rows.forEachIndexed { index, cells ->
            val map = cells.withIndex().mapNotNull { (i, v) ->
                when (v) {
                    null -> null
                    is XlsxValue -> i to v
                    is String -> i to XlsxValue.Text(v)
                    is Number -> i to XlsxValue.Number(v.toString())
                    else -> error("cell $v")
                }
            }.toMap()
            if (map.isNotEmpty()) all += XlsxRow(index + 2, map)
        }
        return XlsxSheet(name, all)
    }

    private val productHeader = listOf("Nom", "Code-barres", "Catégorie", "Sous-catégorie", "Marque", "Fournisseur", "Unité", "Unités par colis", "Prix d'achat (DA)", "Prix de vente (DA)", "Stock dépôt", "Stock minimum", "Date de péremption")
    private val clientHeader = listOf("Nom", "Téléphone", "Type", "Secteur", "Wilaya", "Commune", "Adresse", "Note")

    private fun productsBook(vararg rows: List<Any?>) = XlsxWorkbook(listOf(sheet("Produits", productHeader, *rows)))
    private fun clientsBook(vararg rows: List<Any?>) = XlsxWorkbook(listOf(sheet("Clients", clientHeader, *rows)))

    private fun import(workbook: XlsxWorkbook): ImportResult = runBlocking { importer.apply(workbook, importer.plan(workbook)) }

    private fun seedProduct(name: String, barcode: String, selling: Double, purchase: Double, stock: Double = 0.0): Int = runBlocking {
        (products.addProduct(mapOf("name" to name, "barcode" to barcode, "selling_price" to selling, "purchase_price" to purchase, "stock" to stock, "unit_type" to "carton"))["id"] as Number).toInt()
    }

    private fun seedClient(name: String, phone: String? = null, commune: String? = null, wilaya: String? = null): Int = runBlocking {
        db.clientDao().insertClient(
            ClientEntity(name = name, phone = phone, wilaya_name = wilaya, commune_name = commune, secteur_id = null, secteur_name = null, address = null, note = null, image_uri = null, latitude = null, longitude = null)
        ).toInt()
    }

    // ── Products ──

    @Test
    fun createsProductsWithTheirLookupsStockAndBarcode() {
        val result = import(productsBook(
            listOf("Eau Ifri 1.5L", "6130000000111", "Eaux", "Plates", "Ifri", "Ifri SPA", "pièce", 6, 40.0, 55.0, 24, 5, XlsxValue.Date(LocalDate.of(2027, 6, 30).atStartOfDay())),
            listOf("Sucre vrac", null, null, null, null, null, null, null, null, 150.0),
        ))
        assertEquals(2, result.created)
        assertEquals(0, result.updated)
        assertEquals(safetyFile, result.safetyBackup)

        val all = runBlocking { db.productDao().getAllProducts() }
        val eau = all.single { it.name == "Eau Ifri 1.5L" }
        assertEquals("6130000000111", eau.barcode)
        assertEquals("Eaux", eau.category_name)
        assertEquals("Plates", eau.sous_categorie_name)
        assertEquals("Ifri", eau.marque_name)
        assertEquals("Ifri SPA", eau.supplier_name)
        assertNotNull(eau.supplier_id)
        assertEquals("pièce", eau.unit_type)
        assertEquals(6, eau.pack_size)
        assertEquals(40.0, eau.purchase_price, 0.0)
        assertEquals(55.0, eau.selling_price, 0.0)
        assertEquals(5, eau.min_stock)
        assertEquals(1, eau.has_expiry)
        assertEquals("2027-06-30", eau.expiry_date)
        // Stock comes from an opening movement, as the form makes it.
        assertEquals(24.0, eau.stock, 0.0)
        val opening = runBlocking { db.stockMovementDao().getMovementsForProduct(eau.id) }.single()
        assertEquals("Stock initial", opening.source_label)
        assertEquals(24.0, opening.quantity, 0.0)

        val sucre = all.single { it.name == "Sucre vrac" }
        // No barcode in the file: one made from the id, as the form's button does.
        assertEquals(sucre.id.toString().padStart(13, '0'), sucre.barcode)
        assertEquals(0.0, sucre.stock, 0.0)
        assertEquals("carton", sucre.unit_type)

        // The lookups exist once each, in order after what was there.
        assertEquals(listOf("Eaux"), runBlocking { db.categoryDao().getAllCategories() }.map { it.name })
        assertEquals(listOf("Plates"), runBlocking { db.sousCategorieDao().getAllSousCategories() }.map { it.name })
        assertEquals(listOf("Ifri"), runBlocking { db.marqueDao().getAllMarques() }.map { it.name })
        assertEquals(listOf("Ifri SPA"), runBlocking { db.supplierDao().getAllSuppliers() }.map { it.name })
    }

    @Test
    fun updatesOnlyWhatTheFileGives() {
        val id = seedProduct("Lait Candia 1L", "6130000000123", selling = 120.0, purchase = 100.0, stock = 7.0)
        val result = import(productsBook(listOf("lait candia 1l", null, null, null, null, null, null, null, null, 130.0, 99, null)))
        assertEquals(0, result.created)
        assertEquals(1, result.updated)

        val milk = runBlocking { db.productDao().getProductById(id) }!!
        assertEquals(130.0, milk.selling_price, 0.0)
        assertEquals(100.0, milk.purchase_price, 0.0)
        assertEquals("6130000000123", milk.barcode)
        assertEquals("Lait Candia 1L", milk.name)
        // The stock column is not applied to an existing product.
        assertEquals(7.0, milk.stock, 0.0)
        assertEquals(1, runBlocking { db.stockMovementDao().getMovementsForProduct(id) }.size)
    }

    @Test
    fun anImportOfAnExportChangesNothing() {
        seedProduct("Lait Candia 1L", "6130000000123", selling = 120.0, purchase = 100.0)
        val workbook = productsBook(listOf("Lait Candia 1L", "6130000000123", null, null, null, null, "carton", 0, 100.0, 120.0, 0, 10, null))
        val plan = runBlocking { importer.plan(workbook) }
        assertEquals(1, plan.unchanged)
        val result = runBlocking { importer.apply(workbook, plan) }
        assertEquals(0, result.created + result.updated)
    }

    // ── Clients ──

    @Test
    fun createsAndUpdatesClients() {
        val amine = seedClient("Épicerie Amine", phone = "0555123456", commune = "Souk Ahras", wilaya = "Souk Ahras")
        val result = import(clientsBook(
            listOf("épicerie amine", "0555000000", "Gros", "Centre", null, null, "Rue 1", null),
            listOf("Superette Nadir", "0666", "Société", "Gare", "souk ahras", "sedrata", null, "Ouvre à 8h"),
        ))
        assertEquals(1, result.created)
        assertEquals(1, result.updated)

        val updated = runBlocking { db.clientDao().getClientById(amine) }!!
        assertEquals("Épicerie Amine", updated.name)
        assertEquals("0555000000", updated.phone)
        assertEquals("wholesale", updated.customer_type)
        assertEquals("Centre", updated.secteur_name)
        assertEquals("Rue 1", updated.address)
        assertEquals("Souk Ahras", updated.commune_name)

        val nadir = runBlocking { db.clientDao().getAllClients() }.single { it.name == "Superette Nadir" }
        assertEquals("business", nadir.customer_type)
        assertEquals("Souk Ahras", nadir.wilaya_name)
        assertEquals("Sedrata", nadir.commune_name)
        assertEquals("Gare", nadir.secteur_name)
        assertEquals("Ouvre à 8h", nadir.note)

        // Two secteurs made, each in its client's commune, and the clients point at them.
        val secteurs = runBlocking { db.secteurDao().getAllSecteurs() }
        assertEquals(mapOf("Centre" to "Souk Ahras", "Gare" to "Sedrata"), secteurs.associate { it.nom to it.commune_name })
        assertEquals(secteurs.single { it.nom == "Centre" }.id, updated.secteur_id)
        assertEquals(secteurs.single { it.nom == "Gare" }.id, nadir.secteur_id)
    }

    // ── What stops an import ──

    @Test
    fun stopsWhenTheDataMovedSinceThePlan() {
        val id = seedProduct("Lait Candia 1L", "6130000000123", selling = 120.0, purchase = 100.0)
        val workbook = productsBook(
            listOf("Lait Candia 1L", null, null, null, null, null, null, null, null, 130.0),
            listOf("Nouveau", null, null, null, null, null, null, null, null, 10.0),
        )
        val plan = runBlocking { importer.plan(workbook) }
        // Someone changes the price in the app meanwhile.
        runBlocking { products.updateProduct(id, mapOf("selling_price" to 125.0)) }
        try {
            runBlocking { importer.apply(workbook, plan) }
            fail("should stop")
        } catch (e: ImportStaleException) {
            // Expected.
        }
        // Nothing of the plan was written: no new product, the price as the app left it.
        assertEquals(1, runBlocking { db.productDao().getAllProducts() }.size)
        assertEquals(125.0, runBlocking { db.productDao().getProductById(id) }!!.selling_price, 0.0)
    }

    @Test
    fun writesNothingWhenTheSafetyBackupFails() {
        seedProduct("Lait Candia 1L", "6130000000123", selling = 120.0, purchase = 100.0)
        val workbook = productsBook(listOf("Nouveau", null, "Eaux", null, null, null, null, null, null, 10.0))
        safetyFails = true
        try {
            import(workbook)
            fail("should fail")
        } catch (e: BackupFailedException) {
            // Expected.
        }
        assertEquals(1, runBlocking { db.productDao().getAllProducts() }.size)
        assertTrue(runBlocking { db.categoryDao().getAllCategories() }.isEmpty())
    }

    @Test
    fun skipsRefusedRowsAndAppliesTheRest() {
        seedProduct("Lait Candia 1L", "6130000000123", selling = 120.0, purchase = 100.0)
        val workbook = productsBook(
            listOf("Nouveau", "6130000000123", null, null, null, null, null, null, null, 10.0),
            listOf("Bon", null, null, null, null, null, "bouteille", null, null, 10.0),
            listOf("Autre", null, null, null, null, null, null, null, null, 20.0),
        )
        val plan = runBlocking { importer.plan(workbook) }
        assertEquals(1, plan.refused)
        val result = runBlocking { importer.apply(workbook, plan) }
        assertEquals(1, result.created)
        assertEquals(1, result.updated)
        val names = runBlocking { db.productDao().getAllProducts() }.map { it.name }.toSet()
        assertEquals(setOf("Nouveau", "Autre"), names)
        assertNull(names.firstOrNull { it == "Bon" })
    }
}
