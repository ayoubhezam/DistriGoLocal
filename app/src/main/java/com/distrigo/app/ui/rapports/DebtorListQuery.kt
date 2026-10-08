package com.distrigo.app.ui.rapports

import com.distrigo.app.data.repository.DebtorLine
import com.distrigo.app.ui.common.searchTokens
import java.text.Collator
import java.util.Locale

/** How the full debtor list is ordered. The biggest debt first is the default, as in the report. */
enum class DebtorSort(val label: String) {
    DETTE_DESC("Dette (décroissant)"),
    DETTE_ASC("Dette (croissant)"),
    NOM_AZ("Nom (A-Z)"),
    NOM_ZA("Nom (Z-A)"),
}

/**
 * French collation: "Épicerie" sorts with the E's, not after the Z's, and case does not split the list.
 *
 * A new one for each sort, not one shared: a Collator is not safe to use from two threads at once, and
 * the list is sorted off the main thread (DebtReportViewModel.debtorList).
 */
private fun frenchNames(): Collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.SECONDARY }

/**
 * The debtors whose name holds every word of [query] — in any order, ignoring case, as the client
 * search does — ordered by [sort]. Ties keep the debt order, biggest first.
 */
fun debtorsMatching(debtors: List<DebtorLine>, query: String, sort: DebtorSort): List<DebtorLine> {
    val tokens = searchTokens(query)
    val found = if (tokens.isEmpty()) debtors
    else debtors.filter { d -> tokens.all { d.name.contains(it, ignoreCase = true) } }
    return when (sort) {
        DebtorSort.DETTE_DESC -> found.sortedByDescending { it.balance }
        DebtorSort.DETTE_ASC -> found.sortedBy { it.balance }
        DebtorSort.NOM_AZ -> byName(found, descending = false)
        DebtorSort.NOM_ZA -> byName(found, descending = true)
    }
}

/**
 * [debtors] in French name order. Each name is collated once, into a key; comparing two keys is then a
 * byte comparison, where comparing two names collates both again — some ten thousand times over a
 * thousand debtors. The sort is stable either way, so ties keep the order they came in.
 */
private fun byName(debtors: List<DebtorLine>, descending: Boolean): List<DebtorLine> {
    val collator = frenchNames()
    val keyed = debtors.map { it to collator.getCollationKey(it.name) }
    val sorted = if (descending) keyed.sortedByDescending { it.second } else keyed.sortedBy { it.second }
    return sorted.map { it.first }
}
