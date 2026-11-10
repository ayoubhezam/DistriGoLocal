package com.distrigo.app.data.model

/**
 * How a product's cost — its purchase price — follows the bons received (FIN-04; owner, 2026-11-10). A business setting
 * (`business_settings.cost_method`), read at each receipt: a change applies to the receipts after it, never to the
 * past. Whatever the method, the cost stays editable by hand on the product.
 */
enum class CostMethod(val key: String) {
    /** Coût moyen unitaire pondéré: the stock held and the goods received, averaged by quantity. The default. */
    WEIGHTED_AVERAGE("cmup"),
    /** The unit cost of the last bon received. */
    LAST_PURCHASE("last"),
    /** Never moved by a receipt: only by hand, as before. */
    MANUAL("manual");

    /**
     * The cost after receiving [quantity] at [unitCost] on top of [stockBefore] held at [costBefore], to the centime.
     *
     * The average counts only stock that is there and has a cost: with none held (zero or below — goods sold before
     * they were received) or a cost never set (0), the goods received are the only ones to value, at their own cost.
     */
    fun costAfter(stockBefore: Double, costBefore: Double, quantity: Double, unitCost: Double): Double = when (this) {
        MANUAL -> costBefore
        LAST_PURCHASE -> unitCost
        WEIGHTED_AVERAGE ->
            if (quantity <= 0.0) costBefore
            else if (stockBefore <= 0.0 || costBefore <= 0.0) unitCost
            else Amount.normalize((stockBefore * costBefore + quantity * unitCost) / (stockBefore + quantity))
    }

    companion object {
        val DEFAULT = WEIGHTED_AVERAGE

        /** A key this version does not know — from a newer phone, through a restore — reads as the default. */
        fun fromKey(key: String?): CostMethod = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
