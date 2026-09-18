package com.distrigo.app.data.trash

import java.io.File
import com.distrigo.app.data.image.ImageReferences
import android.database.Cursor
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import com.distrigo.app.data.local.database.AppDatabase
import java.time.Instant

/** A kind of thing that can be in the bin: one table with a `deleted_at` column. */
enum class TrashKind(val table: String, val label: String) {
    PRODUCTS("products", "Produits"),
    CLIENTS("clients", "Clients"),
    SUPPLIERS("suppliers", "Fournisseurs"),
    CATEGORIES("categories", "Catégories"),
    SOUS_CATEGORIES("sous_categories", "Sous-catégories"),
    MARQUES("marques", "Marques"),
    CHARGE_TYPES("charge_types", "Types de charges"),
    CHARGE_SUBTYPES("charge_subtypes", "Sous-types de charges"),
    PERTE_TYPES("perte_types", "Types de pertes"),
}

/** Something in the bin, as the bin lists it. */
data class TrashItem(
    val kind: TrashKind,
    val id: Long,
    val name: String,
    /** What tells it apart from others of the same name: a barcode, a phone, its parent. Null when nothing does. */
    val detail: String?,
    val deletedAt: Instant,
)

/** One way an item is still used, and how many times: `1 vente`, `12 lignes de vente`. */
data class Usage(val singular: String, val plural: String, val count: Long) {
    override fun toString() = "$count ${if (count == 1L) singular else plural}"
}

sealed class TrashOutcome {
    data object Done : TrashOutcome()
    /** Refused, with the reason in words the user can act on. Nothing was changed. */
    data class Refused(val reason: String) : TrashOutcome()
}

/**
 * The bin: what was deleted, putting it back, and deleting it for good.
 *
 * ### What deleting did
 *
 * Deleting a client, a product or one of their kinds only stamped `deleted_at`: the row stays, every screen
 * leaves it out, and documents that name it keep their own copy of the name. So restoring is clearing the
 * stamp — the change-tracking triggers record it like any edit.
 *
 * ### What restoring checks
 *
 * The rules the forms apply when something is created, since a restored row rejoins the others: a product may
 * not come back with the name or barcode of an active one, nor a category, a brand or a type with an active one's
 * name. A sous-catégorie or a charge sub-type is refused while its parent is still in the bin, and may not take
 * the name of an active sibling. Clients and suppliers have nothing that must be unique, and always come back.
 *
 * ### What deleting for good allows
 *
 * Only an item nothing uses: no document, payment, movement, draft or child — counting those in the bin, since
 * any of them could be restored and would then point at nothing. A product takes its own gallery photos and price
 * history with it. The row is deleted, and the tombstone trigger records it for a later sync.
 */
/** [imagesDir] is where stored photos live; null keeps the files, which only the tests want. */
class TrashRepository(private val db: AppDatabase, private val imagesDir: File? = null) {

    private val sql: SupportSQLiteDatabase get() = db.openHelper.writableDatabase

    /** How many items of each kind are in the bin, for every kind. Blocks: off the main thread. */
    fun counts(): Map<TrashKind, Int> = TrashKind.entries.associateWith { kind ->
        sql.query("SELECT COUNT(*) FROM ${kind.table} WHERE deleted_at IS NOT NULL").use { it.moveToFirst(); it.getInt(0) }
    }

    /** What is in the bin of [kind], the most recently deleted first. Blocks: off the main thread. */
    fun items(kind: TrashKind): List<TrashItem> {
        val (select, detail) = when (kind) {
            TrashKind.PRODUCTS -> "SELECT t.id, t.name, t.deleted_at, t.barcode AS a, t.category_name AS b FROM products t" to
                { c: Cursor -> join(c.textOrNull(3), c.textOrNull(4)) }
            TrashKind.CLIENTS -> "SELECT t.id, t.name, t.deleted_at, t.phone AS a, COALESCE(t.commune_name, t.secteur_name) AS b FROM clients t" to
                { c: Cursor -> join(c.textOrNull(3), c.textOrNull(4)) }
            TrashKind.SUPPLIERS -> "SELECT t.id, t.name, t.deleted_at, t.phone AS a FROM suppliers t" to
                { c: Cursor -> c.textOrNull(3) }
            TrashKind.SOUS_CATEGORIES ->
                "SELECT t.id, t.name, t.deleted_at, p.name AS a FROM sous_categories t LEFT JOIN categories p ON p.id = t.category_id" to
                    { c: Cursor -> c.textOrNull(3)?.let { "Catégorie : $it" } }
            TrashKind.CHARGE_SUBTYPES ->
                "SELECT t.id, t.name, t.deleted_at, p.name AS a FROM charge_subtypes t LEFT JOIN charge_types p ON p.id = t.type_id" to
                    { c: Cursor -> c.textOrNull(3)?.let { "Type : $it" } }
            else -> "SELECT t.id, t.name, t.deleted_at FROM ${kind.table} t" to { _: Cursor -> null }
        }
        return sql.query("$select WHERE t.deleted_at IS NOT NULL ORDER BY t.deleted_at DESC, t.id DESC").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(TrashItem(kind, cursor.getLong(0), cursor.getString(1).orEmpty(), detail(cursor), Instant.ofEpochMilli(cursor.getLong(2))))
                }
            }
        }
    }

    /** Puts [id] of [kind] back, unless a rule of the forms forbids it. */
    fun restore(kind: TrashKind, id: Long): TrashOutcome = inTransaction {
        val row = row(kind, id) ?: return@inTransaction TrashOutcome.Refused("Cet élément n'est plus dans la corbeille.")
        restoreConflict(kind, id, row)?.let { return@inTransaction TrashOutcome.Refused(it) }
        sql.execSQL("UPDATE ${kind.table} SET deleted_at = NULL WHERE id = ? AND deleted_at IS NOT NULL", arrayOf<Any>(id))
        TrashOutcome.Done
    }

    /** Everything that still uses [id] of [kind]; empty when it can be deleted for good. Blocks: off the main thread. */
    fun usages(kind: TrashKind, id: Long): List<Usage> =
        usageQueries(kind).mapNotNull { (label, query) ->
            val count = sql.query(SimpleSQLiteQuery(query, arrayOf<Any>(id))).use { it.moveToFirst(); it.getLong(0) }
            if (count > 0) Usage(label.first, label.second, count) else null
        }

    /**
     * Deletes [id] of [kind] for good, only when nothing uses it. Cannot be undone. A product's photo files go
     * with it, unless another row still shows the same picture (files are named by content, so they are shared).
     */
    fun deletePermanently(kind: TrashKind, id: Long): TrashOutcome {
        val photos = mutableSetOf<String>()
        val outcome = inTransaction {
            if (row(kind, id) == null) return@inTransaction TrashOutcome.Refused("Cet élément n'est plus dans la corbeille.")
            val usages = usages(kind, id)
            if (usages.isNotEmpty()) {
                return@inTransaction TrashOutcome.Refused("Impossible de le supprimer définitivement : il est utilisé par " + words(usages) + ".")
            }
            if (kind == TrashKind.PRODUCTS) {
                photos += photoHashesOf(id)
                // Its own photo rows and price history, which nothing else refers to.
                sql.execSQL("DELETE FROM product_images WHERE product_id = ?", arrayOf<Any>(id))
                sql.execSQL("DELETE FROM price_history WHERE product_id = ?", arrayOf<Any>(id))
            }
            sql.execSQL("DELETE FROM ${kind.table} WHERE id = ? AND deleted_at IS NOT NULL", arrayOf<Any>(id))
            TrashOutcome.Done
        }
        // After the commit: a file cannot be rolled back, and a photo still referenced elsewhere stays.
        if (outcome == TrashOutcome.Done && photos.isNotEmpty()) deleteUnreferenced(photos)
        return outcome
    }

    /** The hashes of a product's cover and gallery photos. */
    private fun photoHashesOf(productId: Long): Set<String> {
        val hashes = mutableSetOf<String>()
        sql.query(SimpleSQLiteQuery("SELECT image_uri FROM products WHERE id = ?", arrayOf<Any>(productId))).use {
            if (it.moveToFirst()) hashes += ImageReferences.hashesIn(it.textOrNull(0))
        }
        sql.query(SimpleSQLiteQuery("SELECT image_ref FROM product_images WHERE product_id = ?", arrayOf<Any>(productId))).use {
            while (it.moveToNext()) hashes += ImageReferences.hashesIn(it.textOrNull(0))
        }
        return hashes
    }

    private fun deleteUnreferenced(hashes: Set<String>) {
        val dir = imagesDir ?: return
        val stillUsed = ImageReferences.referencedHashes(sql)
        for (hash in hashes - stillUsed) File(dir, "$hash.jpg").delete()
    }

    // ── Rules ──

    /** The row's fields a restore is judged on, or null if it is not in the bin. */
    private fun row(kind: TrashKind, id: Long): Map<String, String?>? {
        val columns = when (kind) {
            TrashKind.PRODUCTS -> "name, barcode"
            TrashKind.SOUS_CATEGORIES -> "name, category_id AS parent"
            TrashKind.CHARGE_SUBTYPES -> "name, type_id AS parent"
            else -> "name"
        }
        return sql.query(SimpleSQLiteQuery("SELECT $columns FROM ${kind.table} WHERE id = ? AND deleted_at IS NOT NULL", arrayOf<Any>(id))).use { c ->
            if (!c.moveToFirst()) null else (0 until c.columnCount).associate { c.getColumnName(it) to c.textOrNull(it) }
        }
    }

    private fun restoreConflict(kind: TrashKind, id: Long, row: Map<String, String?>): String? {
        val name = row["name"]?.trim().orEmpty()
        fun activeWithName(table: String, extra: String = "", vararg extraArgs: Any): Boolean =
            exists("SELECT 1 FROM $table WHERE deleted_at IS NULL AND id != ? AND LOWER(TRIM(name)) = LOWER(?)$extra", id, name, *extraArgs)

        return when (kind) {
            TrashKind.PRODUCTS -> {
                val barcode = row["barcode"]?.trim().orEmpty()
                when {
                    activeWithName("products") -> "Un produit actif s'appelle déjà « $name ». Renommez-le avant de restaurer celui-ci."
                    barcode.isNotEmpty() && exists("SELECT 1 FROM products WHERE deleted_at IS NULL AND id != ? AND TRIM(barcode) = ?", id, barcode) ->
                        "Le code-barres $barcode est déjà utilisé par un produit actif."
                    else -> null
                }
            }
            TrashKind.CATEGORIES, TrashKind.MARQUES, TrashKind.CHARGE_TYPES, TrashKind.PERTE_TYPES ->
                if (activeWithName(kind.table)) "${singular(kind)} « $name » existe déjà." else null
            TrashKind.SOUS_CATEGORIES, TrashKind.CHARGE_SUBTYPES -> {
                val parentTable = if (kind == TrashKind.SOUS_CATEGORIES) "categories" else "charge_types"
                val parentColumn = if (kind == TrashKind.SOUS_CATEGORIES) "category_id" else "type_id"
                val parent = row["parent"]
                when {
                    parent != null && exists("SELECT 1 FROM $parentTable WHERE id = ? AND deleted_at IS NOT NULL", parent) ->
                        "${if (kind == TrashKind.SOUS_CATEGORIES) "Sa catégorie" else "Son type de charge"} est aussi dans la corbeille : restaurez-${if (kind == TrashKind.SOUS_CATEGORIES) "la" else "le"} d'abord."
                    parent != null && activeWithName(kind.table, " AND $parentColumn = ?", parent) ->
                        "${singular(kind)} « $name » existe déjà au même endroit."
                    else -> null
                }
            }
            TrashKind.CLIENTS, TrashKind.SUPPLIERS -> null
        }
    }

    /** Label and count query of everything that can point at an item of [kind]; `?` is its id. */
    private fun usageQueries(kind: TrashKind): List<Pair<Pair<String, String>, String>> {
        fun count(table: String, column: String) = "SELECT COUNT(*) FROM $table WHERE $column = ?"
        // Drafts keep their lines as JSON; a line's product is written `"product_id":12` followed by `,` or `}`.
        fun inDraftItems(table: String) =
            "SELECT COUNT(*) FROM $table WHERE instr(items_json, '\"product_id\":' || ?1 || ',') > 0 OR instr(items_json, '\"product_id\":' || ?1 || '}') > 0"
        return when (kind) {
            TrashKind.PRODUCTS -> listOf(
                ("ligne de vente" to "lignes de vente") to count("vente_items", "product_id"),
                ("ligne d'achat" to "lignes d'achat") to count("purchase_order_items", "product_id"),
                ("ligne de chargement" to "lignes de chargement") to count("chargement_items", "product_id"),
                ("mouvement de stock" to "mouvements de stock") to count("stock_movements", "product_id"),
                ("perte" to "pertes") to count("pertes", "product_id"),
                ("ligne d'inventaire" to "lignes d'inventaire") to count("inventory_items", "product_id"),
                ("retour client" to "retours clients") to count("retour_client_items", "product_id"),
                ("retour fournisseur" to "retours fournisseurs") to count("retour_fournisseur_items", "product_id"),
                ("brouillon de vente" to "brouillons de vente") to inDraftItems("vente_drafts"),
                ("brouillon de tournée" to "brouillons de tournée") to inDraftItems("tournee_vente_drafts"),
                ("brouillon d'achat" to "brouillons d'achat") to inDraftItems("purchase_drafts"),
                ("brouillon de chargement" to "brouillons de chargement") to
                    "SELECT COUNT(*) FROM chargement_drafts WHERE single_product_id = ?1 OR instr(items_json, '\"product_id\":' || ?1 || ',') > 0 OR instr(items_json, '\"product_id\":' || ?1 || '}') > 0",
            )
            TrashKind.CLIENTS -> listOf(
                ("vente" to "ventes") to count("ventes", "client_id"),
                ("paiement" to "paiements") to count("client_payments", "client_id"),
                ("tournée" to "tournées") to count("tournee_clients", "client_id"),
                ("retour" to "retours") to count("retour_client", "client_id"),
                ("brouillon de vente" to "brouillons de vente") to count("vente_drafts", "client_id"),
                ("brouillon de tournée" to "brouillons de tournée") to count("tournee_vente_drafts", "client_id"),
            )
            TrashKind.SUPPLIERS -> listOf(
                ("produit" to "produits") to count("products", "supplier_id"),
                ("bon d'achat" to "bons d'achat") to count("purchase_orders", "supplier_id"),
                ("paiement" to "paiements") to count("supplier_payments", "supplier_id"),
                ("retour" to "retours") to count("retour_fournisseur", "supplier_id"),
                ("brouillon d'achat" to "brouillons d'achat") to count("purchase_drafts", "supplier_id"),
            )
            TrashKind.CATEGORIES -> listOf(
                ("produit" to "produits") to count("products", "category_id"),
                ("sous-catégorie" to "sous-catégories") to count("sous_categories", "category_id"),
            )
            TrashKind.SOUS_CATEGORIES -> listOf(("produit" to "produits") to count("products", "sous_categorie_id"))
            TrashKind.MARQUES -> listOf(("produit" to "produits") to count("products", "marque_id"))
            TrashKind.CHARGE_TYPES -> listOf(
                ("sous-type" to "sous-types") to count("charge_subtypes", "type_id"),
                ("charge" to "charges") to count("charges", "type_id"),
            )
            TrashKind.CHARGE_SUBTYPES -> listOf(("charge" to "charges") to count("charges", "subtype_id"))
            TrashKind.PERTE_TYPES -> listOf(("perte" to "pertes") to count("pertes", "type_id"))
        }
    }

    // ── Helpers ──

    /** Runs [block] in Room's transaction, so every screen watching these tables is told of the change. */
    private fun <T> inTransaction(block: () -> T): T = db.runInTransaction<T> { block() }

    private fun exists(query: String, vararg args: Any): Boolean =
        sql.query(SimpleSQLiteQuery(query, args)).use { it.moveToFirst() }

    private fun Cursor.textOrNull(index: Int): String? = if (isNull(index)) null else getString(index)?.takeIf { it.isNotBlank() }

    private fun join(vararg parts: String?): String? = parts.filterNotNull().joinToString(" · ").ifEmpty { null }

    companion object {
        fun singular(kind: TrashKind): String = when (kind) {
            TrashKind.PRODUCTS -> "Le produit"
            TrashKind.CLIENTS -> "Le client"
            TrashKind.SUPPLIERS -> "Le fournisseur"
            TrashKind.CATEGORIES -> "La catégorie"
            TrashKind.SOUS_CATEGORIES -> "La sous-catégorie"
            TrashKind.MARQUES -> "La marque"
            TrashKind.CHARGE_TYPES -> "Le type de charge"
            TrashKind.CHARGE_SUBTYPES -> "Le sous-type"
            TrashKind.PERTE_TYPES -> "Le type de perte"
        }

        /** `12 lignes de vente et 3 mouvements de stock`. */
        fun words(usages: List<Usage>): String = when (usages.size) {
            0 -> ""
            1 -> usages.single().toString()
            else -> usages.dropLast(1).joinToString(", ") + " et " + usages.last()
        }
    }
}
