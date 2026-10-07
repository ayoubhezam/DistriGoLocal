package com.distrigo.app.data.repository

import com.distrigo.app.data.local.dao.DebtAge
import com.distrigo.app.data.local.dao.ReportDao
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportRange
import com.distrigo.app.data.time.BusinessDates
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Whose accounts a Créances et dettes report reads: what clients owe, or what is owed to suppliers. */
enum class DebtSide(val label: String) {
    CLIENTS("Clients"),
    FOURNISSEURS("Fournisseurs"),
}

/** How old an unpaid amount is, by the date of the document that left it unpaid. */
enum class AgeBand(val label: String) {
    RECENT("0 – 30 jours"),
    MONTH("31 – 60 jours"),
    TWO_MONTHS("61 – 90 jours"),
    OLD("Plus de 90 jours"),
}

/** One party's balance, its parts by age, and when it last paid ([lastPayment] null if never). */
data class DebtorLine(
    val id: Int,
    val name: String,
    val balance: Double,
    val ages: List<Double>,
    val lastPayment: LocalDate?,
) {
    /** The oldest band holding part of the balance: how long the oldest unpaid amount has waited. */
    val oldest: AgeBand get() = AgeBand.entries[ages.indexOfLast { it > 0.005 }.coerceAtLeast(0)]
}

/**
 * The Créances et dettes report for one side and one filter.
 *
 * [outstanding], [ages] and [debtors] are as of today, whatever the period: a balance is a state, not
 * a flow. [credit], [payments] and [returns] are the period's flows — the credit its documents left,
 * the payments made, the returns — and [change] is what they did to the balance.
 */
data class DebtReport(
    val side: DebtSide,
    val range: ReportRange,
    val today: LocalDate,
    val outstanding: Double,
    val ages: List<Double>,
    val debtors: List<DebtorLine>,
    val credit: Double,
    val payments: ReturnFigures,
    val returns: ReturnFigures,
) {
    val change: Double get() = credit - payments.total - returns.total
}

/**
 * Spends [balance] on a party's unpaid credit by age band, newest first ([credits] indexed by
 * AgeBand.ordinal): the balance is what payments have not yet covered, and a payment settles the oldest
 * debt first, so what is still owed is the most recent credit. What no credit explains — a supplier's
 * opening balance, a sale paid back by a return — is counted in the oldest band.
 */
fun spendByAge(balance: Double, credits: DoubleArray): List<Double> {
    var left = balance
    val out = DoubleArray(AgeBand.entries.size)
    for (band in out.indices) {
        val take = minOf(left, credits.getOrElse(band) { 0.0 }).coerceAtLeast(0.0)
        out[band] = take
        left -= take
    }
    if (left > 0) out[out.lastIndex] += left
    return out.toList()
}

/** Reads one side's debts: SQL aggregates from ReportDao, the age split done here. */
suspend fun ReportDao.debtReport(
    side: DebtSide,
    filter: ReportFilter,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): DebtReport {
    val range = filter.resolve(today, zone)
    val first = range.firstDay.toString()
    val last = range.lastDay.toString()
    val cut = listOf(30L, 60L, 90L).map { today.minusDays(it) }

    val debtors: List<com.distrigo.app.data.local.dao.Debtor>
    val ageRows: List<DebtAge>
    val credit: Double
    val payments: ReturnFigures
    val returns: ReturnFigures
    when (side) {
        DebtSide.CLIENTS -> {
            debtors = clientDebtors()
            ageRows = cut.map { BusinessDates.dayStart(it, zone) }.let { clientDebtAges(it[0], it[1], it[2]) }
            credit = creditGiven(range.start, range.end)
            payments = clientPayments(range.start, range.end).let { ReturnFigures(it.count, it.total) }
            returns = clientReturns(first, last).let { ReturnFigures(it.count, it.total) }
        }
        DebtSide.FOURNISSEURS -> {
            debtors = supplierDebtors()
            ageRows = cut.map { it.toString() }.let { supplierDebtAges(it[0], it[1], it[2]) }
            credit = creditTaken(first, last)
            payments = supplierPayments(range.start, range.end).let { ReturnFigures(it.count, it.total) }
            returns = supplierReturns(first, last).let { ReturnFigures(it.count, it.total) }
        }
    }

    val creditsByParty = ageRows.groupBy { it.party }.mapValues { (_, rows) ->
        DoubleArray(AgeBand.entries.size).also { a -> rows.forEach { a[it.band] += it.credit } }
    }
    val lines = debtors.map { d ->
        DebtorLine(
            id = d.id,
            name = d.name,
            balance = d.balance,
            ages = spendByAge(d.balance, creditsByParty[d.id] ?: DoubleArray(0)),
            lastPayment = d.last_payment?.let { runCatching { Instant.parse(it).atZone(zone).toLocalDate() }.getOrNull() },
        )
    }
    val ages = AgeBand.entries.indices.map { band -> lines.sumOf { it.ages[band] } }

    return DebtReport(
        side = side,
        range = range,
        today = today,
        outstanding = lines.sumOf { it.balance },
        ages = ages,
        debtors = lines,
        credit = credit,
        payments = payments,
        returns = returns,
    )
}
