package com.distrigo.app.ui.products

import com.distrigo.app.data.model.Product

/**
 * A verbatim copy of the Produits screen's inline filter and sort before C6, kept only to prove the
 * extracted versions return the same thing. The ViewModel fields became parameters of the same
 * name; nothing else is changed. It calls the same [isExpiringSoon], which moved unchanged.
 */
internal object LegacyProductFilters {

    fun productsScreen(
        products: List<Product>,
        searchQuery: String,
        filterCategoryId: Int?,
        filterSousCategorieId: Int?,
        filterMarqueId: Int?,
        filterSupplierId: Int?,
        filterUnitType: String?,
        filterStockLevel: String?,
        filterPriceMin: String,
        filterPriceMax: String,
        filterExpiringSoon: Boolean,
        sortOption: SortOption
    ): Pair<List<Product>, List<Product>> {
        val tokens   = searchQuery.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val filtered = products.filter { product ->
            (tokens.isEmpty() || tokens.all { token ->
                product.name.contains(token, ignoreCase = true) ||
                        (product.barcode?.contains(token, ignoreCase = true) == true)
            }) &&
                    (filterCategoryId == null || product.category_id == filterCategoryId) &&
                    (filterSousCategorieId == null || product.sous_categorie_id == filterSousCategorieId) &&
                    (filterMarqueId == null || product.marque_id == filterMarqueId) &&
                    (filterSupplierId == null || product.supplier_id == filterSupplierId) &&
                    (filterUnitType == null || product.unit_type == filterUnitType) &&
                    (filterStockLevel == null || when (filterStockLevel) {
                        "in_stock"     -> product.stock > product.min_stock
                        "low_stock"    -> product.stock in 1.0..product.min_stock.toDouble()
                        "out_of_stock" -> product.stock <= 0
                        else           -> true
                    }) &&
                    (filterPriceMin.toDoubleOrNull()?.let { product.selling_price >= it } ?: true) &&
                    (filterPriceMax.toDoubleOrNull()?.let { product.selling_price <= it } ?: true) &&
                    (!filterExpiringSoon || (product.has_expiry == 1 && isExpiringSoon(product.expiry_date)))
        }
        val sorted = when (sortOption) {
            SortOption.NAME_ASC   -> filtered.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC  -> filtered.sortedByDescending { it.name.lowercase() }
            SortOption.STOCK_ASC  -> filtered.sortedBy { it.stock }
            SortOption.STOCK_DESC -> filtered.sortedByDescending { it.stock }
            SortOption.PRICE_ASC  -> filtered.sortedBy { it.selling_price }
            SortOption.PRICE_DESC -> filtered.sortedByDescending { it.selling_price }
        }
        return filtered to sorted
    }

    fun productsScreen(products: List<Product>, searchQuery: String, f: ProductListFilters, sortOption: SortOption) =
        productsScreen(
            products, searchQuery, f.categoryId, f.sousCategorieId, f.marqueId, f.supplierId,
            f.unitType, f.stockLevel, f.priceMin, f.priceMax, f.expiringSoon, sortOption
        )
}
