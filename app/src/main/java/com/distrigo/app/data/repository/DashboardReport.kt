package com.distrigo.app.data.repository

import java.time.ZonedDateTime
import com.distrigo.app.R

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
 * The Dashboard's sales, as of [now]: [today] and the [month] so far. No comparison with another day or month: the
 * owner took it off the Dashboard (2026-10-20).
 */
data class DashboardSales(
    val now: ZonedDateTime,
    val today: SalesWindow,
    val month: SalesWindow,
)

/** What needs seeing to: products out of stock and below their minimum, and client debts over 90 days. */
data class DashboardAlerts(val outOfStock: Int, val lowStock: Int, val oldClientDebt: Double) {
    val any: Boolean get() = outOfStock > 0 || lowStock > 0 || oldClientDebt > 0.005
}

/** What the clients owe today, and what is owed to the suppliers. */
data class DashboardBalances(val clientsOwe: Double, val owedToSuppliers: Double)

/** How much of a sale was paid at the time: all of it, part, or nothing. */
enum class PaymentState { PAID, PARTIAL, UNPAID }

/** « Activité récente »'s filter (D11). */
enum class ActivityFilter(@androidx.annotation.StringRes val label: Int) {
    ALL(R.string.common_filter_all),
    PAID(R.string.common_filter_paid_f),
    CREDIT(R.string.common_filter_on_credit),
}

/** A recent sale, dépôt or tournée, as the Dashboard lists it. */
data class RecentSale(
    val id: Int,
    val numero: String?,
    val clientName: String?,
    val clientImage: String?,
    val source: String,
    val total: Double,
    val paid: Double,
    val createdAt: String,
) {
    val state: PaymentState
        get() = when {
            paid >= total - com.distrigo.app.data.model.Amount.HALF_CENTIME -> PaymentState.PAID
            paid > com.distrigo.app.data.model.Amount.HALF_CENTIME -> PaymentState.PARTIAL
            else -> PaymentState.UNPAID
        }
}

/** Where a client of the open tournée stands today (plan, Part 1 §6). */
enum class VisitState { WITH_SALE, WITHOUT_SALE, PENDING }

data class TourneeDayClient(val clientId: Int, val name: String, val imageUri: String?, val state: VisitState)

/** The open tournée, as the Dashboard shows it: its label and its clients' visits. */
data class TourneeDay(val id: Int, val label: String, val clients: List<TourneeDayClient>) {
    val total: Int get() = clients.size
    val withSale: Int get() = clients.count { it.state == VisitState.WITH_SALE }
    val withoutSale: Int get() = clients.count { it.state == VisitState.WITHOUT_SALE }
    val pending: Int get() = clients.count { it.state == VisitState.PENDING }
    val visited: Int get() = withSale + withoutSale

    companion object {
        /**
         * A client with a sale on the tournée bought; one marked visited without one didn't; the rest are still to
         * visit. [statuses] are TourneeClientInfo's (« visite » once visited).
         */
        fun of(
            id: Int,
            label: String,
            clients: List<Triple<Int, String, String?>>,
            statuses: Map<Int, String>,
            clientsWithSale: Set<Int>,
        ) = TourneeDay(id, label, clients.map { (clientId, name, image) ->
            TourneeDayClient(clientId, name, image, when {
                clientId in clientsWithSale -> VisitState.WITH_SALE
                statuses[clientId] == "visite" -> VisitState.WITHOUT_SALE
                else -> VisitState.PENDING
            })
        })
    }
}
