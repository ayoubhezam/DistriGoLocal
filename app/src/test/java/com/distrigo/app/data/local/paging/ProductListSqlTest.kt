package com.distrigo.app.data.local.paging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The product lists' SQL, checked without a database: deleted products always hidden, a filter that
 * is not set adding nothing, the keyset going the sort's way with ties newest first, and the stock and
 * price columns each picker asks for.
 */
class ProductListSqlTest {

    private fun whereOf(query: ProductListQuery) = ProductListSql.where(query)

    @Test
    fun `an empty query only hides deleted products`() {
        val (where, args) = whereOf(ProductListQuery())
        assertEquals(" WHERE p.deleted_at IS NULL", where)
        assertTrue(args.isEmpty())
    }

    @Test
    fun `each set filter adds its own clause and nothing else`() {
        val (where, args) = whereOf(ProductListQuery(categoryId = 2, unitType = "carton", supplierId = 9))
        assertEquals(" WHERE p.deleted_at IS NULL AND p.category_id = ? AND p.supplier_id = ? AND p.unit_type = ?", where)
        assertEquals(listOf<Any>(2, 9, "carton"), args)
        assertFalse(where.contains("IS NULL OR"))
    }

    @Test
    fun `a search word is looked for in the name and in every barcode`() {
        val (where, args) = whereOf(ProductListQuery(search = "lait 1L"))
        assertEquals(2, Regex("EXISTS \\(SELECT 1 FROM product_barcodes").findAll(where).count())
        assertEquals(listOf<Any>("%lait%", "%lait%", "%lait%", "%1L%", "%1L%", "%1L%"), args)
    }

    @Test
    fun `stock bands read the column the list asks for`() {
        assertTrue(whereOf(ProductListQuery(stockLevel = "low_stock")).first.contains("(p.stock >= 1 AND p.stock <= p.min_stock)"))
        val camion = whereOf(ProductListQuery(stockLevel = "out_of_stock", stockColumn = StockColumn.CAMION)).first
        assertTrue(camion, camion.contains("p.camion_stock <= 0"))
    }

    @Test
    fun `price bounds read selling or purchase price`() {
        val selling = whereOf(ProductListQuery(priceMin = 10.0)).first
        assertTrue(selling, selling.contains("p.selling_price >= ?"))
        val purchase = whereOf(ProductListQuery(priceMax = 99.0, priceColumn = PriceColumn.PURCHASE)).first
        assertTrue(purchase, purchase.contains("p.purchase_price <= ?"))
    }

    @Test
    fun `expiring soon needs both days and an expiry`() {
        assertFalse(whereOf(ProductListQuery(expiringFrom = "2026-09-25")).first.contains("expiry"))
        val (where, args) = whereOf(ProductListQuery(expiringFrom = "2026-09-25", expiringTo = "2026-10-25"))
        assertTrue(where.contains("(p.has_expiry = 1 AND substr(p.expiry_date, 1, 10) BETWEEN ? AND ?)"))
        assertEquals(listOf<Any>("2026-09-25", "2026-10-25"), args)
    }

    @Test
    fun `only products in the camion, for the tournee pickers`() {
        assertTrue(whereOf(ProductListQuery(inCamionOnly = true)).first.endsWith("p.camion_stock > 0"))
    }

    @Test
    fun `an ascending sort pages forward, ties newest first`() {
        val sql = ProductListSql.page(ProductListQuery(sort = ProductSort.NAME_ASC), ProductCursor("lait", 50), 20).sql
        assertTrue(sql, sql.contains("(LOWER(p.name) > ? OR (LOWER(p.name) = ? AND p.id < ?))"))
        assertTrue(sql, sql.endsWith("ORDER BY LOWER(p.name) ASC, p.id DESC LIMIT ?"))
    }

    @Test
    fun `a descending sort pages backward, ties still newest first`() {
        val sql = ProductListSql.page(ProductListQuery(sort = ProductSort.PRICE_DESC), ProductCursor(120.0, 50), 20).sql
        assertTrue(sql, sql.contains("(p.selling_price < ? OR (p.selling_price = ? AND p.id < ?))"))
        assertTrue(sql, sql.endsWith("ORDER BY p.selling_price DESC, p.id DESC LIMIT ?"))
    }

    @Test
    fun `the count sees the same filters as the pages`() {
        val query = ProductListQuery(search = "x", marqueId = 4)
        assertTrue(ProductListSql.count(query).sql.endsWith(whereOf(query).first))
    }
}
