package com.distrigo.app.data.repository

import com.distrigo.app.data.model.report.ReportRange
import java.time.LocalDate

/**
 * One client or supplier over a report's period: how many documents — sales, or bons d'achat — what
 * they came to, and, for a client, what the goods sold had cost.
 */
data class PartyFigures(
    val id: Int,
    val name: String,
    val imageUri: String?,
    val count: Int,
    val total: Double,
    /** What the goods sold had cost; 0 for a supplier, whose bons are themselves costs. */
    val cost: Double = 0.0,
) {
    val margin: Double get() = total - cost
}

/** A client who bought before the period and not since: when last, and what they bought in all. */
data class InactiveClient(val id: Int, val name: String, val imageUri: String?, val lastSale: LocalDate, val total: Double)

/** How a report's parties are ranked. */
enum class PartyRanking(val label: String) { MONTANT("Montant"), MARGE("Marge"), DOCUMENTS("Nombre") }

/**
 * The Clients et fournisseurs report for one side: [parties] are the period's clients and their sales, or
 * suppliers and their bons. [newCount] and [inactive] are for clients only.
 */
data class PartyReport(
    val side: DebtSide,
    val range: ReportRange,
    val parties: List<PartyFigures>,
    val newCount: Int = 0,
    val inactive: List<InactiveClient> = emptyList(),
) {
    val total: Double get() = parties.sumOf { it.total }
    val documents: Int get() = parties.sumOf { it.count }
    /** What a sale, or a bon, comes to on average. */
    val average: Double? get() = if (documents > 0) total / documents else null

    fun ranked(by: PartyRanking): List<PartyFigures> = when (by) {
        PartyRanking.MONTANT -> parties.sortedByDescending { it.total }
        PartyRanking.MARGE -> parties.sortedByDescending { it.margin }
        PartyRanking.DOCUMENTS -> parties.sortedWith(compareByDescending<PartyFigures> { it.count }.thenByDescending { it.total })
    }

    /** The share of the period's total made by the [n] biggest — how much the business leans on a few. */
    fun topShare(n: Int): Double? =
        if (total > 0) parties.sortedByDescending { it.total }.take(n).sumOf { it.total } / total else null
}
