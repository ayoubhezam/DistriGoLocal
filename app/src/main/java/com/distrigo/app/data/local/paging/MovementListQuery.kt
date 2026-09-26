package com.distrigo.app.data.local.paging

import androidx.sqlite.db.SimpleSQLiteQuery

/**
 * What a Mouvements list is asked to show, in database terms — one product's ledger, narrowed as the
 * filter sheet says.
 *
 * The date bounds are instants, `[from, before)`, converted from the local days picked — see
 * `BusinessDates.dayRangeBounds`. Empty [types] means every kind. [party] keeps the movements made with
 * a client (or a supplier) whoever they were; [partyId] narrows to one of them. Null means "any".
 */
data class MovementListQuery(
    val productId     : Int?         = null,
    val createdFrom   : String?      = null,
    val createdBefore : String?      = null,
    val direction     : String?      = null,
    val emplacement   : String?      = null,
    val types         : List<String> = emptyList(),
    val party         : String?      = null,
    val partyId       : Int?         = null,
)

/** Where a page of movements starts: a movement's `created_at`, with its `id` to break ties. */
data class MovementCursor(val createdAt: String, val id: Int)

/**
 * The SQL behind the Mouvements list, built per request, as for Achats and Ventes: a filter that is not
 * set adds no clause, where the fixed query it replaces wrote every one as `(:x IS NULL OR …)`. With
 * the product set, the list is a walk down `stock_movements(product_id, created_at)`, newest first.
 */
internal object MovementListSql {

    /** One page of movements older than [after] (or from the top), at most [limit] of them. */
    fun page(query: MovementListQuery, after: MovementCursor?, limit: Int): SimpleSQLiteQuery {
        val (where, args) = where(query, after?.let {
            "(m.created_at < ? OR (m.created_at = ? AND m.id < ?))" to listOf<Any>(it.createdAt, it.createdAt, it.id)
        })
        return SimpleSQLiteQuery(
            "SELECT m.* FROM stock_movements m$where ORDER BY m.created_at DESC, m.id DESC LIMIT ?",
            (args + limit).toTypedArray(),
        )
    }

    /** Every movement that matches, newest first — for callers that want the whole list, not a page. */
    fun all(query: MovementListQuery): SimpleSQLiteQuery {
        val (where, args) = where(query)
        return SimpleSQLiteQuery("SELECT m.* FROM stock_movements m$where ORDER BY m.created_at DESC, m.id DESC", args.toTypedArray())
    }

    /** The screen's three figures in one pass: how many movements, what came in, what went out. */
    fun totals(query: MovementListQuery): SimpleSQLiteQuery {
        val (where, args) = where(query)
        return SimpleSQLiteQuery(
            "SELECT COUNT(*) AS count, " +
                "COALESCE(SUM(CASE WHEN m.direction = 'entree' THEN m.quantity END), 0.0) AS entrees, " +
                "COALESCE(SUM(CASE WHEN m.direction = 'sortie' THEN m.quantity END), 0.0) AS sorties " +
                "FROM stock_movements m$where",
            args.toTypedArray(),
        )
    }

    /** How many movements match — the filter sheet's own button, for the filters it is editing. */
    fun count(query: MovementListQuery): SimpleSQLiteQuery {
        val (where, args) = where(query)
        return SimpleSQLiteQuery("SELECT COUNT(*) FROM stock_movements m$where", args.toTypedArray())
    }

    internal fun where(query: MovementListQuery, extra: Pair<String, List<Any>>? = null): Pair<String, List<Any>> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()

        query.productId?.let { clauses += "m.product_id = ?"; args += it }
        query.createdFrom?.let { clauses += "m.created_at >= ?"; args += it }
        query.createdBefore?.let { clauses += "m.created_at < ?"; args += it }
        query.direction?.let { clauses += "m.direction = ?"; args += it }
        query.emplacement?.let { clauses += "m.emplacement = ?"; args += it }
        if (query.types.isNotEmpty()) {
            clauses += "m.type IN (${query.types.joinToString(", ") { "?" }})"
            args.addAll(query.types)
        }
        // Whom a movement was with is its document's client or supplier: the document types that
        // belong to each side, and — for one party — the document's own client_id or supplier_id.
        when (query.party) {
            "client"      -> clauses += "m.source_type IN ('vente', 'retour_client')"
            "fournisseur" -> clauses += "m.source_type IN ('purchase_order', 'retour_fournisseur')"
        }
        query.partyId?.let { id ->
            clauses += "((m.source_type = 'vente' AND EXISTS (SELECT 1 FROM ventes v WHERE v.id = m.source_id AND v.client_id = ?))" +
                " OR (m.source_type = 'retour_client' AND EXISTS (SELECT 1 FROM retour_client r WHERE r.id = m.source_id AND r.client_id = ?))" +
                " OR (m.source_type = 'purchase_order' AND EXISTS (SELECT 1 FROM purchase_orders o WHERE o.id = m.source_id AND o.supplier_id = ?))" +
                " OR (m.source_type = 'retour_fournisseur' AND EXISTS (SELECT 1 FROM retour_fournisseur f WHERE f.id = m.source_id AND f.supplier_id = ?)))"
            args.addAll(listOf(id, id, id, id))
        }
        extra?.let { (clause, values) -> clauses += clause; args.addAll(values) }

        return (if (clauses.isEmpty()) "" else " WHERE " + clauses.joinToString(" AND ")) to args
    }
}
