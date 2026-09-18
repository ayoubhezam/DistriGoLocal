package com.distrigo.app.data.importer

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.distrigo.app.data.geo.GeoRepository
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.CategoryEntity
import com.distrigo.app.data.local.entity.ClientEntity
import com.distrigo.app.data.local.entity.MarqueEntity
import com.distrigo.app.data.local.entity.SecteurEntity
import com.distrigo.app.data.local.entity.SousCategorieEntity
import com.distrigo.app.data.local.entity.SupplierEntity
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.time.Instant

/** What an import wrote, and the copy of the data taken before it. */
data class ImportResult(val created: Int, val updated: Int, val safetyBackup: File)

/** The data moved between the preview and the apply: the plan no longer describes what would happen. */
class ImportStaleException : Exception("the data changed since the plan was made")

/** Takes a copy of the data before an import, and says where it is. Throws `BackupFailedException` when it cannot. */
fun interface ImportSafety {
    fun backup(): File
}

/**
 * Imports a workbook: reads it, plans it against the live rows, and applies the plan.
 *
 * The apply takes a safety backup first, then writes everything in one transaction, planning again inside it
 * and comparing with the plan the user confirmed: a row changed in the meantime makes the whole import stop
 * before anything is written, rather than overwrite it. Products go through [ProductRepository], so a new
 * product's stock is an opening adjustment as the form makes it.
 */
class ImportRepository(
    private val db: AppDatabase,
    private val products: ProductRepository,
    private val geo: ImportGeo,
    private val safety: ImportSafety,
) {

    /** Reads the whole file; the stream is the caller's to close. */
    suspend fun read(input: InputStream): XlsxWorkbook = withContext(Dispatchers.IO) { XlsxReader.read(input) }

    suspend fun plan(workbook: XlsxWorkbook): ImportPlan = withContext(Dispatchers.IO) { ImportPlanner(snapshot(), geo).plan(workbook) }

    /**
     * Applies [plan], which must be the one made for [workbook]. Throws [ImportStaleException] when the data no
     * longer matches it, `BackupFailedException` when no safety backup could be taken; nothing is written then.
     */
    suspend fun apply(workbook: XlsxWorkbook, plan: ImportPlan): ImportResult {
        val backup = withContext(Dispatchers.IO) { safety.backup() }
        return db.withTransaction {
            if (ImportPlanner(snapshot(), geo).plan(workbook) != plan) throw ImportStaleException()
            val ids = Ids(snapshot()).also { it.create(plan.toCreate) }
            var created = 0
            var updated = 0
            for (row in plan.rows) {
                when (val outcome = row.outcome) {
                    is RowOutcome.CreateProduct -> { createProduct(outcome.values, ids); created++ }
                    is RowOutcome.UpdateProduct -> { updateProduct(outcome.id, outcome.values, ids); updated++ }
                    is RowOutcome.CreateClient -> { createClient(outcome.values, ids); created++ }
                    is RowOutcome.UpdateClient -> { updateClient(outcome.id, outcome.values, ids); updated++ }
                    is RowOutcome.Unchanged, is RowOutcome.Refused -> Unit
                }
            }
            ImportResult(created, updated, backup)
        }
    }

    private suspend fun snapshot() = ImportSnapshot(
        products = db.productDao().getAllProducts(),
        clients = db.clientDao().getAllClients(),
        categories = db.categoryDao().getAllCategories(),
        sousCategories = db.sousCategorieDao().getAllSousCategories(),
        marques = db.marqueDao().getAllMarques(),
        suppliers = db.supplierDao().getAllSuppliers(),
        secteurs = db.secteurDao().getAllSecteurs(),
    )

    // ── Products ──

    private suspend fun createProduct(values: ProductValues, ids: Ids) {
        val unit = values.unitType ?: "carton"
        val fields = mapOf(
            "name" to values.name,
            "barcode" to values.barcode,
            "selling_price" to (values.sellingPrice ?: 0.0),
            "purchase_price" to (values.purchasePrice ?: 0.0),
            "stock" to (values.initialStock ?: 0.0),
            "min_stock" to (values.minStock ?: 10),
            "unit_type" to unit,
            "pack_size" to if (unit == "pièce") (values.packSize ?: 0) else 0,
            "has_expiry" to if (values.expiry != null) 1 else 0,
            "expiry_date" to values.expiry?.toString(),
            "category_id" to values.category?.let(ids::category),
            "sous_categorie_id" to values.sousCategorie?.let { ids.sousCategorie(values.category!!, it) },
            "marque_id" to values.marque?.let(ids::marque),
        )
        val id = (products.addProduct(fields)["id"] as Number).toInt()
        // A product needs a barcode, and the form makes one from the next id when there is none to scan.
        if (values.barcode == null) {
            val code = id.toString().padStart(13, '0')
            if (!barcodeTaken(code)) products.updateProduct(id, mapOf("barcode" to code))
        }
        values.supplier?.let { products.linkProductToSupplier(ids.supplier(it), id, values.purchasePrice ?: 0.0) }
    }

    private suspend fun updateProduct(id: Int, values: ProductValues, ids: Ids) {
        val existing = db.productDao().getProductById(id) ?: throw ImportStaleException()
        val fields = mutableMapOf<String, Any?>("name" to values.name)
        values.barcode?.let { fields["barcode"] = it }
        values.sellingPrice?.let { fields["selling_price"] = it }
        values.purchasePrice?.let { fields["purchase_price"] = it }
        values.minStock?.let { fields["min_stock"] = it }
        values.unitType?.let { fields["unit_type"] = it }
        val unit = values.unitType ?: existing.unit_type
        values.packSize?.let { fields["pack_size"] = if (unit == "pièce") it else 0 }
        if (values.unitType != null && unit != "pièce") fields["pack_size"] = 0
        values.expiry?.let { fields["has_expiry"] = 1; fields["expiry_date"] = it.toString() }
        values.category?.let { fields["category_id"] = ids.category(it) }
        values.sousCategorie?.let { fields["sous_categorie_id"] = ids.sousCategorie(values.category ?: existing.category_name!!, it) }
        values.marque?.let { fields["marque_id"] = ids.marque(it) }
        products.updateProduct(id, fields)
        values.supplier?.let { products.linkProductToSupplier(ids.supplier(it), id, values.purchasePrice ?: existing.purchase_price) }
    }

    private fun barcodeTaken(code: String): Boolean =
        db.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM products WHERE barcode = ?", arrayOf(code))).use { it.moveToFirst() && it.getLong(0) > 0 }

    // ── Clients ──

    private suspend fun createClient(values: ClientValues, ids: Ids) {
        val secteur = values.secteur?.let { ids.secteur(values.commune!!, it) }
        db.clientDao().insertClient(
            ClientEntity(
                name = values.name, phone = values.phone, wilaya_name = values.wilaya, commune_name = values.commune,
                secteur_id = secteur?.id, secteur_name = secteur?.nom, address = values.address, note = values.note,
                customer_type = values.customerType ?: "retail", image_uri = null, latitude = null, longitude = null,
            )
        )
    }

    private suspend fun updateClient(id: Int, values: ClientValues, ids: Ids) {
        val existing = db.clientDao().getClientById(id) ?: throw ImportStaleException()
        val secteur = values.secteur?.let { ids.secteur(values.commune ?: existing.commune_name!!, it) }
        db.clientDao().updateClient(
            existing.copy(
                name = values.name,
                phone = values.phone ?: existing.phone,
                customer_type = values.customerType ?: existing.customer_type,
                wilaya_name = values.wilaya ?: existing.wilaya_name,
                commune_name = values.commune ?: existing.commune_name,
                secteur_id = secteur?.id ?: existing.secteur_id,
                secteur_name = secteur?.nom ?: existing.secteur_name,
                address = values.address ?: existing.address,
                note = values.note ?: existing.note,
            )
        )
    }

    // ── The lookups, by the names the file uses ──

    /** Ids of the categories, sub-categories, brands, suppliers and secteurs, found by name as the planner matched them. */
    private inner class Ids(snapshot: ImportSnapshot) {
        private val categories = snapshot.categories.associateTo(mutableMapOf()) { ImportText.key(it.name) to it.id }
        private val sousCategories = snapshot.sousCategories.associateTo(mutableMapOf()) { "${it.category_id}|${ImportText.key(it.name)}" to it.id }
        private val marques = snapshot.marques.associateTo(mutableMapOf()) { ImportText.key(it.name) to it.id }
        private val suppliers = snapshot.suppliers.associateTo(mutableMapOf()) { ImportText.key(it.name) to it.id }
        private val secteurs = snapshot.secteurs.associateTo(mutableMapOf()) { "${ImportText.key(it.commune_name)}|${ImportText.key(it.nom)}" to it }
        private var categoryOrder = snapshot.categories.maxOfOrNull { it.sort_order } ?: -1
        private var marqueOrder = snapshot.marques.maxOfOrNull { it.sort_order } ?: -1

        fun category(name: String): Int = categories.getValue(ImportText.key(name))
        fun sousCategorie(category: String, name: String): Int = sousCategories.getValue("${category(category)}|${ImportText.key(name)}")
        fun marque(name: String): Int = marques.getValue(ImportText.key(name))
        fun supplier(name: String): Int = suppliers.getValue(ImportText.key(name))
        fun secteur(commune: String, name: String): SecteurEntity = secteurs.getValue("${ImportText.key(commune)}|${ImportText.key(name)}")

        suspend fun create(toCreate: LookupsToCreate) {
            for (name in toCreate.categories) {
                categories[ImportText.key(name)] = db.categoryDao().insertCategory(CategoryEntity(name = name, sort_order = ++categoryOrder)).toInt()
            }
            for ((category, names) in toCreate.sousCategories) {
                val parent = category(category)
                var order = db.sousCategorieDao().getSousCategoriesForCategory(parent).maxOfOrNull { it.sort_order } ?: -1
                for (name in names) {
                    sousCategories["$parent|${ImportText.key(name)}"] =
                        db.sousCategorieDao().insertSousCategorie(SousCategorieEntity(category_id = parent, name = name, sort_order = ++order)).toInt()
                }
            }
            for (name in toCreate.marques) {
                marques[ImportText.key(name)] = db.marqueDao().insertMarque(MarqueEntity(name = name, sort_order = ++marqueOrder)).toInt()
            }
            for (name in toCreate.suppliers) {
                suppliers[ImportText.key(name)] = db.supplierDao().insertSupplier(
                    SupplierEntity(name = name, phone = null, address = null, note = null, balance = 0.0, latitude = null, longitude = null, wilaya_name = null, commune_name = null)
                ).toInt()
            }
            for ((place, names) in toCreate.secteurs) {
                val commune = place.substringBefore('|')
                val wilaya = place.substringAfter('|').ifEmpty { null }
                for (name in names) {
                    val entity = SecteurEntity(nom = name, commune_name = commune, wilaya_name = wilaya, created_at = Instant.now().toString())
                    val id = db.secteurDao().insertSecteur(entity).toInt()
                    secteurs["${ImportText.key(commune)}|${ImportText.key(name)}"] = entity.copy(id = id)
                }
            }
        }
    }

    companion object {
        /** The wilayas and communes as the client form offers them. */
        val APP_GEO: ImportGeo = object : ImportGeo {
            override fun wilaya(name: String): String? = GeoRepository.findWilayaByFrName(name.trim())?.nameFr
            override fun commune(wilaya: String, name: String): String? =
                GeoRepository.findWilayaByFrName(wilaya)?.let { GeoRepository.findCommuneByFrName(it.wilayaCode, name.trim())?.nameFr }
        }
    }
}
