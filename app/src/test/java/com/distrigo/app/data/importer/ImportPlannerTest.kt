package com.distrigo.app.data.importer

import com.distrigo.app.data.local.entity.CategoryEntity
import com.distrigo.app.data.local.entity.ClientEntity
import com.distrigo.app.data.local.entity.MarqueEntity
import com.distrigo.app.data.local.entity.ProductEntity
import com.distrigo.app.data.local.entity.SecteurEntity
import com.distrigo.app.data.local.entity.SousCategorieEntity
import com.distrigo.app.data.local.entity.SupplierEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

/** What the import decides for each row, against a small catalogue. */
class ImportPlannerTest {

    // ── A catalogue ──

    private fun product(
        id: Int, name: String, barcode: String?, selling: Double = 100.0, purchase: Double = 80.0, category: CategoryEntity? = null,
        unit: String = "carton", packSize: Int = 0, minStock: Int = 10, expiry: String? = null, supplier: SupplierEntity? = null,
    ) = ProductEntity(
        id = id, name = name, barcode = barcode, selling_price = selling, purchase_price = purchase, stock = 5.0, min_stock = minStock,
        unit_type = unit, packages = 0, pack_size = packSize, has_expiry = if (expiry != null) 1 else 0, expiry_date = expiry, image_uri = null,
        category_name = category?.name, category_id = category?.id, supplier_name = supplier?.name, supplier_id = supplier?.id,
    )

    private fun client(id: Int, name: String, phone: String? = null, wilaya: String? = null, commune: String? = null, secteur: SecteurEntity? = null, type: String = "retail") =
        ClientEntity(
            id = id, name = name, phone = phone, wilaya_name = wilaya, commune_name = commune, secteur_id = secteur?.id, secteur_name = secteur?.nom,
            address = null, note = null, customer_type = type, image_uri = null, latitude = null, longitude = null,
        )

    private val boissons = CategoryEntity(id = 1, name = "Boissons", sort_order = 0)
    private val laitiers = CategoryEntity(id = 2, name = "Produits laitiers", sort_order = 1)
    private val jus = SousCategorieEntity(id = 1, category_id = 1, name = "Jus", sort_order = 0)
    private val hamoud = MarqueEntity(id = 1, name = "Hamoud Boualem", sort_order = 0)
    private val cevital = SupplierEntity(id = 1, name = "Cevital", phone = null, address = null, note = null, balance = 0.0, latitude = null, longitude = null, wilaya_name = null, commune_name = null)
    private val centre = SecteurEntity(id = 1, nom = "Centre", commune_name = "Souk Ahras", wilaya_name = "Souk Ahras", created_at = "2026-01-01T00:00:00Z")

    private val milk = product(1, "Lait Candia 1L", "6130000000123", selling = 120.0, purchase = 100.0, category = laitiers)
    private val soda = product(2, "Selecto 1L", "6130000000456", selling = 90.0, purchase = 70.0, category = boissons, supplier = cevital)
    private val noCode = product(3, "Sucre vrac", null, selling = 150.0)

    private val amine = client(1, "Épicerie Amine", phone = "0555123456", wilaya = "Souk Ahras", commune = "Souk Ahras", secteur = centre)
    private val nadir = client(2, "Superette Nadir", type = "wholesale")

    private val snapshot = ImportSnapshot(
        products = listOf(milk, soda, noCode), clients = listOf(amine, nadir),
        categories = listOf(boissons, laitiers), sousCategories = listOf(jus), marques = listOf(hamoud), suppliers = listOf(cevital), secteurs = listOf(centre),
    )

    private val geo = object : ImportGeo {
        private val communes = mapOf("Souk Ahras" to listOf("Souk Ahras", "Sedrata"), "Annaba" to listOf("Annaba", "El Bouni"))
        override fun wilaya(name: String) = communes.keys.firstOrNull { it.equals(name.trim(), ignoreCase = true) }
        override fun commune(wilaya: String, name: String) = communes[wilaya]?.firstOrNull { it.equals(name.trim(), ignoreCase = true) }
    }

    private fun planner(snapshot: ImportSnapshot = this.snapshot) = ImportPlanner(snapshot, geo)

    // ── Building sheets ──

    private fun text(s: String) = XlsxValue.Text(s)
    private fun num(s: String) = XlsxValue.Number(s)
    private fun date(y: Int, m: Int, d: Int) = XlsxValue.Date(LocalDate.of(y, m, d).atStartOfDay())

    /** A sheet from a header line and rows; a null cell is left out, as the reader does with an empty one. */
    private fun sheet(name: String, header: List<String>, vararg rows: List<Any?>): XlsxSheet {
        val all = mutableListOf(XlsxRow(1, header.withIndex().associate { (i, h) -> i to text(h) }))
        rows.forEachIndexed { index, cells ->
            val map = cells.withIndex().mapNotNull { (i, v) ->
                when (v) {
                    null -> null
                    is XlsxValue -> i to v
                    is String -> i to text(v)
                    is Int -> i to num(v.toString())
                    is Double -> i to num(v.toString())
                    else -> error("cell $v")
                }
            }.toMap()
            if (map.isNotEmpty()) all += XlsxRow(index + 2, map)
        }
        return XlsxSheet(name, all)
    }

    private val productHeader = listOf("Nom", "Code-barres", "Catégorie", "Sous-catégorie", "Marque", "Fournisseur", "Unité", "Unités par colis", "Prix d'achat (DA)", "Prix de vente (DA)", "Stock total", "Stock dépôt", "Stock camion", "Stock minimum", "Date de péremption")
    private val clientHeader = listOf("Nom", "Téléphone", "Type", "Secteur", "Wilaya", "Commune", "Adresse", "Solde (DA)", "Note")

    private fun products(vararg rows: List<Any?>) = XlsxWorkbook(listOf(sheet("Produits", productHeader, *rows)))
    private fun clients(vararg rows: List<Any?>) = XlsxWorkbook(listOf(sheet("Clients", clientHeader, *rows)))

    private fun refusal(row: PlannedRow): String = (row.outcome as? RowOutcome.Refused)?.reason ?: fail("row ${row.rowNumber} should be refused, was ${row.outcome}").let { "" }

    // ── Products ──

    @Test
    fun anExportReadBackChangesNothing() {
        val plan = planner().plan(products(
            listOf("Lait Candia 1L", "6130000000123", "Produits laitiers", null, null, null, "carton", 0, 100.0, 120.0, 5.0, 5.0, 0.0, 10, null),
            listOf("Selecto 1L", "6130000000456", "Boissons", null, null, "Cevital", "carton", 0, 70.0, 90.0, 5.0, 5.0, 0.0, 10, null),
        ))
        assertEquals(listOf(RowOutcome.Unchanged(1), RowOutcome.Unchanged(2)), plan.rows.map { it.outcome })
        assertTrue(plan.toCreate.isEmpty)
        assertFalse(plan.hasWork)
        // The stock columns are there, so the note about them is.
        assertEquals(1, plan.notes.size)
    }

    @Test
    fun aNewPriceIsAnUpdateWithTheChangeSpelledOut() {
        val plan = planner().plan(products(listOf("lait candia 1l", null, null, null, null, null, null, null, null, 130.0)))
        val row = plan.rows.single()
        val update = row.outcome as RowOutcome.UpdateProduct
        assertEquals(1, update.id)
        assertEquals(listOf(Change("Prix de vente", "120,00", "130,00")), update.changes)
        assertEquals(130.0, update.values.sellingPrice!!, 0.0)
        // A name that differs only by case is the same name, and stays as the app spells it.
        assertEquals("Lait Candia 1L", update.values.name)
    }

    @Test
    fun theBarcodeFindsTheProductEvenWhenRenamed() {
        val plan = planner().plan(products(listOf("Lait Candia 1 L", "6130000000123", null, null, null, null, null, null, null, null)))
        val update = plan.rows.single().outcome as RowOutcome.UpdateProduct
        assertEquals(1, update.id)
        assertEquals(listOf(Change("Nom", "Lait Candia 1L", "Lait Candia 1 L")), update.changes)
    }

    @Test
    fun aBarcodeTypedAsANumberIsReadDigitForDigit() {
        val plan = planner().plan(products(listOf("Selecto 1L", num("6130000000456"), null, null, null, null, null, null, null, 95.0)))
        val update = plan.rows.single().outcome as RowOutcome.UpdateProduct
        assertEquals(2, update.id)
        assertEquals("6130000000456", update.values.barcode)
    }

    @Test
    fun aNewProductTakesItsStockAndNamesWhatMustBeCreated() {
        val plan = planner().plan(products(
            listOf("Jus Rouiba 1L", "6130000000789", "Boissons", "Jus", "Rouiba", "Cevital", "pièce", 12, 60.5, 80.0, null, 24.0, null, 5, date(2027, 6, 30)),
            listOf("Eau Ifri 1.5L", null, "Eaux", "Plates", "Ifri", "Ifri SPA", "carton", null, 40.0, 55.0, null, null, null, null, "31/12/2026"),
        ))
        val (jus, eau) = plan.rows.map { it.outcome as RowOutcome.CreateProduct }
        assertEquals(
            ProductValues("Jus Rouiba 1L", "6130000000789", "Boissons", "Jus", "Rouiba", "Cevital", "pièce", 12, 60.5, 80.0, 24.0, 5, LocalDate.of(2027, 6, 30)),
            jus.values,
        )
        assertEquals(LocalDate.of(2026, 12, 31), eau.values.expiry)
        assertEquals(null, eau.values.barcode)
        assertEquals(
            LookupsToCreate(categories = listOf("Eaux"), sousCategories = mapOf("Eaux" to listOf("Plates")), marques = listOf("Rouiba", "Ifri"), suppliers = listOf("Ifri SPA")),
            plan.toCreate,
        )
        assertEquals(2, plan.creates)
    }

    @Test
    fun namesAreMatchedWithoutCaseOrAccents() {
        val plan = planner().plan(products(listOf("Selecto 1L", null, "BOISSONS", "jus", "hamoud boualem", "CEVITAL", null, null, null, null)))
        val update = plan.rows.single().outcome as RowOutcome.UpdateProduct
        // Boissons and Cevital are already its own; the sub-category and the brand are new to it but exist.
        assertEquals(listOf(Change("Sous-catégorie", null, "jus"), Change("Marque", null, "hamoud boualem")), update.changes)
        assertTrue(plan.toCreate.isEmpty)
    }

    @Test
    fun refusesWhatTheFormWouldRefuse() {
        val plan = planner().plan(products(
            listOf(null, "6130000000999", null, null, null, null, null, null, null, 10.0),
            listOf("Lait Candia 1L", "6130000000456", null, null, null, null, null, null, null, null),
            listOf("Selecto 1L", null, null, null, null, null, null, null, null, null),
            listOf("selecto 1l", "6130000000456", null, null, null, null, null, null, null, null),
            listOf("Nouveau", "6130000000123", null, null, null, null, null, null, null, null),
            listOf("Autre", null, null, null, null, null, "bouteille", null, null, null),
            listOf("Autre 2", null, null, "Jus", null, null, null, null, null, null),
            listOf("Autre 3", null, null, null, null, null, null, null, -5.0, null),
            listOf("Autre 4", null, null, null, null, null, null, null, "abc", null),
            listOf("Autre 5", null, null, null, null, null, null, 2.5, null, null),
            listOf("Autre 6", null, null, null, null, null, null, null, null, null, null, null, null, null, "hier"),
        ))
        val outcomes = plan.rows.map { it.outcome }
        fun reason(i: Int) = refusal(plan.rows[i])
        assertEquals("Le nom est obligatoire.", reason(0))
        // Selecto's barcode with the milk's name: the barcode finds Selecto, and the rename would take the milk's name.
        assertEquals("Ce nom de produit est déjà enregistré (code-barres 6130000000123).", reason(1))
        assertEquals(RowOutcome.Unchanged(2), outcomes[2])
        // Selecto again, a second row for the same product.
        assertEquals("« selecto 1l » est déjà sur une ligne plus haut.", reason(3))
        // A new name with the milk's barcode: the barcode finds the milk, and the name is a rename — allowed.
        assertEquals(listOf(Change("Nom", "Lait Candia 1L", "Nouveau")), (outcomes[4] as RowOutcome.UpdateProduct).changes)
        assertEquals("L'unité « bouteille » n'existe pas : carton ou pièce.", reason(5))
        assertEquals("La sous-catégorie « Jus » a besoin d'une catégorie.", reason(6))
        assertEquals("Le prix d'achat ne peut pas être négatif.", reason(7))
        assertEquals("« abc » n'est pas un nombre.", reason(8))
        assertEquals("« 2.5 » n'est pas un nombre entier.", reason(9))
        assertEquals("« hier » n'est pas une date (jj/mm/aaaa).", reason(10))
        assertEquals(9, plan.refused)
    }

    @Test
    fun twoProductsWithTheSameNameNeedABarcode() {
        val twins = snapshot.copy(products = listOf(product(1, "Lait", "111"), product(2, "Lait", "222")))
        val plan = planner(twins).plan(products(
            listOf("Lait", null, null, null, null, null, null, null, null, 10.0),
            listOf("Lait", "222", null, null, null, null, null, null, null, 10.0),
        ))
        assertEquals("Plusieurs produits portent le nom « Lait » : précisez le code-barres.", refusal(plan.rows[0]))
        assertEquals(2, (plan.rows[1].outcome as RowOutcome.UpdateProduct).id)
    }

    @Test
    fun onlyTheColumnsPresentAreRead() {
        val plan = planner().plan(XlsxWorkbook(listOf(sheet(
            "produits", listOf("Produit", "PV", "Remarque"),
            listOf("Sucre vrac", "3 450,00", "à vérifier"),
        ))))
        val sheetPlan = plan.sheets.single()
        assertEquals(listOf("Remarque"), sheetPlan.ignoredColumns)
        assertEquals(listOf(Change("Prix de vente", "150,00", "3450,00")), (sheetPlan.rows.single().outcome as RowOutcome.UpdateProduct).changes)
        assertTrue(plan.notes.isEmpty())
    }

    @Test
    fun aSheetWithoutANameColumnIsRefusedAsAWhole() {
        val plan = planner().plan(XlsxWorkbook(listOf(sheet("Produits", listOf("Code", "Prix"), listOf("123", 10.0)))))
        assertEquals("La première ligne doit être l'en-tête, avec au moins une colonne « Nom ».", refusal(plan.rows.single()))
    }

    @Test
    fun aWorkbookWithoutAKnownSheetCannotBeImported() {
        try {
            planner().plan(XlsxWorkbook(listOf(sheet("Ventes", listOf("N°", "Total"), listOf("V-1", 100.0)))))
            fail("should be refused")
        } catch (e: ImportPlanException) {
            assertTrue(e.message!!.contains("Produits"))
        }
    }

    // ── Clients ──

    @Test
    fun clientsAreMatchedByNameAndUpdated() {
        val plan = planner().plan(clients(
            listOf("épicerie amine", "0555000000", "Gros", null, null, null, "Rue 1", 500.0, null),
            listOf("Superette Nadir", null, "Gros", null, null, null, null, null, null),
        ))
        val update = plan.rows[0].outcome as RowOutcome.UpdateClient
        assertEquals(1, update.id)
        assertEquals(listOf(Change("Téléphone", "0555123456", "0555000000"), Change("Type", "retail", "wholesale"), Change("Adresse", null, "Rue 1")), update.changes)
        assertEquals("Épicerie Amine", update.values.name)
        assertEquals(RowOutcome.Unchanged(2), plan.rows[1].outcome)
        // Solde is in the file, and is not imported.
        assertEquals(1, plan.notes.size)
    }

    @Test
    fun aNewClientNeedsAKnownWilayaAndCommune() {
        val plan = planner().plan(clients(
            listOf("Nouveau 1", null, "Détail", "Gare", "souk ahras", "sedrata"),
            listOf("Nouveau 2", null, null, null, "Alger", null),
            listOf("Nouveau 3", null, null, null, "Annaba", "Sedrata"),
            listOf("Nouveau 4", null, null, "Gare", null, null),
            listOf("Nouveau 5", null, "VIP", null, null, null),
            listOf("Nouveau 6", null, null, null, null, "Sedrata"),
        ))
        val first = plan.rows[0].outcome as RowOutcome.CreateClient
        // Spelled as the app spells them.
        assertEquals(ClientValues("Nouveau 1", customerType = "retail", secteur = "Gare", wilaya = "Souk Ahras", commune = "Sedrata"), first.values)
        assertEquals("La wilaya « Alger » n'existe pas.", refusal(plan.rows[1]))
        assertEquals("La commune « Sedrata » n'est pas dans la wilaya de Annaba.", refusal(plan.rows[2]))
        assertEquals("Le secteur « Gare » a besoin d'une commune.", refusal(plan.rows[3]))
        assertEquals("Le type « VIP » n'existe pas : Détail, Gros ou Société.", refusal(plan.rows[4]))
        assertEquals("La commune « Sedrata » a besoin d'une wilaya.", refusal(plan.rows[5]))
        assertEquals(LookupsToCreate(secteurs = mapOf("Sedrata|Souk Ahras" to listOf("Gare"))), plan.toCreate)
    }

    @Test
    fun anExistingClientKeepsItsCommuneForANewSecteur() {
        val plan = planner().plan(clients(listOf("Épicerie Amine", null, null, "Centre", null, null), listOf("Superette Nadir", null, null, "Centre", null, null)))
        assertEquals(RowOutcome.Unchanged(1), plan.rows[0].outcome)
        assertEquals("Le secteur « Centre » a besoin d'une commune.", refusal(plan.rows[1]))
        assertTrue(plan.toCreate.isEmpty)
    }

    @Test
    fun bothSheetsAreReadTogether() {
        val plan = planner().plan(XlsxWorkbook(listOf(
            sheet("Clients", clientHeader, listOf("Superette Nadir", "0666", null, null, null, null, null, null, null)),
            sheet("Autre", listOf("x"), listOf("y")),
            sheet("Produits", productHeader, listOf("Nouveau", null, null, null, null, null, null, null, null, 10.0)),
        )))
        // In the import's order, whatever the file's.
        assertEquals(listOf(ImportSheet.PRODUITS, ImportSheet.CLIENTS), plan.sheets.map { it.sheet })
        assertEquals(1, plan.creates)
        assertEquals(1, plan.updates)
    }

    @Test
    fun twoPlansOverTheSameDataAreEqual() {
        val workbook = products(listOf("Lait Candia 1L", null, null, null, null, null, null, null, null, 130.0), listOf("Neuf", null, "Eaux"))
        assertEquals(planner().plan(workbook), planner().plan(workbook))
        val moved = snapshot.copy(products = snapshot.products.map { if (it.id == 1) it.copy(selling_price = 130.0) else it })
        assertFalse(planner(moved).plan(workbook) == planner().plan(workbook))
    }

    @Test
    fun keysIgnoreCaseAccentsSpacingAndBracketedUnits() {
        assertEquals("prix de vente", ImportText.headerKey("  Prix de vente (DA) "))
        assertEquals("code barres", ImportText.headerKey("Code-barres"))
        assertEquals("date de peremption", ImportText.headerKey("DATE DE PÉREMPTION"))
        assertEquals("epicerie amine", ImportText.key("Épicerie   Amine"))
        assertEquals("حليب كانديا", ImportText.key(" حليب كانديا "))
    }
}
