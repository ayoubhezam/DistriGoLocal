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

/** A secteur as the Secteurs screen lists it: its commune, and how many quartiers and live clients it has. */
data class SecteurSummary(
    val id: Long,
    val name: String,
    val commune: String,
    val wilaya: String?,
    val quartiers: Int,
    val clients: Int,
)

/**
 * One secteur as its form edits it, with what uses it: while anything does, its commune stays as it is
 * (owner, 2026-10-24), so a client's secteur is always in the client's commune.
 */
data class SecteurDetail(
    val id: Long,
    val name: String,
    val commune: String,
    val wilaya: String?,
    val description: String,
    val quartiers: Int,
    val clients: Int,
    val tournees: Int,
) {
    /** « 2 quartiers et 14 clients »; null when nothing uses the secteur. */
    val usedBy: String?
        get() = listOfNotNull(
            quartiers.takeIf { it > 0 }?.let { Usage("quartier", "quartiers", it.toLong()) },
            clients.takeIf { it > 0 }?.let { Usage("client", "clients", it.toLong()) },
            tournees.takeIf { it > 0 }?.let { Usage("tournée", "tournées", it.toLong()) },
        ).takeIf { it.isNotEmpty() }?.joinToString(" et ")
}

/** A quartier as its secteur lists it: its name and how many live clients it has. */
data class QuartierSummary(val id: Long, val name: String, val clients: Int)

/** One quartier as its form edits it: its name, its secteur (fixed) and how many live clients it has. */
data class QuartierDetail(
    val id: Long,
    val name: String,
    val secteurId: Long,
    val secteurName: String,
    val commune: String,
    val clients: Int,
)

/** Where a client created from quartier [quartierId] goes: the quartier, its secteur, the secteur's wilaya and commune. */
data class ClientPlacement(
    val quartierId: Long,
    val quartierName: String,
    val secteurId: Long,
    val secteurName: String,
    val wilaya: String?,
    val commune: String,
)

/** A client as a quartier lists it. */
data class QuartierClient(val id: Int, val name: String, val phone: String?, val address: String?, val imageUri: String?)

/** Clients put in a quartier: done, with how many came from another quartier; or refused, nothing changed. */
sealed class AssignOutcome {
    data class Done(val added: Int, val moved: Int) : AssignOutcome()
    data class Refused(val reason: String) : AssignOutcome()
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
 * Only an entry nothing live uses: no live product, client, charge, perte, tournée or quartier. A built-in type of charge or
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

    /** Every secteur, by name, with its counts. Blocks: off the main thread. */
    fun secteurs(): List<SecteurSummary> {
        val quartiers = groupCount("SELECT secteur_id, COUNT(*) FROM quartiers GROUP BY secteur_id")
        val clients = groupCount("SELECT secteur_id, COUNT(*) FROM clients WHERE secteur_id IS NOT NULL AND deleted_at IS NULL GROUP BY secteur_id")
        val rows = sql.query("SELECT id, nom, commune_name, wilaya_name FROM secteurs").use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    add(
                        SecteurSummary(
                            id = id,
                            name = c.getString(1).orEmpty(),
                            commune = c.getString(2).orEmpty(),
                            wilaya = if (c.isNull(3)) null else c.getString(3),
                            quartiers = quartiers[id]?.toInt() ?: 0,
                            clients = clients[id]?.toInt() ?: 0,
                        )
                    )
                }
            }
        }
        val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.SECONDARY }
        return rows.sortedWith(compareBy(collator) { it: SecteurSummary -> it.name.trim() })
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
        /** A secteur's description. */
        description: String? = null,
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
                ListKind.SECTEURS -> {
                    put("commune_name", commune!!.trim()); put("wilaya_name", wilaya?.trim()?.ifEmpty { null })
                    put("description", description?.trim()?.ifEmpty { null })
                }
            }
        }
        sql.insert(kind.table, SQLiteDatabase.CONFLICT_ABORT, values)
        ListOutcome.Done
    }

    /** Secteur [id] with what uses it; null when it no longer exists. Blocks: off the main thread. */
    fun secteur(id: Long): SecteurDetail? {
        fun n(query: String) = sql.query(SimpleSQLiteQuery(query, arrayOf<Any>(id))).use { it.moveToFirst(); it.getInt(0) }
        return sql.query(SimpleSQLiteQuery("SELECT nom, commune_name, wilaya_name, description FROM secteurs WHERE id = ?", arrayOf<Any>(id))).use { c ->
            if (!c.moveToFirst()) null
            else SecteurDetail(
                id = id,
                name = c.getString(0).orEmpty(),
                commune = c.getString(1).orEmpty(),
                wilaya = if (c.isNull(2)) null else c.getString(2),
                description = if (c.isNull(3)) "" else c.getString(3),
                quartiers = n("SELECT COUNT(*) FROM quartiers WHERE secteur_id = ?"),
                clients = n("SELECT COUNT(*) FROM clients WHERE secteur_id = ? AND deleted_at IS NULL"),
                tournees = n("SELECT COUNT(*) FROM tournee_secteurs WHERE secteur_id = ?"),
            )
        }
    }

    /** Secteur [secteurId]'s quartiers, by name, each with its live clients. Blocks: off the main thread. */
    fun quartiers(secteurId: Long): List<QuartierSummary> {
        val clients = groupCount("SELECT quartier_id, COUNT(*) FROM clients WHERE quartier_id IS NOT NULL AND deleted_at IS NULL GROUP BY quartier_id")
        val rows = sql.query(SimpleSQLiteQuery("SELECT id, nom FROM quartiers WHERE secteur_id = ?", arrayOf<Any>(secteurId))).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    add(QuartierSummary(id, c.getString(1).orEmpty(), clients[id]?.toInt() ?: 0))
                }
            }
        }
        val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.SECONDARY }
        return rows.sortedWith(compareBy(collator) { it: QuartierSummary -> it.name.trim() })
    }

    /** The place of a client created from quartier [quartierId]; null when it no longer exists. Blocks: off the main thread. */
    fun placement(quartierId: Long): ClientPlacement? = sql.query(
        SimpleSQLiteQuery(
            "SELECT q.nom, q.secteur_id, s.nom, s.wilaya_name, s.commune_name FROM quartiers q JOIN secteurs s ON s.id = q.secteur_id WHERE q.id = ?",
            arrayOf<Any>(quartierId),
        )
    ).use { c ->
        if (!c.moveToFirst()) null
        else ClientPlacement(
            quartierId, c.getString(0).orEmpty(), c.getLong(1), c.getString(2).orEmpty(),
            if (c.isNull(3)) null else c.getString(3), c.getString(4).orEmpty(),
        )
    }

    /** Quartier [id] with its secteur; null when it no longer exists. Blocks: off the main thread. */
    fun quartier(id: Long): QuartierDetail? = sql.query(
        SimpleSQLiteQuery(
            "SELECT q.nom, q.secteur_id, s.nom, s.commune_name, " +
                "(SELECT COUNT(*) FROM clients c WHERE c.quartier_id = q.id AND c.deleted_at IS NULL) " +
                "FROM quartiers q JOIN secteurs s ON s.id = q.secteur_id WHERE q.id = ?",
            arrayOf<Any>(id),
        )
    ).use { c ->
        if (!c.moveToFirst()) null
        else QuartierDetail(id, c.getString(0).orEmpty(), c.getLong(1), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getInt(4))
    }

    /** The live clients of quartier [quartierId], by name. Blocks: off the main thread. */
    fun clientsIn(quartierId: Long): List<QuartierClient> {
        val rows = sql.query(
            SimpleSQLiteQuery("SELECT id, name, phone, address, image_uri FROM clients WHERE quartier_id = ? AND deleted_at IS NULL", arrayOf<Any>(quartierId))
        ).use { c ->
            fun str(i: Int) = if (c.isNull(i)) null else c.getString(i)
            buildList { while (c.moveToNext()) add(QuartierClient(c.getInt(0), c.getString(1).orEmpty(), str(2), str(3), str(4))) }
        }
        val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.SECONDARY }
        return rows.sortedWith(compareBy(collator) { it: QuartierClient -> it.name.trim() })
    }

    /**
     * Adds quartier [name] to secteur [secteurId]: a name no other quartier of the secteur has, case and spaces aside
     * (owner's prototype v5). Its secteur is fixed from then on.
     */
    fun addQuartier(secteurId: Long, name: String): ListOutcome = inTransaction {
        val clean = clean(name)
        if (clean.isEmpty()) return@inTransaction ListOutcome.Refused("Saisissez le nom du quartier.")
        val secteur = secteur(secteurId) ?: return@inTransaction ListOutcome.Refused(gone(ListKind.SECTEURS))
        quartierTaken(secteurId, clean, exceptId = null, secteur.name)?.let { return@inTransaction ListOutcome.Refused(it) }
        val now = java.time.Instant.now()
        sql.insert("quartiers", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
            put("nom", clean)
            put("secteur_id", secteurId)
            put("uuid", newRowUuid())
            put("created_at", now.toString())
            put("updated_at", now.toEpochMilli())
            put("version", 1)
        })
        ListOutcome.Done
    }

    /** Renames quartier [id], and the copy of its name on its clients (as a secteur's, see the class). */
    fun renameQuartier(id: Long, name: String): ListOutcome = inTransaction {
        val clean = clean(name)
        if (clean.isEmpty()) return@inTransaction ListOutcome.Refused("Saisissez le nom du quartier.")
        val current = quartier(id) ?: return@inTransaction ListOutcome.Refused("Le quartier n'existe plus : il a peut-être été supprimé.")
        if (current.name == clean) return@inTransaction ListOutcome.Done
        quartierTaken(current.secteurId, clean, exceptId = id, current.secteurName)?.let { return@inTransaction ListOutcome.Refused(it) }
        sql.execSQL("UPDATE quartiers SET nom = ? WHERE id = ?", arrayOf<Any>(clean, id))
        sql.execSQL("UPDATE clients SET quartier_name = ? WHERE quartier_id = ? AND quartier_name IS NOT ?", arrayOf<Any>(clean, id, clean))
        ListOutcome.Done
    }

    /**
     * Puts clients [ids] in quartier [quartierId], in one transaction: the quartier, its secteur, and the secteur's
     * wilaya and commune, so a client's place always agrees with its quartier (owner's prototype v5). A client that was
     * in another quartier is moved out of it.
     */
    fun assignClientsToQuartier(quartierId: Long, ids: Collection<Int>): AssignOutcome = db.runInTransaction<AssignOutcome> {
        val quartier = quartier(quartierId) ?: return@runInTransaction AssignOutcome.Refused("Le quartier n'existe plus : il a peut-être été supprimé.")
        val secteur = secteur(quartier.secteurId) ?: return@runInTransaction AssignOutcome.Refused(gone(ListKind.SECTEURS))
        var added = 0
        var moved = 0
        for (id in ids.distinct()) {
            val from = sql.query(
                SimpleSQLiteQuery("SELECT quartier_id FROM clients WHERE id = ? AND deleted_at IS NULL", arrayOf<Any>(id))
            ).use { c -> if (!c.moveToFirst()) null else Pair(true, if (c.isNull(0)) null else c.getLong(0)) } ?: continue
            if (from.second == quartierId) continue
            if (from.second != null) moved++
            sql.execSQL(
                "UPDATE clients SET quartier_id = ?, quartier_name = ?, secteur_id = ?, secteur_name = ?, wilaya_name = ?, commune_name = ? WHERE id = ?",
                arrayOf<Any?>(quartierId, quartier.name, secteur.id, secteur.name, secteur.wilaya, secteur.commune, id),
            )
            added++
        }
        AssignOutcome.Done(added, moved)
    }

    /** Deletes quartier [id] for good, unless a live client is in it. */
    fun deleteQuartier(id: Long): ListOutcome = inTransaction {
        val current = quartier(id) ?: return@inTransaction ListOutcome.Refused("Le quartier n'existe plus : il a peut-être été supprimé.")
        if (current.clients > 0) {
            return@inTransaction ListOutcome.Refused(
                "Impossible de supprimer le quartier « ${current.name} » : il est utilisé par ${Usage("client", "clients", current.clients.toLong())}."
            )
        }
        sql.execSQL("DELETE FROM quartiers WHERE id = ?", arrayOf<Any>(id))
        ListOutcome.Done
    }

    private fun quartierTaken(secteurId: Long, name: String, exceptId: Long?, secteurName: String): String? {
        val except = if (exceptId != null) " AND id != $exceptId" else ""
        val exists = sql.query(
            SimpleSQLiteQuery("SELECT 1 FROM quartiers WHERE secteur_id = ? AND LOWER(TRIM(nom)) = LOWER(?)$except", arrayOf<Any>(secteurId, name))
        ).use { it.moveToFirst() }
        return if (exists) "Ce quartier existe déjà dans le secteur « $secteurName »." else null
    }

    /**
     * Saves secteur [id]'s form: its name (and every copy, as [rename] does), its commune — refused while anything
     * uses the secteur, see [SecteurDetail] — and its description.
     */
    fun updateSecteur(id: Long, name: String, commune: String, wilaya: String?, description: String): ListOutcome = inTransaction {
        val clean = clean(name)
        if (clean.isEmpty()) return@inTransaction ListOutcome.Refused("Saisissez le nom du secteur.")
        if (commune.isBlank()) return@inTransaction ListOutcome.Refused("Sélectionnez une commune.")
        val current = secteur(id) ?: return@inTransaction ListOutcome.Refused(gone(ListKind.SECTEURS))
        val moved = !current.commune.trim().equals(commune.trim(), ignoreCase = true) ||
            !current.wilaya.orEmpty().trim().equals(wilaya.orEmpty().trim(), ignoreCase = true)
        if (moved) current.usedBy?.let { return@inTransaction ListOutcome.Refused("La commune ne peut pas être modifiée : le secteur est utilisé par $it.") }
        taken(ListKind.SECTEURS, clean, exceptId = id, parentId = null, commune = commune)?.let { return@inTransaction ListOutcome.Refused(it) }

        sql.execSQL(
            "UPDATE secteurs SET nom = ?, commune_name = ?, wilaya_name = ?, description = ? WHERE id = ?",
            arrayOf<Any?>(clean, commune.trim(), wilaya?.trim()?.ifEmpty { null }, description.trim().ifEmpty { null }, id),
        )
        if (current.name != clean) {
            for ((table, column, idColumn) in copiesOf(ListKind.SECTEURS)) {
                sql.execSQL("UPDATE $table SET $column = ? WHERE $idColumn = ? AND $column IS NOT ?", arrayOf<Any>(clean, id, clean))
            }
        }
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
                ("quartier" to "quartiers") to by("quartiers", "secteur_id"),
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
