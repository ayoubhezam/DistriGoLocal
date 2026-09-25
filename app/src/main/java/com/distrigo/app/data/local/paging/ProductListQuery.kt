package com.distrigo.app.data.local.paging

import androidx.sqlite.db.SimpleSQLiteQuery

/** The orders a product list can be read in. */
enum class ProductSort(val column: String, val ascending: Boolean) {
    NAME_ASC  ("LOWER(p.name)",      true),
    NAME_DESC ("LOWER(p.name)",      false),
    STOCK_ASC ("p.stock",            true),
    STOCK_DESC("p.stock",            false),
    PRICE_ASC ("p.selling_price",    true),
    PRICE_DESC("p.selling_price",    false),
}

/** Which stock a stock filter reads: the dépôt's, or the camion's for the tournée pickers. */
enum class StockColumn(val column: String) { DEPOT("p.stock"), CAMION("p.camion_stock") }

/** Which price a price filter reads: Produits filters on the selling price, Achats on the purchase price. */
enum class PriceColumn(val column: String) { SELLING("p.selling_price"), PURCHASE("p.purchase_price") }

/**
 * What a product list is asked to show, in database terms — shared by the Produits screen and every
 * product picker, which differ only in which of these they set.
 *
 * Null, blank or false means "any" throughout. [expiringFrom] and [expiringTo] are calendar days,
 * `yyyy-MM-dd`, both included: the caller turns "expiring soon" into today and today + 30.
 */
data class ProductListQuery(
    val search          : String       = "",
    val categoryId      : Int?         = null,
    val sousCategorieId : Int?         = null,
    val marqueId        : Int?         = null,
    val supplierId      : Int?         = null,
    val unitType        : String?      = null,
    val stockLevel      : String?      = null,
    val stockColumn     : StockColumn  = StockColumn.DEPOT,
    val priceColumn     : PriceColumn  = PriceColumn.SELLING,
    val priceMin        : Double?      = null,
    val priceMax        : Double?      = null,
    val expiringFrom    : String?      = null,
    val expiringTo      : String?      = null,
    /** Only products the camion carries: what a tournée can sell and a return to the dépôt can take. */
    val inCamionOnly    : Boolean      = false,
    val sort            : ProductSort  = ProductSort.NAME_ASC,
)

/**
 * Where a page of a product list starts: the last row's sort value and its id.
 *
 * The sort value alone is not unique — many products share a price or a stock — so the id breaks the
 * tie, newest first, as the lists always did: they loaded the catalogue newest first and then sorted it
 * stably, which leaves equal products in that order.
 */
data class ProductCursor(val sortValue: Any, val id: Int)

/**
 * The SQL behind every product list, built per request.
 *
 * As for Achats (see PurchaseOrderListSql): a filter that is not set adds no clause, rather than every
 * filter being written as `(:x IS NULL OR …)`, so SQLite is only ever asked what the screen asks.
 *
 * Names sort by `LOWER(name)`. SQLite lowers ASCII letters only, where Kotlin's `lowercase()` also
 * lowered accented capitals; a name starting "É" can therefore sort among the capitals rather than
 * beside "é". Names are rarely written that way, and Arabic has no case.
 */
internal object ProductListSql {

    private const val FROM = "FROM products p"

    /** One page, after [after] (or from the top), at most [limit] products. */
    fun page(query: ProductListQuery, after: ProductCursor?, limit: Int): SimpleSQLiteQuery {
        val sort = query.sort
        val (where, args) = where(query, after?.let { keyset(sort, it) })
        val direction = if (sort.ascending) "ASC" else "DESC"
        return SimpleSQLiteQuery(
            // LOWER(name) comes back with the row so a name cursor holds exactly the value SQLite
            // compares against — Kotlin's lowercase() differs from it on accented capitals.
            "SELECT p.*, LOWER(p.name) AS sort_name $FROM$where " +
                "ORDER BY ${sort.column} $direction, p.id DESC LIMIT ?",
            (args + limit).toTypedArray(),
        )
    }

    /** How many products match, for the counter and the filter sheet's button. */
    fun count(query: ProductListQuery): SimpleSQLiteQuery {
        val (where, args) = where(query)
        return SimpleSQLiteQuery("SELECT COUNT(*) $FROM$where", args.toTypedArray())
    }

    /** Past [cursor] in [sort]'s direction, or level with it and older. */
    private fun keyset(sort: ProductSort, cursor: ProductCursor): Pair<String, List<Any>> {
        val beyond = if (sort.ascending) ">" else "<"
        return "(${sort.column} $beyond ? OR (${sort.column} = ? AND p.id < ?))" to
            listOf(cursor.sortValue, cursor.sortValue, cursor.id)
    }

    internal fun where(query: ProductListQuery, extra: Pair<String, List<Any>>? = null): Pair<String, List<Any>> {
        val clauses = mutableListOf("p.deleted_at IS NULL")
        val args = mutableListOf<Any>()

        // Every word must be in the name or in one of the barcodes — the rule every product search
        // used. The products.barcode column is checked as well as product_barcodes, for a product
        // saved before barcodes had their own table.
        searchTokens(query.search).forEach { token ->
            val pattern = "%${escapeLike(token)}%"
            clauses += "(p.name LIKE ? ESCAPE '\\' OR p.barcode LIKE ? ESCAPE '\\' OR EXISTS " +
                "(SELECT 1 FROM product_barcodes b WHERE b.product_id = p.id AND b.code LIKE ? ESCAPE '\\'))"
            args.addAll(listOf(pattern, pattern, pattern))
        }
        query.categoryId?.let { clauses += "p.category_id = ?"; args += it }
        query.sousCategorieId?.let { clauses += "p.sous_categorie_id = ?"; args += it }
        query.marqueId?.let { clauses += "p.marque_id = ?"; args += it }
        query.supplierId?.let { clauses += "p.supplier_id = ?"; args += it }
        query.unitType?.let { clauses += "p.unit_type = ?"; args += it }

        val stock = query.stockColumn.column
        when (query.stockLevel) {
            // The bands the filter sheets have always used; "low" is 1 up to the minimum, inclusive.
            "in_stock"     -> clauses += "$stock > p.min_stock"
            "low_stock"    -> clauses += "($stock >= 1 AND $stock <= p.min_stock)"
            "out_of_stock" -> clauses += "$stock <= 0"
        }
        query.priceMin?.let { clauses += "${query.priceColumn.column} >= ?"; args += it }
        query.priceMax?.let { clauses += "${query.priceColumn.column} <= ?"; args += it }
        if (query.expiringFrom != null && query.expiringTo != null) {
            clauses += "(p.has_expiry = 1 AND substr(p.expiry_date, 1, 10) BETWEEN ? AND ?)"
            args.addAll(listOf(query.expiringFrom, query.expiringTo))
        }
        if (query.inCamionOnly) clauses += "p.camion_stock > 0"
        extra?.let { (clause, values) -> clauses += clause; args.addAll(values) }

        return " WHERE " + clauses.joinToString(" AND ") to args
    }

    internal fun searchTokens(search: String): List<String> =
        search.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun escapeLike(token: String): String =
        token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
