package com.distrigo.app.data.local.paging

import androidx.sqlite.db.SimpleSQLiteQuery
import com.distrigo.app.data.local.dao.PURCHASE_LIST_SUPPLIER_NAME

/**
 * What the Achats list is asked to show: the search and the filter sheet, in database terms.
 *
 * [createdFrom] and [createdBefore] are instant bounds, `[from, before)`, already converted from the
 * local days the user picked — see `BusinessDates.dayRangeBounds`. Null means "no limit" for every
 * field, as in the filter sheet.
 */
data class PurchaseOrderListQuery(
    val search         : String  = "",
    val receptionStatus: String? = null,
    val paymentStatus  : String? = null,
    val supplierId     : Int?    = null,
    val createdFrom    : String? = null,
    val createdBefore  : String? = null,
)

/**
 * Where a page of the Achats list starts: a bon's `created_at` and `id`.
 *
 * Both, because two bons can share a `created_at` to the second and a cursor on the date alone would
 * skip the second of them at a page boundary. `id` breaks the tie and makes every position unique.
 */
data class PurchaseOrderCursor(val createdAt: String, val id: Int)

/**
 * The SQL behind the Achats list, built per request.
 *
 * Built rather than written as one `@Query` with `(:x IS NULL OR col = :x)` for every filter: SQLite
 * plans that shape once, for the case where every filter could be set, and can then use no index at
 * all. Here a filter that is not set adds no clause, so the query SQLite sees is only what was asked.
 *
 * The list is newest first by `created_at`, then `id` — the order the day headers are read in.
 */
internal object PurchaseOrderListSql {

    /**
     * The supplier's name as the list shows it: the name the bon was made under, else the supplier's
     * current one, else a placeholder — the order `getPurchaseOrders` used when it built the list in
     * Kotlin.
     */
    const val SUPPLIER_NAME = PURCHASE_LIST_SUPPLIER_NAME

    private const val FROM =
        "FROM purchase_orders o LEFT JOIN suppliers s ON s.id = o.supplier_id"

    /** One page of rows older than [after] (or from the top when null), at most [limit] of them. */
    fun page(query: PurchaseOrderListQuery, after: PurchaseOrderCursor?, limit: Int): SimpleSQLiteQuery =
        select(query, limit, cursor = after?.let { older(it) })

    /** How many bons match, for the "N bons" counter and the filter sheet's button. */
    fun count(query: PurchaseOrderListQuery): SimpleSQLiteQuery {
        val (where, args) = where(query)
        return SimpleSQLiteQuery("SELECT COUNT(*) $FROM$where", args.toTypedArray())
    }

    private fun select(
        query      : PurchaseOrderListQuery,
        limit      : Int,
        cursor     : Pair<String, List<Any>>?,
    ): SimpleSQLiteQuery {
        val (where, args) = where(query, cursor)
        return SimpleSQLiteQuery(
            "SELECT o.*, $SUPPLIER_NAME AS display_supplier_name, " +
                "(SELECT COUNT(*) FROM purchase_order_items i WHERE i.purchase_order_id = o.id) AS items_count " +
                "$FROM$where ORDER BY o.created_at DESC, o.id DESC LIMIT ?",
            (args + limit).toTypedArray(),
        )
    }

    private fun older(cursor: PurchaseOrderCursor): Pair<String, List<Any>> =
        "(o.created_at < ? OR (o.created_at = ? AND o.id < ?))" to
            listOf(cursor.createdAt, cursor.createdAt, cursor.id)

    /** The WHERE clause, with a leading space, or an empty string when nothing narrows the list. */
    internal fun where(query: PurchaseOrderListQuery, extra: Pair<String, List<Any>>? = null): Pair<String, List<Any>> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()

        // Every word of the search must match the supplier's name, the bon's number or its id —
        // the rule the list applied in Kotlin. LIKE is case-insensitive for ASCII only, which is
        // where it differs from Kotlin's ignoreCase: an accented capital does not match its
        // lower-case letter. Arabic has no case, so it is unaffected.
        searchTokens(query.search).forEach { token ->
            val pattern = "%${escapeLike(token)}%"
            clauses += "($SUPPLIER_NAME LIKE ? ESCAPE '\\' OR CAST(o.id AS TEXT) LIKE ? ESCAPE '\\' " +
                "OR o.numero LIKE ? ESCAPE '\\')"
            args.addAll(listOf(pattern, pattern, pattern))
        }
        query.receptionStatus?.let { clauses += "o.status = ?"; args += it }
        when (query.paymentStatus) {
            "paye"    -> clauses += "(o.montant_paye >= o.total AND o.total > 0)"
            "impaye"  -> clauses += "o.montant_paye <= 0"
            "partiel" -> clauses += "(o.montant_paye > 0 AND o.montant_paye < o.total)"
        }
        query.supplierId?.let { clauses += "o.supplier_id = ?"; args += it }
        query.createdFrom?.let { clauses += "o.created_at >= ?"; args += it }
        query.createdBefore?.let { clauses += "o.created_at < ?"; args += it }
        extra?.let { (clause, values) -> clauses += clause; args.addAll(values) }

        return (if (clauses.isEmpty()) "" else " WHERE " + clauses.joinToString(" AND ")) to args
    }

    /** The search split on whitespace, as the list screens split it. */
    internal fun searchTokens(search: String): List<String> =
        search.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** `%`, `_` and the escape itself taken literally, so "50%" searches for "50%". */
    private fun escapeLike(token: String): String =
        token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
