package com.distrigo.app.data.local.paging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Mouvements list's SQL, checked without a database — as for Achats and Ventes: an unset filter
 * adding no clause, a keyset that neither skips nor repeats the row at a page boundary, and the totals
 * and count seeing exactly the filters the pages see.
 */
class MovementListSqlTest {

    private fun whereOf(query: MovementListQuery) = MovementListSql.where(query)

    @Test
    fun `nothing set means no WHERE at all`() {
        val (where, args) = whereOf(MovementListQuery())
        assertEquals("", where)
        assertTrue(args.isEmpty())
    }

    @Test
    fun `a product alone is one clause`() {
        val (where, args) = whereOf(MovementListQuery(productId = 12))
        assertEquals(" WHERE m.product_id = ?", where)
        assertEquals(listOf<Any>(12), args)
    }

    @Test
    fun `each set filter adds exactly its own clause`() {
        val (where, args) = whereOf(
            MovementListQuery(
                productId = 12, createdFrom = "2026-01-01T00:00:00Z", createdBefore = "2026-02-01T00:00:00Z",
                direction = "sortie", emplacement = "depot", types = listOf("vente", "perte"),
            )
        )
        assertEquals(
            " WHERE m.product_id = ? AND m.created_at >= ? AND m.created_at < ? AND m.direction = ?" +
                " AND m.emplacement = ? AND m.type IN (?, ?)",
            where,
        )
        assertEquals(
            listOf<Any>(12, "2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z", "sortie", "depot", "vente", "perte"),
            args,
        )
        assertFalse("an unset filter leaked in", where.contains("IS NULL"))
    }

    @Test
    fun `a party side is a fixed clause on the document types`() {
        assertEquals(
            " WHERE m.source_type IN ('vente', 'retour_client')",
            whereOf(MovementListQuery(party = "client")).first,
        )
        assertEquals(
            " WHERE m.source_type IN ('purchase_order', 'retour_fournisseur')",
            whereOf(MovementListQuery(party = "fournisseur")).first,
        )
    }

    @Test
    fun `one party is looked up on each kind of document`() {
        val (where, args) = whereOf(MovementListQuery(partyId = 5))
        listOf("ventes v", "retour_client r", "purchase_orders o", "retour_fournisseur f").forEach {
            assertTrue(it, where.contains("EXISTS (SELECT 1 FROM $it WHERE"))
        }
        assertEquals(listOf<Any>(5, 5, 5, 5), args)
    }

    @Test
    fun `the next page starts strictly after the last row, ties broken by id`() {
        val q = MovementListSql.page(MovementListQuery(productId = 1), MovementCursor("2026-05-01T10:00:00Z", 42), 20)
        assertTrue(q.sql, q.sql.contains("m.product_id = ? AND (m.created_at < ? OR (m.created_at = ? AND m.id < ?))"))
        assertTrue(q.sql, q.sql.endsWith("ORDER BY m.created_at DESC, m.id DESC LIMIT ?"))
        assertEquals(5, q.argCount)
    }

    @Test
    fun `the first page has no cursor at all`() {
        val sql = MovementListSql.page(MovementListQuery(productId = 1), after = null, limit = 60).sql
        assertFalse(sql, sql.contains("m.created_at < ?"))
        assertTrue(sql, sql.endsWith("ORDER BY m.created_at DESC, m.id DESC LIMIT ?"))
    }

    @Test
    fun `totals and count see the same filters as the pages`() {
        val query = MovementListQuery(productId = 3, direction = "entree", types = listOf("achat"))
        val where = whereOf(query).first
        val totals = MovementListSql.totals(query).sql
        val count = MovementListSql.count(query).sql
        assertTrue(totals, totals.startsWith("SELECT COUNT(*) AS count, "))
        assertTrue(totals, totals.endsWith("FROM stock_movements m$where"))
        assertEquals("SELECT COUNT(*) FROM stock_movements m$where", count)
    }
}
