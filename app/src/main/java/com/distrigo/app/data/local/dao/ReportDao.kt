package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query

// The Rapports screens' figures, computed in SQL: each query returns a handful of rows however many
// sales the period holds (audit item C9's rule). Instants are selected with half-open bounds from
// ReportFilter.resolve — `created_at >= :start AND created_at < :end` — which the `created_at` and
// `(source, created_at)` indices on `ventes` serve. `:source` is 'depot', 'camion', or null for both.

/** A period's sales from one source: counted, with totals and the part paid at the sale summed. */
data class SalesBySource(val source: String, val count: Int, val total: Double, val paid: Double)

/**
 * The cost of a period's sales lines, at the purchase price each was sold at. [estimated] is the part
 * of [cost] on lines sold before that price was recorded (MIGRATION_59_60), and so only approximate.
 */
data class SalesCost(val cost: Double, val estimated: Double)

/**
 * A period's sales in one UTC hour, `yyyy-MM-ddTHH`, from one source. Grouped by hour, not day,
 * because the day a sale belongs to is local (docs/data/timestamps.md, rule 3), and SQLite cannot
 * convert to the phone's zone reliably; the repository folds the hours into local days.
 */
data class SalesHour(val hour: String, val source: String, val count: Int, val total: Double, val paid: Double)

/** A period's client returns, counted and summed at their selling prices. */
data class ReturnTotals(val count: Int, val total: Double)

@Dao
interface ReportDao {

    @Query(
        """
        SELECT source, COUNT(*) AS count, COALESCE(SUM(total), 0) AS total,
               COALESCE(SUM(MIN(montant_paye, total)), 0) AS paid
        FROM ventes
        WHERE created_at >= :start AND created_at < :end AND (:source IS NULL OR source = :source)
        GROUP BY source
        """
    )
    suspend fun salesBySource(start: String, end: String, source: String?): List<SalesBySource>

    @Query(
        """
        SELECT COALESCE(SUM(i.quantity * i.purchase_price_snapshot), 0) AS cost,
               COALESCE(SUM(CASE WHEN i.cost_estimated THEN i.quantity * i.purchase_price_snapshot ELSE 0 END), 0) AS estimated
        FROM vente_items i JOIN ventes v ON v.id = i.vente_id
        WHERE v.created_at >= :start AND v.created_at < :end AND (:source IS NULL OR v.source = :source)
        """
    )
    suspend fun salesCost(start: String, end: String, source: String?): SalesCost

    @Query(
        """
        SELECT COUNT(DISTINCT client_id) FROM ventes
        WHERE created_at >= :start AND created_at < :end AND (:source IS NULL OR source = :source)
        """
    )
    suspend fun clientsServed(start: String, end: String, source: String?): Int

    // `substr(…, 1, 13)` reads the fixed-width `yyyy-MM-ddTHH` every stored instant begins with; only
    // the fractional digits after the seconds vary in width.
    @Query(
        """
        SELECT substr(created_at, 1, 13) AS hour, source, COUNT(*) AS count, COALESCE(SUM(total), 0) AS total,
               COALESCE(SUM(MIN(montant_paye, total)), 0) AS paid
        FROM ventes
        WHERE created_at >= :start AND created_at < :end AND (:source IS NULL OR source = :source)
        GROUP BY hour, source
        """
    )
    suspend fun salesByHour(start: String, end: String, source: String?): List<SalesHour>

    /** Returns carry a calendar date, local, so they are selected by day: [firstDay] to [lastDay], both included. */
    @Query(
        """
        SELECT COUNT(*) AS count, COALESCE(SUM(total), 0) AS total FROM retour_client
        WHERE date >= :firstDay AND date <= :lastDay
        """
    )
    suspend fun clientReturns(firstDay: String, lastDay: String): ReturnTotals
}
