package com.distrigo.app.data.local.paging

import androidx.sqlite.db.SimpleSQLiteQuery

/** What the inventory history is asked to show: every session, or those whose number matches [search]. */
data class InventorySessionListQuery(val search: String = "")

/** Where a page of sessions starts: a session's date (finished, or started for a draft), with its id. */
data class InventorySessionCursor(val sortAt: String, val id: Int)

/** Where a page of a session's lines starts: a line's `created_at`, with its `id` to break ties. */
data class InventoryItemCursor(val createdAt: String, val id: Int)

/**
 * The SQL behind the inventory history and a session's detail, built per request as for the other
 * paged lists.
 *
 * A session's date is when it finished, or when it started while still a draft — the date its day
 * header shows, so the list is ordered by it too and a day's sessions cannot be split by another
 * day's. The history used to sort by the start and group by the finish.
 */
internal object InventoryListSql {

    /** A session's date, as the list sorts and groups it. */
    private const val SORT_AT = "COALESCE(s.completed_at, s.started_at)"

    /**
     * One page of sessions older than [after], each with its totals — lines counted, lines with an
     * écart, and the écarts' value — summed over that page's sessions only. The history used to
     * sum every line of every inventory each time it opened.
     */
    fun sessionPage(query: InventorySessionListQuery, after: InventorySessionCursor?, limit: Int): SimpleSQLiteQuery {
        val (where, args) = sessionWhere(query, after?.let {
            "($SORT_AT < ? OR ($SORT_AT = ? AND s.id < ?))" to listOf<Any>(it.sortAt, it.sortAt, it.id)
        })
        return SimpleSQLiteQuery(
            "SELECT p.id AS id, p.status AS status, p.started_at AS started_at, p.completed_at AS completed_at, " +
                "p.sort_at AS sort_at, COUNT(i.id) AS total_products, " +
                "COALESCE(SUM(CASE WHEN i.ecart != 0 THEN 1 ELSE 0 END), 0) AS total_ecarts, " +
                "COALESCE(SUM(ABS(i.valeur_ecart)), 0.0) AS total_value_ecarts " +
                "FROM (SELECT s.id, s.status, s.started_at, s.completed_at, $SORT_AT AS sort_at " +
                "FROM inventory_sessions s$where ORDER BY $SORT_AT DESC, s.id DESC LIMIT ?) p " +
                "LEFT JOIN inventory_items i ON i.session_id = p.id " +
                "GROUP BY p.id ORDER BY p.sort_at DESC, p.id DESC",
            (args + limit).toTypedArray(),
        )
    }

    /** One page of a session's lines, newest scanned first, as its detail always listed them. */
    fun itemPage(sessionId: Int, after: InventoryItemCursor?, limit: Int): SimpleSQLiteQuery {
        val keyset = if (after == null) "" else " AND (i.created_at < ? OR (i.created_at = ? AND i.id < ?))"
        val args = mutableListOf<Any>(sessionId)
        after?.let { args.addAll(listOf(it.createdAt, it.createdAt, it.id)) }
        args += limit
        return SimpleSQLiteQuery(
            "SELECT i.* FROM inventory_items i WHERE i.session_id = ?$keyset ORDER BY i.created_at DESC, i.id DESC LIMIT ?",
            args.toTypedArray(),
        )
    }

    internal fun sessionWhere(query: InventorySessionListQuery, extra: Pair<String, List<Any>>? = null): Pair<String, List<Any>> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()

        // The number as inventoryNumero prints it — "N° " and the id on five digits — searched as
        // one string, as the list searched it. The two must be kept alike.
        val search = query.search.trim()
        if (search.isNotEmpty()) {
            clauses += "('N° ' || printf('%05d', s.id)) LIKE ? ESCAPE '\\'"
            args += "%${escapeLike(search)}%"
        }
        extra?.let { (clause, values) -> clauses += clause; args.addAll(values) }

        return (if (clauses.isEmpty()) "" else " WHERE " + clauses.joinToString(" AND ")) to args
    }

    /** `%`, `_` and the escape itself taken literally. */
    private fun escapeLike(token: String): String =
        token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
