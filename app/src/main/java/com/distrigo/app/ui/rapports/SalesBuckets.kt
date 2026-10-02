package com.distrigo.app.ui.rapports

import com.distrigo.app.data.repository.SalesDay
import com.distrigo.app.data.repository.SalesFigures
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One bar of the Ventes chart and one row of its table: a day, or a month when the period is long.
 * [label] is short, for under a bar; [title] is the table row's.
 */
data class SalesBucket(
    val start: LocalDate,
    val label: String,
    val title: String,
    val depot: SalesFigures,
    val camion: SalesFigures,
) {
    val all: SalesFigures get() = depot + camion
}

/** Beyond this many days a bar a day is too thin to read or tap, so the report shows months. */
const val MAX_DAILY_BUCKETS = 62

private val FR = Locale.FRENCH
private val DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM", FR)
private val DAY_TITLE = DateTimeFormatter.ofPattern("EEE dd/MM/yyyy", FR)
private val MONTH_LABEL = DateTimeFormatter.ofPattern("MMM", FR)
private val MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy", FR)

/** The report's [days] as bars: one a day up to [MAX_DAILY_BUCKETS] days, one a month beyond. Oldest first. */
fun salesBuckets(days: List<SalesDay>): List<SalesBucket> =
    if (days.size <= MAX_DAILY_BUCKETS) {
        days.map { SalesBucket(it.day, it.day.format(DAY_LABEL), it.day.format(DAY_TITLE).capitalized(), it.depot, it.camion) }
    } else {
        days.groupBy { YearMonth.from(it.day) }.map { (month, inMonth) ->
            SalesBucket(
                start = month.atDay(1),
                label = month.format(MONTH_LABEL).trimEnd('.'),
                title = month.format(MONTH_TITLE).capitalized(),
                depot = inMonth.fold(SalesFigures.ZERO) { sum, d -> sum + d.depot },
                camion = inMonth.fold(SalesFigures.ZERO) { sum, d -> sum + d.camion },
            )
        }
    }

private fun String.capitalized() = replaceFirstChar { it.titlecase(FR) }
