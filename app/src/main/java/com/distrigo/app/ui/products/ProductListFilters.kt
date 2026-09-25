package com.distrigo.app.ui.products

import com.distrigo.app.data.local.paging.PriceColumn
import com.distrigo.app.data.local.paging.ProductListQuery
import com.distrigo.app.data.local.paging.ProductSort
import com.distrigo.app.data.model.Product
import java.time.LocalDate
import com.distrigo.app.ui.common.productMatchesTokens
import com.distrigo.app.ui.common.searchTokens

/** The Produits screen's filter sheet. Null — or blank, for the two price bounds — means "any". */
data class ProductListFilters(
    val categoryId      : Int?    = null,
    val sousCategorieId : Int?    = null,
    val marqueId        : Int?    = null,
    val supplierId      : Int?    = null,
    val unitType        : String? = null,
    val stockLevel      : String? = null,
    val priceMin        : String  = "",
    val priceMax        : String  = "",
    val expiringSoon    : Boolean = false
)

/**
 * The products that the search [query] and [filters] let through, in the order given.
 *
 * Exactly what ProductsScreen used to compute inline, in its composable body, on every
 * recomposition. The price bounds are parsed once here rather than once per product; the value is
 * the same for every product, so the result is too. ProductListFiltersEquivalenceTest checks it
 * against a verbatim copy of the inline code.
 */
fun filterProducts(products: List<Product>, query: String, filters: ProductListFilters): List<Product> {
    val tokens   = searchTokens(query)
    val priceMin = filters.priceMin.toDoubleOrNull()
    val priceMax = filters.priceMax.toDoubleOrNull()
    return products.filter { product ->
        productMatchesTokens(product, tokens) &&
            (filters.categoryId == null || product.category_id == filters.categoryId) &&
            (filters.sousCategorieId == null || product.sous_categorie_id == filters.sousCategorieId) &&
            (filters.marqueId == null || product.marque_id == filters.marqueId) &&
            (filters.supplierId == null || product.supplier_id == filters.supplierId) &&
            (filters.unitType == null || product.unit_type == filters.unitType) &&
            (filters.stockLevel == null || when (filters.stockLevel) {
                "in_stock"     -> product.stock > product.min_stock
                "low_stock"    -> product.stock in 1.0..product.min_stock.toDouble()
                "out_of_stock" -> product.stock <= 0
                else           -> true
            }) &&
            (priceMin?.let { product.selling_price >= it } ?: true) &&
            (priceMax?.let { product.selling_price <= it } ?: true) &&
            (!filters.expiringSoon || (product.has_expiry == 1 && isExpiringSoon(product.expiry_date)))
    }
}

/**
 * [products] in [sort] order.
 *
 * The two name orders compare a lowercase copy of each name made once per product, before sorting,
 * instead of `sortedBy { it.name.lowercase() }` making two for every comparison. The keys and the
 * stable sort are unchanged, so the order is identical, ties included. A case-insensitive
 * comparator would have been shorter and wrong: it folds case character by character, which does
 * not always agree with lowercase() — it counts "İ" and "i" as equal, where lowercase() does not.
 */
fun sortProducts(products: List<Product>, sort: SortOption): List<Product> = when (sort) {
    SortOption.NAME_ASC   -> products.map { it to it.name.lowercase() }.sortedBy { it.second }.map { it.first }
    SortOption.NAME_DESC  -> products.map { it to it.name.lowercase() }.sortedByDescending { it.second }.map { it.first }
    SortOption.STOCK_ASC  -> products.sortedBy { it.stock }
    SortOption.STOCK_DESC -> products.sortedByDescending { it.stock }
    SortOption.PRICE_ASC  -> products.sortedBy { it.selling_price }
    SortOption.PRICE_DESC -> products.sortedByDescending { it.selling_price }
}

/**
 * True when [expiryDate] ("yyyy-MM-dd") is between today and [withinDays] days from now.
 *
 * Moved here unchanged from ProductsScreen, where the product filter was its only user.
 */
internal fun isExpiringSoon(expiryDate: String?, withinDays: Int = 30): Boolean {
    val date = expiryDate?.let { runCatching { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(it) }.getOrNull() } ?: return false
    val diffDays = (date.time - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)
    return diffDays in 0..withinDays
}

/**
 * The Produits sheet, the search and the sort as the query the paged list runs.
 *
 * "Bientôt périmé" becomes today to today + 30, both included. The in-memory filter counted days by
 * truncating milliseconds, which also let in the 31st day; this is the rule Achats step 02 already
 * used, now for both.
 */
fun ProductListFilters.toListQuery(
    search: String,
    sort  : SortOption,
    today : LocalDate = LocalDate.now(),
): ProductListQuery = ProductListQuery(
    search          = search,
    categoryId      = categoryId,
    sousCategorieId = sousCategorieId,
    marqueId        = marqueId,
    supplierId      = supplierId,
    unitType        = unitType,
    stockLevel      = stockLevel,
    priceColumn     = PriceColumn.SELLING,
    priceMin        = priceMin.toDoubleOrNull(),
    priceMax        = priceMax.toDoubleOrNull(),
    expiringFrom    = if (expiringSoon) today.toString() else null,
    expiringTo      = if (expiringSoon) today.plusDays(30).toString() else null,
    sort            = sort.toProductSort(),
)

/** The screen's sort choice as the query's. */
fun SortOption.toProductSort(): ProductSort = when (this) {
    SortOption.NAME_ASC   -> ProductSort.NAME_ASC
    SortOption.NAME_DESC  -> ProductSort.NAME_DESC
    SortOption.STOCK_ASC  -> ProductSort.STOCK_ASC
    SortOption.STOCK_DESC -> ProductSort.STOCK_DESC
    SortOption.PRICE_ASC  -> ProductSort.PRICE_ASC
    SortOption.PRICE_DESC -> ProductSort.PRICE_DESC
}
