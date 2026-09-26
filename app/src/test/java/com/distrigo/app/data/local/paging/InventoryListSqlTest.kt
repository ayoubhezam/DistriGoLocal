package com.distrigo.app.data.local.paging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The inventory history's and a session's detail's SQL, checked without a database: no clause for a
 * search not typed, the number searched as the screen prints it, keysets that neither skip nor repeat a
 * row at a page boundary, and totals summed over the page's sessions only.
 */
class InventoryListSqlTest {

    @Test
    fun `no search means no WHERE at all`() {
        val (where, args) = InventoryListSql.sessionWhere(InventorySessionListQuery("  "))
        assertEquals("", where)
        assertTrue(args.isEmpty())
    }

    @Test
    fun `the search matches the number as inventoryNumero prints it`() {
        val (where, args) = InventoryListSql.sessionWhere(InventorySessionListQuery(" 0012 "))
        assertEquals(" WHERE ('N° ' || printf('%05d', s.id)) LIKE ? ESCAPE '\\'", where)
        assertEquals(listOf<Any>("%0012%"), args)
    }

    @Test
    fun `LIKE wildcards in a search are taken literally`() {
        val (_, args) = InventoryListSql.sessionWhere(InventorySessionListQuery("5%_"))
        assertEquals("%5\\%\\_%", args.single())
    }

    @Test
    fun `sessions are ordered and paged by the date their header shows`() {
        val q = InventoryListSql.sessionPage(InventorySessionListQuery(), InventorySessionCursor("2026-05-01T10:00:00Z", 7), 20)
        assertTrue(q.sql, q.sql.contains(
            "WHERE (COALESCE(s.completed_at, s.started_at) < ? OR (COALESCE(s.completed_at, s.started_at) = ? AND s.id < ?))"
        ))
        assertTrue(q.sql, q.sql.contains("ORDER BY COALESCE(s.completed_at, s.started_at) DESC, s.id DESC LIMIT ?) p"))
        assertTrue(q.sql, q.sql.endsWith("GROUP BY p.id ORDER BY p.sort_at DESC, p.id DESC"))
        assertEquals(4, q.argCount)
    }

    @Test
    fun `totals are summed over the page's sessions, not the whole table`() {
        val sql = InventoryListSql.sessionPage(InventorySessionListQuery(), after = null, limit = 20).sql
        // The limit is applied to the sessions first; only their lines are joined and summed.
        assertTrue(sql, sql.indexOf("LIMIT ?) p") < sql.indexOf("LEFT JOIN inventory_items i ON i.session_id = p.id"))
        assertFalse(sql, sql.contains("s.completed_at, s.started_at) < ?"))
    }

    @Test
    fun `a session's lines page newest first, strictly after the cursor`() {
        val first = InventoryListSql.itemPage(3, after = null, limit = 20)
        assertEquals("SELECT i.* FROM inventory_items i WHERE i.session_id = ? ORDER BY i.created_at DESC, i.id DESC LIMIT ?", first.sql)
        assertEquals(2, first.argCount)

        val next = InventoryListSql.itemPage(3, InventoryItemCursor("2026-05-01T10:00:00Z", 42), 20)
        assertTrue(next.sql, next.sql.contains("i.session_id = ? AND (i.created_at < ? OR (i.created_at = ? AND i.id < ?))"))
        assertEquals(5, next.argCount)
    }
}
