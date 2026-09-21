package com.distrigo.app.ui.products

import com.distrigo.app.data.model.PriceMovement
import com.distrigo.app.data.model.PriceMovementKind
import com.distrigo.app.data.time.BusinessDates
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * How far back the price history looks — and, with it, what one point of the chart stands for.
 *
 * One period governs the summaries, the chart and the list, so the three cannot disagree. There is
 * deliberately no "everything": a wholesaler prices against the last year, not against a catalogue's
 * whole life, and an unbounded range is also the one range whose chart cannot be laid out in advance.
 */
enum class PricePeriod(val label: String, val days: Long, val slots: Int) {
    WEEK("7 j", 7, 7),
    MONTH("30 j", 30, 4),
    YEAR("12 mois", 365, 12),
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
    val period    : PricePeriod   = PricePeriod.MONTH,
    val variation : PriceVariation = PriceVariation.ALL,
    val sort      : PriceSort     = PriceSort.RECENT,
    val query     : String        = "",
) {
    /** Filters that narrow what the list shows, beyond the kind and the search box. */
    val activeCount: Int
        get() = listOf(
            period != PricePeriod.MONTH,
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
    val earliest = filters.period.startOn(today)

    return filter { movement ->
        if (includeKind && filters.kind != null && movement.kind != filters.kind) return@filter false
        if (movement.day() < earliest) return@filter false
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

/**
 * One slot of the chart's axis: a stretch of days and the label under it.
 *
 * The slots are laid out from the period alone — seven days, four weeks, twelve months — before any
 * data is looked at, which is what lets the axis read « lun mar mer… » or « S1 S2 S3 S4 » instead of
 * bare dates, and what keeps Achat and Vente on the same footing.
 */
data class PriceSlot(val index: Int, val from: LocalDate, val until: LocalDate, val label: String)

/**
 * One plotted point.
 *
 * [price] is the quantity-weighted average of what changed hands in the slot — the price actually
 * paid per unit, not the average of the tickets. A slot with no movement carries the previous slot's
 * price forward ([carried]): in wholesale the last price agreed stays the price in force until a new
 * deal is struck, which is what keeps the line continuous. A carried point is drawn without a dot,
 * so the line never claims a transaction that did not happen.
 */
data class PricePoint(val slot: PriceSlot, val price: Double, val count: Int, val carried: Boolean)

/** A kind's line on the chart. */
data class PriceSeries(val kind: PriceMovementKind, val points: List<PricePoint>)

/**
 * A day or a month as the axis names it: « Lun », « Sept ».
 *
 * The locale's own short form, capitalised and without its trailing point — cut to three letters it
 * would name juin and juillet alike.
 */
private fun java.time.DayOfWeek.shortLabel(): String =
    getDisplayName(TextStyle.SHORT, Locale.FRENCH).removeSuffix(".").replaceFirstChar { it.uppercase() }

private fun java.time.Month.shortLabel(): String =
    getDisplayName(TextStyle.SHORT, Locale.FRENCH).removeSuffix(".").replaceFirstChar { it.uppercase() }

/** The first day the period covers, [PricePeriod.days] back from and including [today]. */
fun PricePeriod.startOn(today: LocalDate): LocalDate = today.minusDays(days - 1)

/**
 * The period's slots, oldest first.
 *
 * A week is seven days, labelled by their name. Twelve months are twelve calendar months, labelled
 * by theirs. Thirty days do not divide into four equal weeks, so the two spare days go to the oldest
 * slot — the axis reads S1…S4 and the range really is the thirty days the chip promises.
 */
fun PricePeriod.slotsOn(today: LocalDate): List<PriceSlot> = when (this) {
    PricePeriod.WEEK -> (0 until slots).map { i ->
        val day = startOn(today).plusDays(i.toLong())
        PriceSlot(i, day, day.plusDays(1), day.dayOfWeek.shortLabel())
    }
    PricePeriod.MONTH -> {
        val start = startOn(today)
        (0 until slots).map { i ->
            val from  = if (i == 0) start else start.plusDays(days - (slots - i) * 7L)
            val until = start.plusDays(days - (slots - i - 1) * 7L)
            PriceSlot(i, from, until, "S${i + 1}")
        }
    }
    PricePeriod.YEAR -> {
        val firstMonth = today.withDayOfMonth(1).minusMonths(slots - 1L)
        (0 until slots).map { i ->
            val from = firstMonth.plusMonths(i.toLong())
            PriceSlot(i, from, from.plusMonths(1), from.month.shortLabel())
        }
    }
}

/**
 * The lines to draw over [period]'s slots, one per kind present.
 *
 * Each slot holds the quantity-weighted average of that kind's movements inside it; a slot with none
 * carries the previous price forward. Nothing is carried *before* a kind's first movement — there was
 * no price in force yet — so a line begins where its first real point is.
 */
fun List<PriceMovement>.chartSeries(period: PricePeriod, today: LocalDate = LocalDate.now()): List<PriceSeries> {
    val slots = period.slotsOn(today)
    return PriceMovementKind.entries.mapNotNull { kind ->
        val movements = filter { it.kind == kind }
        if (movements.isEmpty()) return@mapNotNull null

        var lastPrice: Double? = null
        val points = slots.mapNotNull { slot ->
            val inSlot = movements.filter { it.day() >= slot.from && it.day() < slot.until }
            if (inSlot.isNotEmpty()) {
                // Weighted by quantity: two cartons at 80 and ten at 90 average to 88.33, not 85.
                val quantity = inSlot.sumOf { it.quantity }
                val price = if (quantity > 0) inSlot.sumOf { it.unitPrice * it.quantity } / quantity
                            else inSlot.sumOf { it.unitPrice } / inSlot.size
                lastPrice = price
                PricePoint(slot, price, inSlot.size, carried = false)
            } else {
                lastPrice?.let { PricePoint(slot, it, 0, carried = true) }
            }
        }
        if (points.isEmpty()) null else PriceSeries(kind, points)
    }
}
