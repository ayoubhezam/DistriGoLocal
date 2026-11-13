package com.distrigo.app.data.local.paging

import androidx.sqlite.db.SimpleSQLiteQuery
import com.distrigo.app.data.model.Amount

/** Where an account stands, as the solde cell reads it (see Amount.owing). */
enum class BalanceBand { OWING, SETTLED, ADVANCE }

/**
 * What a client list or picker is asked to show, in database terms (PERF-01, audit 2026-11-09). The Clients list, the
 * client pickers and « Ajouter des clients » differ only in which of these they set. Null, blank or false is "any".
 */
data class ClientListQuery(
    val search          : String       = "",
    /** The search also reads the commune and the wilaya, not the phone: « Ajouter des clients » to a tournée. */
    val searchPlaces    : Boolean      = false,
    val type            : String?      = null,
    val wilaya          : String?      = null,
    val commune         : String?      = null,
    val secteur         : String?      = null,
    val balance         : BalanceBand? = null,
    val withoutQuartier : Boolean      = false,
    /** Only the clients not in this quartier: what « Ajouter des clients » can add to it. */
    val outsideQuartier : Int?         = null,
    /** Clients to leave out: those a tournée already has. */
    val excludeIds      : List<Int>    = emptyList(),
    /** Ordered by secteur, « Sans secteur » last, for the pickers' folds; newest first otherwise and within each. */
    val bySecteur       : Boolean      = false,
)

/** A secteur's fold in a client picker: its name (null for « Sans secteur ») and how many clients match in it. */
data class SecteurCount(val secteur: String?, val count: Int)

/** What the head of a client or supplier list shows: how many there are, and how many owe how much. */
data class PartySummary(val total: Int = 0, val owingCount: Int = 0, val owingTotal: Double = 0.0, val withoutQuartier: Int = 0)

/** What a supplier list or picker is asked to show. */
data class SupplierListQuery(
    val search  : String       = "",
    val wilaya  : String?      = null,
    val commune : String?      = null,
    val balance : BalanceBand? = null,
)

/**
 * The SQL behind the client lists, built per request as [ProductListSql] is: a filter not set adds no clause.
 *
 * The search is the one every client search used: every word in the name or the phone. SQLite's LIKE ignores the case
 * of ASCII letters only, where Kotlin's also ignored it on accented ones, as for products; Arabic has no case.
 */
internal object ClientListSql {

    /** A client's fold: its secteur, or null when it has none. */
    private const val SECTEUR = "(CASE WHEN TRIM(IFNULL(c.secteur_name, '')) = '' THEN NULL ELSE c.secteur_name END)"

    private fun order(query: ClientListQuery) =
        if (query.bySecteur) " ORDER BY $SECTEUR IS NULL, $SECTEUR, c.id DESC" else " ORDER BY c.id DESC"

    fun page(query: ClientListQuery, offset: Int, limit: Int): SimpleSQLiteQuery {
        val (where, args) = where(query)
        return SimpleSQLiteQuery("SELECT c.* FROM clients c$where${order(query)} LIMIT ? OFFSET ?", (args + limit + offset).toTypedArray())
    }

    fun count(query: ClientListQuery): SimpleSQLiteQuery {
        val (where, args) = where(query)
        return SimpleSQLiteQuery("SELECT COUNT(*) FROM clients c$where", args.toTypedArray())
    }

    /** The folds, in the order [page] lists their clients when [ClientListQuery.bySecteur]. */
    fun secteurCounts(query: ClientListQuery): SimpleSQLiteQuery {
        val (where, args) = where(query)
        return SimpleSQLiteQuery(
            "SELECT $SECTEUR AS secteur, COUNT(*) AS count FROM clients c$where GROUP BY $SECTEUR ORDER BY $SECTEUR IS NULL, $SECTEUR",
            args.toTypedArray(),
        )
    }

    /** The ids of the clients [query] finds in one fold ([secteur] null: « Sans secteur »), for « Tout sélectionner ». */
    fun idsInSecteur(query: ClientListQuery, secteur: String?): SimpleSQLiteQuery {
        val (where, args) = where(query)
        return if (secteur == null) SimpleSQLiteQuery("SELECT c.id FROM clients c$where AND $SECTEUR IS NULL", args.toTypedArray())
        else SimpleSQLiteQuery("SELECT c.id FROM clients c$where AND $SECTEUR = ?", (args + secteur).toTypedArray())
    }

    /** All of them, whatever is searched or filtered: the debt banner and the empty state. */
    fun summary(query: ClientListQuery): SimpleSQLiteQuery {
        val (where, args) = where(ClientListQuery(outsideQuartier = query.outsideQuartier, excludeIds = query.excludeIds))
        return SimpleSQLiteQuery(
            "SELECT COUNT(*) AS total, " +
                "COALESCE(SUM(c.balance >= ${Amount.HALF_CENTIME}), 0) AS owingCount, " +
                "COALESCE(SUM(CASE WHEN c.balance >= ${Amount.HALF_CENTIME} THEN c.balance ELSE 0 END), 0) AS owingTotal, " +
                "COALESCE(SUM(c.quartier_id IS NULL), 0) AS withoutQuartier FROM clients c$where",
            args.toTypedArray(),
        )
    }

    /** The places the clients have, for the filter sheet: [column] is wilaya_name, commune_name or secteur_name. */
    fun places(query: ClientListQuery, column: String, inWilaya: String? = null): SimpleSQLiteQuery {
        require(column in setOf("wilaya_name", "commune_name", "secteur_name"))
        val (where, args) = where(ClientListQuery(outsideQuartier = query.outsideQuartier, wilaya = inWilaya))
        return SimpleSQLiteQuery("SELECT DISTINCT c.$column FROM clients c$where", args.toTypedArray())
    }

    internal fun where(query: ClientListQuery): Pair<String, List<Any>> {
        val clauses = mutableListOf("c.deleted_at IS NULL")
        val args = mutableListOf<Any>()
        PartySql.searchTokens(query.search).forEach { token ->
            val pattern = PartySql.pattern(token)
            if (query.searchPlaces) {
                clauses += "(c.name LIKE ? ESCAPE '\\' OR IFNULL(c.commune_name, '') LIKE ? ESCAPE '\\' OR IFNULL(c.wilaya_name, '') LIKE ? ESCAPE '\\')"
                args.addAll(listOf(pattern, pattern, pattern))
            } else {
                clauses += "(c.name LIKE ? ESCAPE '\\' OR IFNULL(c.phone, '') LIKE ? ESCAPE '\\')"
                args.addAll(listOf(pattern, pattern))
            }
        }
        query.type?.let { clauses += "c.customer_type = ?"; args += it }
        PartySql.samePlace("c.wilaya_name", query.wilaya, clauses, args)
        PartySql.samePlace("c.commune_name", query.commune, clauses, args)
        PartySql.samePlace("c.secteur_name", query.secteur, clauses, args)
        query.balance?.let { clauses += PartySql.band("c.balance", it) }
        if (query.withoutQuartier) clauses += "c.quartier_id IS NULL"
        query.outsideQuartier?.let { clauses += "(c.quartier_id IS NULL OR c.quartier_id != ?)"; args += it }
        if (query.excludeIds.isNotEmpty()) {
            clauses += "c.id NOT IN (${query.excludeIds.joinToString(",") { "?" }})"
            args.addAll(query.excludeIds)
        }
        return " WHERE " + clauses.joinToString(" AND ") to args
    }
}

/** The SQL behind the supplier lists, as [ClientListSql]: by name, as they always were. */
internal object SupplierListSql {

    fun page(query: SupplierListQuery, offset: Int, limit: Int): SimpleSQLiteQuery {
        val (where, args) = where(query)
        return SimpleSQLiteQuery("SELECT s.* FROM suppliers s$where ORDER BY s.name ASC, s.id ASC LIMIT ? OFFSET ?", (args + limit + offset).toTypedArray())
    }

    fun count(query: SupplierListQuery): SimpleSQLiteQuery {
        val (where, args) = where(query)
        return SimpleSQLiteQuery("SELECT COUNT(*) FROM suppliers s$where", args.toTypedArray())
    }

    fun summary(): SimpleSQLiteQuery = SimpleSQLiteQuery(
        "SELECT COUNT(*) AS total, " +
            "COALESCE(SUM(s.balance >= ${Amount.HALF_CENTIME}), 0) AS owingCount, " +
            "COALESCE(SUM(CASE WHEN s.balance >= ${Amount.HALF_CENTIME} THEN s.balance ELSE 0 END), 0) AS owingTotal, " +
            "0 AS withoutQuartier FROM suppliers s WHERE s.deleted_at IS NULL"
    )

    fun places(column: String, inWilaya: String? = null): SimpleSQLiteQuery {
        require(column in setOf("wilaya_name", "commune_name"))
        val (where, args) = where(SupplierListQuery(wilaya = inWilaya))
        return SimpleSQLiteQuery("SELECT DISTINCT s.$column FROM suppliers s$where", args.toTypedArray())
    }

    internal fun where(query: SupplierListQuery): Pair<String, List<Any>> {
        val clauses = mutableListOf("s.deleted_at IS NULL")
        val args = mutableListOf<Any>()
        PartySql.searchTokens(query.search).forEach { token ->
            val pattern = PartySql.pattern(token)
            clauses += "(s.name LIKE ? ESCAPE '\\' OR IFNULL(s.phone, '') LIKE ? ESCAPE '\\')"
            args.addAll(listOf(pattern, pattern))
        }
        PartySql.samePlace("s.wilaya_name", query.wilaya, clauses, args)
        PartySql.samePlace("s.commune_name", query.commune, clauses, args)
        query.balance?.let { clauses += PartySql.band("s.balance", it) }
        return " WHERE " + clauses.joinToString(" AND ") to args
    }
}

internal object PartySql {
    fun searchTokens(search: String): List<String> = search.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    fun pattern(token: String): String = "%" + token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    /** A place, without case or edge spaces, as the sheet compared it. */
    fun samePlace(column: String, wanted: String?, clauses: MutableList<String>, args: MutableList<Any>) {
        wanted ?: return
        clauses += "LOWER(TRIM($column)) = LOWER(TRIM(?))"
        args += wanted
    }

    fun band(column: String, band: BalanceBand): String = when (band) {
        BalanceBand.OWING   -> "$column >= ${Amount.HALF_CENTIME}"
        BalanceBand.ADVANCE -> "$column <= -${Amount.HALF_CENTIME}"
        BalanceBand.SETTLED -> "($column < ${Amount.HALF_CENTIME} AND $column > -${Amount.HALF_CENTIME})"
    }
}
