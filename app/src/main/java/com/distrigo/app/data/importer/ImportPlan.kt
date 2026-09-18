package com.distrigo.app.data.importer

import com.distrigo.app.data.local.entity.CategoryEntity
import com.distrigo.app.data.local.entity.ClientEntity
import com.distrigo.app.data.local.entity.MarqueEntity
import com.distrigo.app.data.local.entity.ProductEntity
import com.distrigo.app.data.local.entity.SecteurEntity
import com.distrigo.app.data.local.entity.SousCategorieEntity
import com.distrigo.app.data.local.entity.SupplierEntity
import java.text.Normalizer
import java.time.LocalDate

/** The sheets an import knows. Their names and headers are the ones the export writes, so an export is the template. */
enum class ImportSheet(val title: String, val singular: String, val plural: String) {
    PRODUITS("Produits", "produit", "produits"),
    CLIENTS("Clients", "client", "clients");

    companion object {
        /** `produits`, `Produits `, `PRODUITS`: the same sheet. */
        fun named(name: String): ImportSheet? = entries.firstOrNull { ImportText.key(it.title) == ImportText.key(name) }
    }
}

/** What a product row gives; null where the cell was blank, which an update reads as "leave it". */
data class ProductValues(
    val name: String,
    val barcode: String? = null,
    val category: String? = null,
    val sousCategorie: String? = null,
    val marque: String? = null,
    val supplier: String? = null,
    val unitType: String? = null,
    val packSize: Int? = null,
    val purchasePrice: Double? = null,
    val sellingPrice: Double? = null,
    /** Only a new product takes stock from the file: an existing one is corrected by an inventory or an adjustment. */
    val initialStock: Double? = null,
    val minStock: Int? = null,
    val expiry: LocalDate? = null,
)

data class ClientValues(
    val name: String,
    val phone: String? = null,
    /** `retail`, `wholesale` or `business`. */
    val customerType: String? = null,
    val secteur: String? = null,
    val wilaya: String? = null,
    val commune: String? = null,
    val address: String? = null,
    val note: String? = null,
)

/** One field an update would change, in the words the screens use. */
data class Change(val label: String, val from: String?, val to: String?)

sealed class RowOutcome {
    data class CreateProduct(val values: ProductValues) : RowOutcome()
    data class UpdateProduct(val id: Int, val values: ProductValues, val changes: List<Change>) : RowOutcome()
    data class CreateClient(val values: ClientValues) : RowOutcome()
    data class UpdateClient(val id: Int, val values: ClientValues, val changes: List<Change>) : RowOutcome()
    data class Unchanged(val id: Int) : RowOutcome()
    data class Refused(val reason: String) : RowOutcome()
}

/** One row of the file and what the import would do with it. [name] is what the row calls the thing, for the screen. */
data class PlannedRow(val sheet: ImportSheet, val rowNumber: Int, val name: String, val outcome: RowOutcome) {
    val isCreate get() = outcome is RowOutcome.CreateProduct || outcome is RowOutcome.CreateClient
    val isUpdate get() = outcome is RowOutcome.UpdateProduct || outcome is RowOutcome.UpdateClient
    val isUnchanged get() = outcome is RowOutcome.Unchanged
    val isRefused get() = outcome is RowOutcome.Refused
}

/** Names the file uses that the app does not have yet, and would create first. */
data class LookupsToCreate(
    val categories: List<String> = emptyList(),
    /** Category name to its new sub-categories. */
    val sousCategories: Map<String, List<String>> = emptyMap(),
    val marques: List<String> = emptyList(),
    val suppliers: List<String> = emptyList(),
    /** `commune|wilaya` to its new secteurs. */
    val secteurs: Map<String, List<String>> = emptyMap(),
) {
    val isEmpty get() = categories.isEmpty() && sousCategories.isEmpty() && marques.isEmpty() && suppliers.isEmpty() && secteurs.isEmpty()
}

data class SheetPlan(
    val sheet: ImportSheet,
    /** Headers the sheet has that the import does not read. */
    val ignoredColumns: List<String>,
    val rows: List<PlannedRow>,
) {
    val creates get() = rows.count { it.isCreate }
    val updates get() = rows.count { it.isUpdate }
    val unchanged get() = rows.count { it.isUnchanged }
    val refused get() = rows.count { it.isRefused }
}

/**
 * Everything an import would do, decided before anything is written, so the screen can show it and the
 * apply can check the data has not moved underneath it (two plans over the same data are equal).
 */
data class ImportPlan(val sheets: List<SheetPlan>, val toCreate: LookupsToCreate, val notes: List<String>) {
    val rows get() = sheets.flatMap { it.rows }
    val creates get() = rows.count { it.isCreate }
    val updates get() = rows.count { it.isUpdate }
    val unchanged get() = rows.count { it.isUnchanged }
    val refused get() = rows.count { it.isRefused }
    /** Whether applying it would write anything. */
    val hasWork get() = creates > 0 || updates > 0
}

/** Why a workbook cannot be imported at all, as opposed to one of its rows. */
class ImportPlanException(message: String) : Exception(message)

/** The live rows the plan is decided against: what the DAOs return, outside the bin. */
data class ImportSnapshot(
    val products: List<ProductEntity>,
    val clients: List<ClientEntity>,
    val categories: List<CategoryEntity>,
    val sousCategories: List<SousCategorieEntity>,
    val marques: List<MarqueEntity>,
    val suppliers: List<SupplierEntity>,
    val secteurs: List<SecteurEntity>,
)

/** The wilayas and communes the app knows, as the client form offers them. */
interface ImportGeo {
    /** The wilaya's name as the app spells it, or null when there is no such wilaya. */
    fun wilaya(name: String): String?
    /** The commune's name as the app spells it, or null when [wilaya] has no such commune. */
    fun commune(wilaya: String, name: String): String?
}

/** Text as the import compares it: trimmed, one space between words, no case, no accents. */
object ImportText {
    fun key(text: String): String {
        val flat = Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "")
        return flat.lowercase().replace(NOT_WORD, " ").trim().replace(SPACES, " ")
    }

    /** A header's key, with a unit in brackets set aside: `Prix de vente (DA)` is `prix de vente`. */
    fun headerKey(header: String): String = key(header.replace(BRACKETS, " "))

    private val MARKS = Regex("\\p{M}+")
    private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val SPACES = Regex(" +")
    private val BRACKETS = Regex("\\([^)]*\\)")
}
