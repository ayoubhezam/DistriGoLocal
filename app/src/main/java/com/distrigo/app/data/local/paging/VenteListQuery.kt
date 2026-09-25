package com.distrigo.app.data.local.paging

import androidx.sqlite.db.SimpleSQLiteQuery
import com.distrigo.app.data.local.dao.VENTE_LIST_CLIENT_NAME

/**
 * What the Ventes list is asked to show, in database terms.
 *
 * [source] is `depot` for the Dépôt Vente list; the tournée's own sales live on its screen. The date
 * bounds are instants, `[from, before)`, converted from the local days picked — see
 * `BusinessDates.dayRangeBounds`. Null means "any" throughout.
 */
data class VenteListQuery(
    val source        : String? = "depot",
    val search        : String  = "",
    val status        : String? = null,
    val paymentStatus : String? = null,
    val clientId      : Int?    = null,
    val createdFrom   : String? = null,
    val createdBefore : String? = null,
)

/** Where a page of the Ventes list starts: a sale's `created_at`, with its `id` to break ties. */
data class VenteCursor(val createdAt: String, val id: Int)

/**
 * The SQL behind the Ventes list, built per request, as for Achats (see PurchaseOrderListSql): a filter
 * that is not set adds no clause, and the list is newest first by `created_at`, then `id` — an order
 * the `ventes(source, created_at)` index serves directly.
 */
internal object VenteListSql {

    const val CLIENT_NAME = VENTE_LIST_CLIENT_NAME

    private const val FROM =
        "FROM ventes v LEFT JOIN clients c ON c.id = v.client_id AND c.deleted_at IS NULL"

    /** One page of sales older than [after] (or from the top), at most [limit] of them. */
    fun page(query: VenteListQuery, after: VenteCursor?, limit: Int): SimpleSQLiteQuery {
        val (where, args) = where(query, after?.let {
            "(v.created_at < ? OR (v.created_at = ? AND v.id < ?))" to listOf<Any>(it.createdAt, it.createdAt, it.id)
        })
        return SimpleSQLiteQuery(
            "SELECT v.*, $CLIENT_NAME AS display_client_name, " +
                "(SELECT COUNT(*) FROM vente_items vi WHERE vi.vente_id = v.id) AS items_count " +
                "$FROM$where ORDER BY v.created_at DESC, v.id DESC LIMIT ?",
            (args + limit).toTypedArray(),
        )
    }

    /** How many sales match, for the "N ventes" counter and the filter sheet's button. */
    fun count(query: VenteListQuery): SimpleSQLiteQuery {
        val (where, args) = where(query)
        return SimpleSQLiteQuery("SELECT COUNT(*) $FROM$where", args.toTypedArray())
    }

    internal fun where(query: VenteListQuery, extra: Pair<String, List<Any>>? = null): Pair<String, List<Any>> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()

        query.source?.let { clauses += "v.source = ?"; args += it }
        // Every word must match the client's name, the sale's number or its id — the list's rule.
        searchTokens(query.search).forEach { token ->
            val pattern = "%${escapeLike(token)}%"
            clauses += "($CLIENT_NAME LIKE ? ESCAPE '\\' OR CAST(v.id AS TEXT) LIKE ? ESCAPE '\\' " +
                "OR v.numero LIKE ? ESCAPE '\\')"
            args.addAll(listOf(pattern, pattern, pattern))
        }
        query.status?.let { clauses += "v.status = ?"; args += it }
        when (query.paymentStatus) {
            "paye"    -> clauses += "(v.montant_paye >= v.total AND v.total > 0)"
            "impaye"  -> clauses += "v.montant_paye <= 0"
            "partiel" -> clauses += "(v.montant_paye > 0 AND v.montant_paye < v.total)"
        }
        query.clientId?.let { clauses += "v.client_id = ?"; args += it }
        query.createdFrom?.let { clauses += "v.created_at >= ?"; args += it }
        query.createdBefore?.let { clauses += "v.created_at < ?"; args += it }
        extra?.let { (clause, values) -> clauses += clause; args.addAll(values) }

        return (if (clauses.isEmpty()) "" else " WHERE " + clauses.joinToString(" AND ")) to args
    }

    internal fun searchTokens(search: String): List<String> =
        search.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun escapeLike(token: String): String =
        token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
