package com.distrigo.app.data.model.report

import com.distrigo.app.data.time.BusinessDates
import java.time.LocalDate
import java.time.ZoneId

/**
 * The period a report covers, as the user picks it. Each preset is resolved against a given day, so
 * "Ce mois" on the 2nd is the 1st and the 2nd, and a test can name the day.
 *
 * A rolling "7 derniers jours" rather than a calendar week: the business's week starts on Sunday,
 * a phone's calendar often on Monday, and a rolling week needs neither.
 */
enum class ReportPeriod(val label: String) {
    AUJOURDHUI("Aujourd'hui"),
    HIER("Hier"),
    SEPT_JOURS("7 derniers jours"),
    CE_MOIS("Ce mois"),
    MOIS_DERNIER("Mois dernier"),
    CETTE_ANNEE("Cette année"),
    PERSONNALISE("Personnalisé"),
}

/** Where the sales of a report were made: everywhere, the dépôt counter, or the camion. */
enum class ReportSource(val label: String, val key: String?) {
    TOUT("Tout", null),
    DEPOT("Dépôt", "depot"),
    CAMION("Camion", "camion"),
}

/**
 * What every report is filtered by, shared between the report screens so changing screen keeps the
 * period. [customFrom] and [customTo] are read only for [ReportPeriod.PERSONNALISE].
 */
data class ReportFilter(
    val period: ReportPeriod = ReportPeriod.CE_MOIS,
    val source: ReportSource = ReportSource.TOUT,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
) {
    /** The local days this filter covers, both included, as of [today]. */
    fun days(today: LocalDate): ClosedRange<LocalDate> = when (period) {
        ReportPeriod.AUJOURDHUI -> today..today
        ReportPeriod.HIER -> today.minusDays(1).let { it..it }
        ReportPeriod.SEPT_JOURS -> today.minusDays(6)..today
        ReportPeriod.CE_MOIS -> today.withDayOfMonth(1)..today
        ReportPeriod.MOIS_DERNIER -> today.minusMonths(1).withDayOfMonth(1).let { it..it.plusMonths(1).minusDays(1) }
        ReportPeriod.CETTE_ANNEE -> today.withDayOfYear(1)..today
        ReportPeriod.PERSONNALISE -> {
            val from = customFrom ?: today
            val to = customTo ?: from
            if (to < from) to..from else from..to
        }
    }

    /** This filter resolved for a query, as of [today] in [zone]. */
    fun resolve(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): ReportRange {
        val days = days(today)
        return ReportRange(
            firstDay = days.start,
            lastDay = days.endInclusive,
            start = BusinessDates.dayStart(days.start, zone),
            end = BusinessDates.dayStart(days.endInclusive.plusDays(1), zone),
            source = source.key,
        )
    }
}

/**
 * A filter ready for SQL. Instants (`ventes.created_at`) are selected with [start] and [end], half-open
 * `[start, end)`, as docs/data/timestamps.md rule 4 says. Calendar dates (`retour_client.date`) are
 * selected with [firstDay] and [lastDay], both included. [source] is `depot`, `camion`, or null for both.
 */
data class ReportRange(
    val firstDay: LocalDate,
    val lastDay: LocalDate,
    val start: String,
    val end: String,
    val source: String?,
)
