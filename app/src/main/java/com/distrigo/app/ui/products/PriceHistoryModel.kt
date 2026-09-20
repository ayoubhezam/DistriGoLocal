package com.distrigo.app.ui.products

import com.distrigo.app.data.model.PriceMovement
import com.distrigo.app.data.model.PriceMovementKind
import com.distrigo.app.data.time.BusinessDates
import java.time.LocalDate

/** How far back the price history looks. One period governs the summaries, the chart and the list. */
enum class PricePeriod(val label: String, val days: Long?) {
    ALL("Tout", null),
    WEEK("7 jours", 7),
    MONTH("30 jours", 30),
    QUARTER("3 mois", 92),
}

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
    val period    : PricePeriod   = PricePeriod.ALL,
    val variation : PriceVariation = PriceVariation.ALL,
    val sort      : PriceSort     = PriceSort.RECENT,
    val query     : String        = "",
) {
    /** Filters that narrow what the list shows, beyond the kind and the search box. */
    val activeCount: Int
        get() = listOf(
            period != PricePeriod.ALL,
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
    val earliest = filters.period.days?.let { today.minusDays(it) }

    return filter { movement ->
        if (includeKind && filters.kind != null && movement.kind != filters.kind) return@filter false
        if (earliest != null && movement.day() < earliest) return@filter false
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

// ── The chart ────────────────────────────────────────────────────────────────

/** How many movements one plotted point stands for. */
enum class PriceGrouping(val caption: String?) {
    DAILY(null),
    WEEKLY("Un point par semaine"),
    MONTHLY("Un point par mois"),
}

/** One plotted point: a date, a price, and how many movements were averaged into it. */
data class PricePoint(val day: LocalDate, val price: Double, val count: Int)

/** A kind's line on the chart. */
data class PriceSeries(val kind: PriceMovementKind, val points: List<PricePoint>)

/**
 * The lines to draw, and at what granularity.
 *
 * A year of daily purchases is a hundred points on a 310 dp chart — unreadable, and an axis of
 * overlapping labels. So the points are thinned by how many there are, not by a control of their
 * own: past [DENSE] movements in the range they are averaged by week, and past [DENSE] weeks by
 * month. The granularity is chosen from the *densest* line, so both are plotted on the same footing,
 * and [PriceGrouping.caption] tells the user what a point now means rather than averaging silently.
 */
fun List<PriceMovement>.chartSeries(): Pair<List<PriceSeries>, PriceGrouping> {
    val byKind = PriceMovementKind.entries.associateWith { kind -> filter { it.kind == kind } }
        .filterValues { it.isNotEmpty() }
    if (byKind.isEmpty()) return emptyList<PriceSeries>() to PriceGrouping.DAILY

    // Weeks first: a line of many movements spread over many weeks needs months, not weeks.
    val weeks = byKind.values.maxOf { line -> line.map { startOfWeek(it.day()) }.distinct().size }
    val grouping = when {
        weeks > DENSE -> PriceGrouping.MONTHLY
        byKind.values.maxOf { it.size } > DENSE -> PriceGrouping.WEEKLY
        else -> PriceGrouping.DAILY
    }

    val series = byKind.map { (kind, line) ->
        val buckets = line.groupBy { movement ->
            when (grouping) {
                PriceGrouping.DAILY   -> movement.day()
                PriceGrouping.WEEKLY  -> startOfWeek(movement.day())
                PriceGrouping.MONTHLY -> movement.day().withDayOfMonth(1)
            }
        }
        PriceSeries(
            kind   = kind,
            points = buckets.map { (day, movements) ->
                PricePoint(day, movements.sumOf { it.unitPrice } / movements.size, movements.size)
            }.sortedBy { it.day }
        )
    }.sortedBy { it.kind.ordinal }

    return series to grouping
}

/** Above this many points on one line, the chart groups them — see [chartSeries]. */
const val DENSE = 40

private fun startOfWeek(day: LocalDate): LocalDate = day.minusDays((day.dayOfWeek.value - 1).toLong())
