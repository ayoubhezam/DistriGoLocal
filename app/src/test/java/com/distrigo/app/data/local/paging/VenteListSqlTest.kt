package com.distrigo.app.data.local.paging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Ventes list's SQL, checked without a database — see PurchaseOrderListSqlTest for why these. */
class VenteListSqlTest {

    private fun whereOf(query: VenteListQuery) = VenteListSql.where(query)

    @Test
    fun `the dépôt list asks for the dépôt and nothing else`() {
        val (where, args) = whereOf(VenteListQuery())
        assertEquals(" WHERE v.source = ?", where)
        assertEquals(listOf<Any>("depot"), args)
    }

    @Test
    fun `each set filter adds its own clause, no IS NULL OR`() {
        val (where, args) = whereOf(
            VenteListQuery(status = "delivered", clientId = 4, createdFrom = "a", createdBefore = "b")
        )
        assertEquals(
            " WHERE v.source = ? AND v.status = ? AND v.client_id = ? AND v.created_at >= ? AND v.created_at < ?",
            where,
        )
        assertEquals(listOf<Any>("depot", "delivered", 4, "a", "b"), args)
        assertFalse(where.contains("IS NULL OR"))
    }

    @Test
    fun `every search word must match the client, the id or the number`() {
        val (where, args) = whereOf(VenteListQuery(search = "rachid 0001"))
        assertEquals(2, Regex("COALESCE\\(c.name, ''\\) LIKE").findAll(where).count())
        assertEquals(listOf<Any>("depot", "%rachid%", "%rachid%", "%rachid%", "%0001%", "%0001%", "%0001%"), args)
    }

    @Test
    fun `payment status is a fixed clause`() {
        assertTrue(whereOf(VenteListQuery(paymentStatus = "partiel")).first.endsWith("(v.montant_paye > 0 AND v.montant_paye < v.total)"))
    }

    @Test
    fun `the next page starts strictly after the last sale, ties broken by id`() {
        val sql = VenteListSql.page(VenteListQuery(), VenteCursor("2026-01-01T00:00:00Z", 7), 20).sql
        assertTrue(sql, sql.contains("(v.created_at < ? OR (v.created_at = ? AND v.id < ?))"))
        assertTrue(sql, sql.endsWith("ORDER BY v.created_at DESC, v.id DESC LIMIT ?"))
    }

    @Test
    fun `the count sees the same filters as the pages`() {
        val query = VenteListQuery(search = "x", clientId = 2)
        assertTrue(VenteListSql.count(query).sql.endsWith(whereOf(query).first))
    }
}
