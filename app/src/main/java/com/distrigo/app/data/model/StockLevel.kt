package com.distrigo.app.data.model

/**
 * "En stock", "Stock faible", "Rupture de stock": the one rule behind every stock filter.
 *
 *  - **Rupture** — nothing left: the stock is zero or below.
 *  - **Stock faible** — some left, but no more than the minimum.
 *  - **En stock** — more than the minimum.
 *
 * Every stock is in exactly one band. The rule used to be written three times (the Produits sheet, the
 * Achats sheet, the paged SQL) with "faible" starting at 1, so half a carton left was in none of them.
 * [of] and [sql] are the same bounds, written once each, with the tolerance of [Quantity].
 */
object StockLevel {

    const val IN_STOCK = "in_stock"
    const val LOW_STOCK = "low_stock"
    const val OUT_OF_STOCK = "out_of_stock"

    fun of(stock: Double, minStock: Double): String = when {
        stock < Quantity.EPSILON             -> OUT_OF_STOCK
        stock < minStock + Quantity.EPSILON  -> LOW_STOCK
        else                                 -> IN_STOCK
    }

    /** True when [stock] is in [level]; an unknown level filters nothing. */
    fun matches(level: String, stock: Double, minStock: Double): Boolean =
        level !in LEVELS || of(stock, minStock) == level

    /** [level] as a SQL condition on the expressions [stock] and [minStock]; null for an unknown level. */
    fun sql(level: String, stock: String, minStock: String): String? {
        val eps = Quantity.EPSILON
        return when (level) {
            OUT_OF_STOCK -> "$stock < $eps"
            LOW_STOCK    -> "($stock >= $eps AND $stock < $minStock + $eps)"
            IN_STOCK     -> "$stock >= $minStock + $eps"
            else         -> null
        }
    }

    private val LEVELS = setOf(IN_STOCK, LOW_STOCK, OUT_OF_STOCK)
}
