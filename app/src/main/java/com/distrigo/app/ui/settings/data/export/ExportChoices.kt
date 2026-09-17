package com.distrigo.app.ui.settings.data.export

import com.distrigo.app.data.export.ExportDataset
import com.distrigo.app.data.export.ExportFormat
import com.distrigo.app.data.export.ExportPeriod
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The periods offered, most used first. */
enum class PeriodPreset(val label: String) {
    THIS_MONTH("Ce mois-ci"),
    LAST_MONTH("Mois dernier"),
    THIS_YEAR("Cette année"),
    ALL("Tout"),
    CUSTOM("Choisir les dates"),
}

/** What the export screen works out from the user's choices: the period, how it reads, and the file's name and type. */
object ExportChoices {

    private val DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /** The local days [preset] covers on [today]; [from] and [to] are used only by [PeriodPreset.CUSTOM]. */
    fun period(preset: PeriodPreset, today: LocalDate, from: LocalDate? = null, to: LocalDate? = null): ExportPeriod = when (preset) {
        PeriodPreset.THIS_MONTH -> ExportPeriod(today.withDayOfMonth(1), today)
        PeriodPreset.LAST_MONTH -> today.minusMonths(1).let { ExportPeriod(it.withDayOfMonth(1), it.withDayOfMonth(it.lengthOfMonth())) }
        PeriodPreset.THIS_YEAR -> ExportPeriod(today.withDayOfYear(1), today)
        PeriodPreset.ALL -> ExportPeriod.ALL
        PeriodPreset.CUSTOM ->
            // Picked the wrong way round, the two dates still mean the days between them.
            if (from != null && to != null && from.isAfter(to)) ExportPeriod(to, from) else ExportPeriod(from, to)
    }

    /** `du 01/09/2026 au 17/09/2026`, `depuis le 01/09/2026`, `jusqu'au 17/09/2026`, `de toutes les dates`: what follows « Exporte les données ». */
    fun describe(period: ExportPeriod): String {
        val from = period.from
        val to = period.to
        return when {
            from != null && to != null && from == to -> "du ${DAY.format(from)}"
            from != null && to != null -> "du ${DAY.format(from)} au ${DAY.format(to)}"
            from != null -> "depuis le ${DAY.format(from)}"
            to != null -> "jusqu'au ${DAY.format(to)}"
            else -> "de toutes les dates"
        }
    }

    /**
     * `DistriGo-ventes-2026-09-01-au-2026-09-17.xlsx`, or `DistriGo-export-….zip` for several datasets as CSV. Clients
     * and products alone are dated by the day of the export, since they are what they are that day.
     */
    fun fileName(datasets: Set<ExportDataset>, period: ExportPeriod, today: LocalDate, format: ExportFormat): String {
        val what = datasets.singleOrNull()?.fileName ?: "export"
        val `when` = if (datasets.none { it.byPeriod }) today.toString() else when {
            period.from != null && period.to != null && period.from == period.to -> period.from.toString()
            period.from != null && period.to != null -> "${period.from}-au-${period.to}"
            period.from != null -> "depuis-${period.from}"
            period.to != null -> "jusqu-au-${period.to}"
            else -> "tout"
        }
        return "DistriGo-$what-$`when`.${format.extensionFor(datasets.size)}"
    }

    fun mimeType(datasets: Set<ExportDataset>, format: ExportFormat): String = format.mimeTypeFor(datasets.size)
}
