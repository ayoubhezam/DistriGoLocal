package com.distrigo.app.ui.purchases

import com.distrigo.app.data.local.paging.PriceColumn
import com.distrigo.app.data.local.paging.ProductListQuery
import com.distrigo.app.data.local.paging.ProductSort
import com.distrigo.app.data.model.barcodeContains
import com.distrigo.app.data.model.Product
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

// What Achats step 02 narrows its product list by.
//
// The same criteria as the Produits sheet, so the two read as one tool, with one deliberate
// difference: price is the *purchase* price — the price this list shows and the price a bon is
// written at — where Produits filters on the selling price.
//
// Kept free of Compose so the rules are unit-tested, and held as one immutable value so the whole
// set can live on the form session and come back intact after a detour.

data class ProductListFilters(
    val categoryId      : Int?    = null,
    val sousCategorieId : Int?    = null,
    val marqueId        : Int?    = null,
    val supplierId      : Int?    = null,
    val unitType        : String? = null,   // "carton" | "pièce"
    val stockLevel      : String? = null,   // "in_stock" | "low_stock" | "out_of_stock"
    val priceMin        : String  = "",     // the raw text typed, as the Produits sheet keeps it
    val priceMax        : String  = "",
    val expiringSoon    : Boolean = false
) {
    /** How many criteria are narrowing the list. A price range counts once, however it is bounded. */
    val activeCount: Int
        get() = listOf(
            categoryId != null,
            sousCategorieId != null,
            marqueId != null,
            supplierId != null,
            unitType != null,
            stockLevel != null,
            priceMin.toDoubleOrNull() != null || priceMax.toDoubleOrNull() != null,
            expiringSoon
        ).count { it }

    val isActive: Boolean get() = activeCount > 0
}

/** Every word typed must appear in the name or the barcode — the rule step 02 has always used. */
internal fun Product.matchesSearch(query: String): Boolean {
    val tokens = query.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
    return tokens.all { token ->
        name.contains(token, ignoreCase = true) ||
            barcodeContains(token)
    }
}

internal fun Product.matches(filters: ProductListFilters, today: LocalDate = LocalDate.now()): Boolean =
    (filters.categoryId      == null || category_id       == filters.categoryId) &&
    (filters.sousCategorieId == null || sous_categorie_id == filters.sousCategorieId) &&
    (filters.marqueId        == null || marque_id         == filters.marqueId) &&
    (filters.supplierId      == null || supplier_id       == filters.supplierId) &&
    (filters.unitType        == null || unit_type         == filters.unitType) &&
    (filters.stockLevel == null || when (filters.stockLevel) {
        // Same bands as the Produits sheet, so "Stock faible" means the same thing on both.
        "in_stock"     -> stock > min_stock
        "low_stock"    -> stock in 1.0..min_stock.toDouble()
        "out_of_stock" -> stock <= 0
        else           -> true
    }) &&
    (filters.priceMin.toDoubleOrNull()?.let { purchase_price >= it } ?: true) &&
    (filters.priceMax.toDoubleOrNull()?.let { purchase_price <= it } ?: true) &&
    (!filters.expiringSoon || (has_expiry == 1 && expiresWithin(expiry_date, today, days = 30)))

private fun expiresWithin(expiryDate: String?, today: LocalDate, days: Long): Boolean {
    val date = try {
        expiryDate?.let { LocalDate.parse(it.take(10)) }
    } catch (e: DateTimeParseException) {
        null
    } ?: return false
    return ChronoUnit.DAYS.between(today, date) in 0..days
}

/**
 * Step 02's search and filters as the query its paged list runs: the Produits rules, with price read
 * from the purchase price, newest product first as the list has always been.
 */
fun ProductListFilters.toListQuery(search: String, today: LocalDate = LocalDate.now()): ProductListQuery =
    ProductListQuery(
        search          = search,
        categoryId      = categoryId,
        sousCategorieId = sousCategorieId,
        marqueId        = marqueId,
        supplierId      = supplierId,
        unitType        = unitType,
        stockLevel      = stockLevel,
        priceColumn     = PriceColumn.PURCHASE,
        priceMin        = priceMin.toDoubleOrNull(),
        priceMax        = priceMax.toDoubleOrNull(),
        expiringFrom    = if (expiringSoon) today.toString() else null,
        expiringTo      = if (expiringSoon) today.plusDays(30).toString() else null,
        sort            = ProductSort.NEWEST,
    )
