package com.distrigo.app.ui.products

import com.distrigo.app.data.model.PriceMovement
import com.distrigo.app.data.model.PriceMovementKind
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportPeriod
import com.distrigo.app.data.time.BusinessDates
import java.time.LocalDate

/** Keeps only the movements that went one way. */
enum class PriceVariation(val label: String) {
    ALL("Toutes"),
    UP("Hausses"),
    DOWN("Baisses"),
}

enum class PriceSort(val label: String) {
    RECENT("Plus récent"),
    OLDEST("Plus ancien"),
    CHEAPEST("Prix croissant"),
    DEAREST("Prix décroissant"),
}

/**
 * Everything the price history screen is narrowed by.
 *
 * [kind] is the segmented control (null = Tous) and is deliberately *not* part of [narrow]'s work
 * for the counters: the screen counts each kind over the other filters, so switching kind cannot
 * change the counters beside it.
 */
data class PriceHistoryFilters(
    val kind      : PriceMovementKind? = null,
    /**
     * The days the list covers — the Rapports filter, picked with the same bar. A year by default: a
     * price moves far less often than a sale, and this month alone is often empty.
     */
    val period    : ReportFilter  = ReportFilter(ReportPeriod.CETTE_ANNEE),
    val variation : PriceVariation = PriceVariation.ALL,
    val sort      : PriceSort     = PriceSort.RECENT,
    val query     : String        = "",
) {
    /** Filters that narrow what the list shows, beyond the kind and the search box. */
    val activeCount: Int
        get() = listOf(
            variation != PriceVariation.ALL,
            sort != PriceSort.RECENT,
        ).count { it }
}

/** The local day a movement belongs to — an instant from a vente, a calendar date from an older bon. */
fun PriceMovement.day(): LocalDate =
    runCatching { LocalDate.parse(BusinessDates.localDay(date)) }.getOrDefault(LocalDate.MIN)

/**
 * The movements [filters] keeps, in its order.
 *
 * [includeKind] is false when counting the kinds: the counters describe what each segment would
 * show, so they are measured with every other filter applied but the segment itself.
 */
fun List<PriceMovement>.narrow(
    filters     : PriceHistoryFilters,
    today       : LocalDate = LocalDate.now(),
    includeKind : Boolean = true,
): List<PriceMovement> {
    val tokens = filters.query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val days = filters.period.days(today)

    return filter { movement ->
        if (includeKind && filters.kind != null && movement.kind != filters.kind) return@filter false
        if (movement.day() !in days) return@filter false
        when (filters.variation) {
            PriceVariation.UP   -> if ((movement.delta ?: 0.0) <= 0.0) return@filter false
            PriceVariation.DOWN -> if ((movement.delta ?: 0.0) >= 0.0) return@filter false
            PriceVariation.ALL  -> Unit
        }
        if (tokens.isNotEmpty()) {
            val haystack = (movement.party + " " + movement.kind.label + " " + movement.documentLabel).lowercase()
            if (!tokens.all { haystack.contains(it) }) return@filter false
        }
        true
    }.sortedWith(filters.sort.comparator())
}

private fun PriceSort.comparator(): Comparator<PriceMovement> = when (this) {
    PriceSort.RECENT   -> compareByDescending<PriceMovement> { it.day() }.thenByDescending { it.documentId }
    PriceSort.OLDEST   -> compareBy<PriceMovement> { it.day() }.thenBy { it.documentId }
    PriceSort.CHEAPEST -> compareBy<PriceMovement> { it.unitPrice }.thenByDescending { it.day() }
    PriceSort.DEAREST  -> compareByDescending<PriceMovement> { it.unitPrice }.thenByDescending { it.day() }
}

/** What one kind's prices amount to over the movements shown. */
data class PriceStats(val last: Double, val average: Double, val min: Double, val max: Double)

/** The stats of [kind] among these movements, or null when it has none. */
fun List<PriceMovement>.statsOf(kind: PriceMovementKind): PriceStats? {
    val prices = filter { it.kind == kind }
    if (prices.isEmpty()) return null
    val latest = prices.maxWith(compareBy<PriceMovement> { it.day() }.thenBy { it.documentId })
    return PriceStats(
        last    = latest.unitPrice,
        average = prices.sumOf { it.unitPrice } / prices.size,
        min     = prices.minOf { it.unitPrice },
        max     = prices.maxOf { it.unitPrice },
    )
}
