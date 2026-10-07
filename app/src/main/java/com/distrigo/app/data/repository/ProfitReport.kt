package com.distrigo.app.data.repository

import com.distrigo.app.data.model.report.ReportRange

/** A type of charge over the period: how many, and what they came to. */
data class ChargeByType(val type: String, val count: Int, val value: Double)

/**
 * The Résultat report: what the period earned once everything is taken off.
 *
 * - [sales] and [cost]: what the period's sales came to, and what the goods sold had cost — each line at
 *   the purchase price it was sold at;
 * - [returns] and [returnsCost]: the client returns of the period, at the price they were sold at, and
 *   what those goods cost — at today's purchase price, a return line keeping no cost of its own. A
 *   return cancels its sale's margin: the refund goes, the goods' cost comes back. Goods that came back
 *   only to be thrown away are in [losses] too, which is right: the margin is cancelled, then the goods
 *   are lost;
 * - [charges] and [losses]: the period's expenses and pertes.
 */
data class ProfitReport(
    val range: ReportRange,
    val sales: Double,
    val cost: Double,
    val returnsCount: Int,
    val returns: Double,
    val returnsCost: Double,
    val charges: List<ChargeByType>,
    val losses: List<LossByType>,
) {
    val grossMargin: Double get() = sales - cost
    /** The margin the period's returns cancelled. */
    val returnsMargin: Double get() = returns - returnsCost
    val chargesTotal: Double get() = charges.sumOf { it.value }
    val chargesCount: Int get() = charges.sumOf { it.count }
    val lossesTotal: Double get() = losses.sumOf { it.value }
    val lossesCount: Int get() = losses.sumOf { it.count }
    /** What is left: the gross margin, less the returns' margin, the charges and the pertes. */
    val net: Double get() = grossMargin - returnsMargin - chargesTotal - lossesTotal
    /** Of every 100 DA sold, what is left once everything is taken off. */
    val netShare: Double? get() = if (sales > 0) net / sales else null
}
