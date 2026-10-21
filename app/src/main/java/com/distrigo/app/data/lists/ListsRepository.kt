package com.distrigo.app.data.lists

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.newRowUuid
import com.distrigo.app.data.trash.Usage
import java.text.Collator
import java.util.Locale

/**
 * A list that classifies the catalogue, the documents or the clients, as « Listes et catégories » manages it
 * (owner, 2026-10-21). [label] names the list, [singular] one of its entries.
 */
enum class ListKind(val table: String, val label: String, val singular: String) {
    CATEGORIES("categories", "Catégories", "catégorie"),
    SOUS_CATEGORIES("sous_categories", "Sous-catégories", "sous-catégorie"),
    MARQUES("marques", "Marques", "marque"),
    CHARGE_TYPES("charge_types", "Types de charges", "type de charge"),
    CHARGE_SUBTYPES("charge_subtypes", "Sous-types de charges", "sous-type"),
    PERTE_TYPES("perte_types", "Types de pertes", "type de perte"),
    SECTEURS("secteurs", "Secteurs", "secteur");

    /** A secteur's name is `nom`; every other list's is `name`. */
    val nameColumn: String get() = if (this == SECTEURS) "nom" else "name"

    /** The entry this list hangs under: a sous-catégorie's category, a sous-type's type of charge. */
    val parentColumn: String?
        get() = when (this) {
            SOUS_CATEGORIES -> "category_id"
            CHARGE_SUBTYPES -> "type_id"
            else -> null
        }

    /** The list under each of this list's entries. */
    val children: ListKind?
        get() = when (this) {
            CATEGORIES -> SOUS_CATEGORIES
            CHARGE_TYPES -> CHARGE_SUBTYPES
            else -> null
        }

    /** Deleting stamps `deleted_at`, so the Corbeille can restore it. Secteurs have no such column. */
    val softDeleted: Boolean get() = this != SECTEURS

    /** The lists whose built-in entries (`is_default`) the app relies on. */
    val hasDefaults: Boolean get() = this == CHARGE_TYPES || this == CHARGE_SUBTYPES || this == PERTE_TYPES

    /** « Une catégorie », « Un type de perte »: feminine for the lists named so. */
    val feminine: Boolean get() = this == CATEGORIES || this == SOUS_CATEGORIES || this == MARQUES
}

/**
 * One entry of a list: its name, what tells it apart ([detail]: a secteur's commune), what uses it now, how many
 * entries hang under it, and whether it is one of the app's own.
 */
data class ListEntry(
    val id: Long,
    val name: String,
    val detail: String?,
    val usages: List<Usage>,
    val children: Int,
    val isDefault: Boolean,
) {
    /** Deletable: not built in, and nothing live uses it. */
    val deletable: Boolean get() = !isDefault && usages.isEmpty()
}

sealed class ListOutcome {
    data object Done : ListOutcome()
    /** Refused, with the reason in words the user can act on. Nothing was changed. */
    data class Refused(val reason: String) : ListOutcome()
}

/**
 * The lists of « Listes et catégories »: what is in them, and adding, renaming and deleting their entries.
 *
 * ### Renaming
 *
 * A name is copied onto the rows that use it — a product keeps its category's, sous-catégorie's and brand's names,
 * a charge its type's and sous-type's, a perte its type's, a client and a tournée their secteur's — and the reports
 * group charges and pertes by that copy. So a rename rewrites every copy in the same transaction: a typo fixed in
 * « Carburant » does not split the Charges report in two.
 *
 * ### Deleting
 *
 * Only an entry nothing live uses: no live product, client, charge, perte or tournée. A built-in type of charge or
 * of perte is never deleted (returns and the Charges screen rely on them; renaming one is fine, they are known by
 * their uuid). Deleting stamps `deleted_at`, as the forms' own deletes do, so the Corbeille can put it back; a
 * category's or a type's entries go with it. A secteur has no `deleted_at`, and is deleted for good.
 *
 * ### Adding
 *
 * The forms' rules: a name, and none an active entry already has in the same place (the same category, type of
 * charge or commune), case and spaces aside.
 */
class ListsRepository(private val db: AppDatabase) {

    private val sql: SupportSQLiteDatabase get() = db.openHelper.writableDatabase

    /** How many live entries each list has. Blocks: off the main thread. */
    fun counts(): Map<ListKind, Int> = ListKind.entries.associateWith { kind ->
        count("SELECT COUNT(*) FROM ${kind.table}${if (kind.softDeleted) " WHERE deleted_at IS NULL" else ""}")
    }

    /**
     * The live entries of [kind] — under [parentId] for a sous-catégorie or a sous-type — by name, each with what
     * uses it. Blocks: off the main thread.
     */
    fun entries(kind: ListKind, parentId: Long? = null): List<ListEntry> {
        val where = buildList {
            if (kind.softDeleted) add("deleted_at IS NULL")
            if (kind.parentColumn != null && parentId != null) add("${kind.parentColumn} = $parentId")
        }.joinToString(" AND ").let { if (it.isEmpty()) "" else " WHERE $it" }
        val defaultColumn = if (kind.hasDefaults) "is_default" else "0"
        val detailColumn = if (kind == ListKind.SECTEURS) "commune_name" else "NULL"
        val usages = usageCounts(kind)
        val children = kind.children?.let { child ->
            groupCount("SELECT ${child.parentColumn}, COUNT(*) FROM ${child.table} WHERE deleted_at IS NULL GROUP BY ${child.parentColumn}")
        }.orEmpty()

        val rows = sql.query("SELECT id, ${kind.nameColumn}, $defaultColumn, $detailColumn FROM ${kind.table}$where").use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    add(
                        ListEntry(
                            id = id,
                            name = c.getString(1).orEmpty(),
                            detail = if (c.isNull(3)) null else c.getString(3),
                            usages = usages.mapNotNull { (label, byId) -> byId[id]?.let { Usage(label.first, label.second, it) } },
                            children = children[id]?.toInt() ?: 0,
                            isDefault = c.getInt(2) != 0,
                        )
                    )
                }
            }
        }
        val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.SECONDARY }
        return rows.sortedWith(compareBy(collator, { it: ListEntry -> it.detail.orEmpty() }).thenBy(collator) { it.name.trim() })
    }

    /**
     * Adds [name] to [kind]: under [parentId] for a sous-catégorie or a sous-type, in [commune] (of [wilaya]) for a
     * secteur. [hasFournisseur] is a sous-type's « Nécessite un fournisseur / une station ».
     */
    fun add(
        kind: ListKind,
        name: String,
        parentId: Long? = null,
        commune: String? = null,
        wilaya: String? = null,
        hasFournisseur: Boolean = false,
    ): ListOutcome = inTransaction {
        val clean = clean(name)
        if (clean.isEmpty()) return@inTransaction ListOutcome.Refused("Saisissez un nom.")
        if (kind.parentColumn != null && parentId == null) return@inTransaction ListOutcome.Refused("Choisissez d'abord où l'ajouter.")
        if (kind == ListKind.SECTEURS && commune.isNullOrBlank()) return@inTransaction ListOutcome.Refused("Choisissez la commune du secteur.")
        taken(kind, clean, exceptId = null, parentId = parentId, commune = commune)?.let { return@inTransaction ListOutcome.Refused(it) }

        val now = java.time.Instant.now()
        val values = ContentValues().apply {
            put(kind.nameColumn, clean)
            put("uuid", newRowUuid())
            put("created_at", now.toString())
            put("updated_at", now.toEpochMilli())
            put("version", 1)
            when (kind) {
                ListKind.CATEGORIES, ListKind.MARQUES -> put("sort_order", 0)
                ListKind.SOUS_CATEGORIES -> { put("category_id", parentId); put("sort_order", 0) }
                // The generic icon and colour the forms give a type they create
                ListKind.CHARGE_TYPES -> { put("icon", "category"); put("color_hex", "#98A2B3"); put("is_default", 0) }
                ListKind.CHARGE_SUBTYPES -> {
                    put("type_id", parentId); put("icon", "category"); put("has_fournisseur", if (hasFournisseur) 1 else 0); put("is_default", 0)
                }
                ListKind.PERTE_TYPES -> { put("icon", "category"); put("color_hex", "#98A2B3"); put("is_default", 0) }
                ListKind.SECTEURS -> { put("commune_name", commune!!.trim()); put("wilaya_name", wilaya?.trim()?.ifEmpty { null }) }
            }
        }
        sql.insert(kind.table, SQLiteDatabase.CONFLICT_ABORT, values)
        ListOutcome.Done
    }

    /** Renames [id] of [kind] to [name], and every copy of its name with it (see the class). */
    fun rename(kind: ListKind, id: Long, name: String): ListOutcome = inTransaction {
        val clean = clean(name)
        if (clean.isEmpty()) return@inTransaction ListOutcome.Refused("Saisissez un nom.")
        val (current, parentId, commune) = row(kind, id) ?: return@inTransaction ListOutcome.Refused(gone(kind))
        if (current == clean) return@inTransaction ListOutcome.Done
        taken(kind, clean, exceptId = id, parentId = parentId, commune = commune)?.let { return@inTransaction ListOutcome.Refused(it) }

        sql.execSQL("UPDATE ${kind.table} SET ${kind.nameColumn} = ? WHERE id = ?", arrayOf<Any>(clean, id))
        for ((table, column, idColumn) in copiesOf(kind)) {
            sql.execSQL("UPDATE $table SET $column = ? WHERE $idColumn = ? AND $column IS NOT ?", arrayOf<Any>(clean, id, clean))
        }
        ListOutcome.Done
    }

    /** Deletes [id] of [kind], with the entries under it, unless it is built in or still used (see the class). */
    fun delete(kind: ListKind, id: Long): ListOutcome = inTransaction {
        row(kind, id) ?: return@inTransaction ListOutcome.Refused(gone(kind))
        val entry = entries(kind).firstOrNull { it.id == id } ?: return@inTransaction ListOutcome.Refused(gone(kind))
        if (entry.isDefault) return@inTransaction ListOutcome.Refused(BUILT_IN)
        if (entry.usages.isNotEmpty()) return@inTransaction ListOutcome.Refused(inUse(kind, entry))

        if (kind.softDeleted) {
            kind.children?.let { child ->
                sql.execSQL("UPDATE ${child.table} SET deleted_at = ? WHERE ${child.parentColumn} = ? AND deleted_at IS NULL", arrayOf<Any>(nowMillis(), id))
            }
            sql.execSQL("UPDATE ${kind.table} SET deleted_at = ? WHERE id = ? AND deleted_at IS NULL", arrayOf<Any>(nowMillis(), id))
        } else {
            sql.execSQL("DELETE FROM ${kind.table} WHERE id = ?", arrayOf<Any>(id))
        }
        ListOutcome.Done
    }

    // ── Rules ──

    /** Why [name] cannot be [kind]'s: an active entry in the same place already has it, case and spaces aside. */
    private fun taken(kind: ListKind, name: String, exceptId: Long?, parentId: Long?, commune: String?): String? {
        val conditions = buildList {
            add("LOWER(TRIM(${kind.nameColumn})) = LOWER(?)")
            if (kind.softDeleted) add("deleted_at IS NULL")
            if (exceptId != null) add("id != $exceptId")
            if (kind.parentColumn != null && parentId != null) add("${kind.parentColumn} = $parentId")
            if (kind == ListKind.SECTEURS) add("LOWER(TRIM(commune_name)) = LOWER(TRIM(?))")
        }
        val args = listOfNotNull<Any>(name, if (kind == ListKind.SECTEURS) commune.orEmpty() else null).toTypedArray()
        val exists = sql.query(SimpleSQLiteQuery("SELECT 1 FROM ${kind.table} WHERE ${conditions.joinToString(" AND ")}", args)).use { it.moveToFirst() }
        if (!exists) return null
        val place = when (kind) {
            ListKind.SOUS_CATEGORIES -> " dans cette catégorie"
            ListKind.CHARGE_SUBTYPES -> " dans ce type"
            ListKind.SECTEURS -> " dans cette commune"
            else -> ""
        }
        return "${article(kind)} « $name » existe déjà$place."
    }

    /** The current name of [id], its parent and its commune; null when it is not a live entry. */
    private fun row(kind: ListKind, id: Long): Triple<String, Long?, String?>? {
        val parent = kind.parentColumn ?: "NULL"
        val commune = if (kind == ListKind.SECTEURS) "commune_name" else "NULL"
        val live = if (kind.softDeleted) " AND deleted_at IS NULL" else ""
        return sql.query(SimpleSQLiteQuery("SELECT ${kind.nameColumn}, $parent, $commune FROM ${kind.table} WHERE id = ?$live", arrayOf<Any>(id))).use { c ->
            if (!c.moveToFirst()) null
            else Triple(c.getString(0).orEmpty(), if (c.isNull(1)) null else c.getLong(1), if (c.isNull(2)) null else c.getString(2))
        }
    }

    /** Where [kind]'s name is copied: the table, the copy's column, and the column holding the entry's id. */
    private fun copiesOf(kind: ListKind): List<Triple<String, String, String>> = when (kind) {
        ListKind.CATEGORIES -> listOf(Triple("products", "category_name", "category_id"))
        ListKind.SOUS_CATEGORIES -> listOf(Triple("products", "sous_categorie_name", "sous_categorie_id"))
        ListKind.MARQUES -> listOf(Triple("products", "marque_name", "marque_id"))
        ListKind.CHARGE_TYPES -> listOf(Triple("charges", "type_name", "type_id"))
        ListKind.CHARGE_SUBTYPES -> listOf(Triple("charges", "subtype_name", "subtype_id"))
        ListKind.PERTE_TYPES -> listOf(Triple("pertes", "type_name", "type_id"))
        ListKind.SECTEURS -> listOf(Triple("clients", "secteur_name", "secteur_id"), Triple("tournee_secteurs", "secteur_name", "secteur_id"))
    }

    /** What uses an entry of [kind] now, as (label, counts by entry id). Live products and clients only. */
    private fun usageCounts(kind: ListKind): List<Pair<Pair<String, String>, Map<Long, Long>>> {
        fun by(table: String, column: String, live: Boolean = false) =
            groupCount("SELECT $column, COUNT(*) FROM $table WHERE $column IS NOT NULL${if (live) " AND deleted_at IS NULL" else ""} GROUP BY $column")
        return when (kind) {
            ListKind.CATEGORIES -> listOf(("produit" to "produits") to by("products", "category_id", live = true))
            ListKind.SOUS_CATEGORIES -> listOf(("produit" to "produits") to by("products", "sous_categorie_id", live = true))
            ListKind.MARQUES -> listOf(("produit" to "produits") to by("products", "marque_id", live = true))
            ListKind.CHARGE_TYPES -> listOf(("charge" to "charges") to by("charges", "type_id"))
            ListKind.CHARGE_SUBTYPES -> listOf(("charge" to "charges") to by("charges", "subtype_id"))
            ListKind.PERTE_TYPES -> listOf(("perte" to "pertes") to by("pertes", "type_id"))
            ListKind.SECTEURS -> listOf(
                ("client" to "clients") to by("clients", "secteur_id", live = true),
                ("tournée" to "tournées") to by("tournee_secteurs", "secteur_id"),
            )
        }
    }

    private fun inUse(kind: ListKind, entry: ListEntry): String =
        "Impossible de supprimer ${article(kind).lowercase()} « ${entry.name} » : ${if (kind.feminine) "elle" else "il"} est " +
            "${if (kind.feminine) "utilisée" else "utilisé"} par ${entry.usages.joinToString(" et ")}."

    private fun gone(kind: ListKind): String =
        "${article(kind)} n'existe plus : ${if (kind.feminine) "elle a" else "il a"} peut-être été ${if (kind.feminine) "supprimée" else "supprimé"}."

    // ── Helpers ──

    /** Runs [block] in Room's transaction, so every screen watching these tables is told of the change. */
    private fun <T> inTransaction(block: () -> T): T = db.runInTransaction<T> { block() }

    private fun count(query: String): Int = sql.query(query).use { it.moveToFirst(); it.getInt(0) }

    private fun groupCount(query: String): Map<Long, Long> = sql.query(query).use { c ->
        buildMap { while (c.moveToNext()) put(c.getLong(0), c.getLong(1)) }
    }

    private fun nowMillis(): Long = System.currentTimeMillis()

    companion object {
        const val BUILT_IN = "Les types de l'application ne peuvent pas être supprimés. Vous pouvez les renommer."

        /** One space between words, none around. */
        fun clean(name: String): String = name.trim().replace(Regex("\\s+"), " ")

        /** « La catégorie », « Le type de perte », « Le secteur ». */
        fun article(kind: ListKind): String = (if (kind.feminine) "La " else "Le ") + kind.singular
    }
}
