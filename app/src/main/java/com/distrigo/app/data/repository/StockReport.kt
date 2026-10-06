package com.distrigo.app.data.repository

import com.distrigo.app.data.model.report.ReportRange

/** What the stock is worth today where the report looks — dépôt, camion or both — at purchase price. */
data class StockValue(val depot: Double, val camion: Double, val products: Int) {
    val total: Double get() = depot + camion
}

/** A product to restock: out of stock, or below its minimum, where the report looks. */
data class RestockLine(
    val productId: Int, val name: String, val unit: String, val imageUri: String?,
    val stock: Double, val minStock: Double,
) {
    /** Nothing left — or less than nothing, sold beyond the stock. */
    val isOut: Boolean get() = stock <= 0.0005
}

/** A type of perte over the period: how many, and what they cost. */
data class LossByType(val type: String, val count: Int, val value: Double)

/** A product lost over the period: how much, in its unit, and what it cost. */
data class LossByProduct(val productId: Int, val name: String, val unit: String, val imageUri: String?, val quantity: Double, val value: Double)

/**
 * The Stock et pertes report. [stock] and [restock] are as of today, whatever the period — a stock is
 * a state; [losses] and [lostProducts] are the period's pertes, and [salesCost] what the period's
 * sales cost, against which they are measured.
 */
data class StockReport(
    val range: ReportRange,
    val stock: StockValue,
    val restock: List<RestockLine>,
    val losses: List<LossByType>,
    val lostProducts: List<LossByProduct>,
    val salesCost: Double,
) {
    val outOfStock: Int get() = restock.count { it.isOut }
    val lowStock: Int get() = restock.count { !it.isOut }
    val lossValue: Double get() = losses.sumOf { it.value }
    val lossCount: Int get() = losses.sumOf { it.count }
    /** The period's pertes over what its sales cost: of every 100 DA of goods sold, this much was lost. */
    val lossRate: Double? get() = if (salesCost > 0) lossValue / salesCost else null
}
