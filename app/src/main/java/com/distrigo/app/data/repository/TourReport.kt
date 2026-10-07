package com.distrigo.app.data.repository

import com.distrigo.app.data.model.report.ReportRange
import java.time.LocalDate

/**
 * One tournée of the period: its name and day, whether it is still open, how many clients it planned
 * and visited, and its sales — how many, to how many clients, what they came to and what was paid at
 * the moment of the sale.
 */
data class TourFigures(
    val id: Int,
    val name: String,
    val day: LocalDate?,
    val open: Boolean,
    val planned: Int,
    val visited: Int,
    val sales: Int,
    val buyers: Int,
    val total: Double,
    val paid: Double,
)

/** The Tournées report: the tournées started over the period, newest first. */
data class TourReport(val range: ReportRange, val tours: List<TourFigures>) {
    val total: Double get() = tours.sumOf { it.total }
    val paid: Double get() = tours.sumOf { it.paid }
    val sales: Int get() = tours.sumOf { it.sales }
    val planned: Int get() = tours.sumOf { it.planned }
    val visited: Int get() = tours.sumOf { it.visited }
    val openCount: Int get() = tours.count { it.open }
    /** Clients visited over clients planned, all tournées together. */
    val visitRate: Double? get() = if (planned > 0) visited.toDouble() / planned else null
    /** What was paid at the moment of the sales, over what they came to. */
    val paidRate: Double? get() = if (total > 0) paid / total else null
    /** What a tournée sells on average. */
    val perTour: Double? get() = if (tours.isNotEmpty()) total / tours.size else null
}
