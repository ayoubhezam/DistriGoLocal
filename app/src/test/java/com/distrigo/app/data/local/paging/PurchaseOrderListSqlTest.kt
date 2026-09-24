package com.distrigo.app.data.local.paging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Achats list's SQL, checked without a database.
 *
 * What these pin is what would fail quietly: a filter that is not set still adding a clause (the
 * `IS NULL OR` shape this builder exists to avoid), a keyset condition that skips or repeats the row at
 * a page boundary, and a search word taken as a LIKE pattern.
 */
class PurchaseOrderListSqlTest {

    private fun whereOf(query: PurchaseOrderListQuery) = PurchaseOrderListSql.where(query)

    @Test
    fun `nothing set means no WHERE at all`() {
        val (where, args) = whereOf(PurchaseOrderListQuery())
        assertEquals("", where)
        assertTrue(args.isEmpty())
    }

    @Test
    fun `each set filter adds exactly its own clause`() {
        val (where, args) = whereOf(
            PurchaseOrderListQuery(
                receptionStatus = "received", supplierId = 7,
                createdFrom = "2026-01-01T00:00:00Z", createdBefore = "2026-02-01T00:00:00Z",
            )
        )
        assertEquals(
            " WHERE o.status = ? AND o.supplier_id = ? AND o.created_at >= ? AND o.created_at < ?",
            where,
        )
        assertEquals(listOf<Any>("received", 7, "2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z"), args)
        assertFalse("an unset filter leaked in", where.contains("IS NULL"))
    }

    @Test
    fun `payment status is a fixed clause with no argument`() {
        assertEquals(" WHERE (o.montant_paye >= o.total AND o.total > 0)", whereOf(PurchaseOrderListQuery(paymentStatus = "paye")).first)
        assertEquals(" WHERE o.montant_paye <= 0", whereOf(PurchaseOrderListQuery(paymentStatus = "impaye")).first)
        assertEquals(
            " WHERE (o.montant_paye > 0 AND o.montant_paye < o.total)",
            whereOf(PurchaseOrderListQuery(paymentStatus = "partiel")).first,
        )
    }

    @Test
    fun `every word of the search must match, each against name, id and number`() {
        val (where, args) = whereOf(PurchaseOrderListQuery(search = "  cevital   ba-6 "))
        assertEquals(2, Regex("LIKE \\? ESCAPE").findAll(where).count() / 3)
        assertEquals(listOf<Any>("%cevital%", "%cevital%", "%cevital%", "%ba-6%", "%ba-6%", "%ba-6%"), args)
    }

    @Test
    fun `LIKE wildcards in a search are taken literally`() {
        val (_, args) = whereOf(PurchaseOrderListQuery(search = "50%_x"))
        assertEquals("%50\\%\\_x%", args.first())
    }

    @Test
    fun `the next page starts strictly after the last row, ties broken by id`() {
        val sql = PurchaseOrderListSql.page(PurchaseOrderListQuery(), PurchaseOrderCursor("2026-05-01T10:00:00Z", 42), 20).sql
        assertTrue(sql, sql.contains("(o.created_at < ? OR (o.created_at = ? AND o.id < ?))"))
        assertTrue(sql, sql.endsWith("ORDER BY o.created_at DESC, o.id DESC LIMIT ?"))
    }

    @Test
    fun `the first page has no cursor at all`() {
        val sql = PurchaseOrderListSql.page(PurchaseOrderListQuery(), after = null, limit = 60).sql
        assertFalse(sql, sql.contains("o.created_at < ?"))
        assertTrue(sql, sql.endsWith("ORDER BY o.created_at DESC, o.id DESC LIMIT ?"))
    }

    @Test
    fun `the count sees the same filters as the pages`() {
        val query = PurchaseOrderListQuery(search = "x", supplierId = 3)
        val count = PurchaseOrderListSql.count(query).sql
        assertTrue(count, count.startsWith("SELECT COUNT(*) FROM purchase_orders o LEFT JOIN suppliers s"))
        assertTrue(count, count.endsWith(whereOf(query).first))
    }
}
