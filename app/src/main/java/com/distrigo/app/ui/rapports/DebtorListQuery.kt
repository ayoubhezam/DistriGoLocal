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

// French collation: "Épicerie" sorts with the E's, not after the Z's, and case does not split the list.
private val NAMES = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.SECONDARY }

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
        DebtorSort.NOM_AZ -> found.sortedWith(compareBy(NAMES) { it.name })
        DebtorSort.NOM_ZA -> found.sortedWith(compareByDescending(NAMES) { it.name })
    }
}
