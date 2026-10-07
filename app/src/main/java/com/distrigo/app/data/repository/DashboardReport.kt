package com.distrigo.app.data.repository

import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * The sales over a stretch of time, both places together: how many, what they came to, what was paid
 * at the sale, what the goods sold had cost, and to how many clients.
 */
data class SalesWindow(val count: Int, val total: Double, val paid: Double, val cost: Double, val clients: Int) {
    val credit: Double get() = total - paid
    val margin: Double get() = total - cost
    /** What a sale comes to on average; null without a sale. */
    val basket: Double? get() = if (count > 0) total / count else null
    /** The margin on the purchase price, as the Direction du Commerce counts it; null without a cost. */
    val marginRate: Double? get() = if (cost > 0) margin / cost else null

    companion object {
        val ZERO = SalesWindow(0, 0.0, 0.0, 0.0, 0)
    }
}

/**
 * How [now] compares with [before]: 0.12 for 12 % more, −0.3 for 30 % less; null when there is nothing
 * before to compare with.
 */
fun change(now: Double, before: Double): Double? = if (before > 0) (now - before) / before else null

/** A day's sales, both places together. */
data class DayTotal(val day: LocalDate, val total: Double)

/**
 * The Dashboard's sales, as of [now]:
 * - [today] so far, and [lastWeek]: the same day a week before, up to the same hour — a day is compared
 *   with a day of the same kind, and a morning with a morning;
 * - [month] so far, and [lastMonth]: the month before, up to the same date and hour;
 * - [week]: the seven days up to today, oldest first.
 */
data class DashboardSales(
    val now: ZonedDateTime,
    val today: SalesWindow,
    val lastWeek: SalesWindow,
    val month: SalesWindow,
    val lastMonth: SalesWindow,
    val week: List<DayTotal>,
)

/** What needs seeing to: products out of stock and below their minimum, and client debts over 90 days. */
data class DashboardAlerts(val outOfStock: Int, val lowStock: Int, val oldClientDebt: Double) {
    val any: Boolean get() = outOfStock > 0 || lowStock > 0 || oldClientDebt > 0.005
}

/** What the clients owe today, and what is owed to the suppliers. */
data class DashboardBalances(val clientsOwe: Double, val owedToSuppliers: Double)
