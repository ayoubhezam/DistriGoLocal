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

/** One sale of a day, as the day's list shows it. */
data class DaySale(
    val id: Int, val numero: String?, val client_name: String?, val source: String,
    val total: Double, val montant_paye: Double, val created_at: String,
)

/** A period's client returns, counted and summed at their selling prices. */
data class ReturnTotals(val count: Int, val total: Double)

// ── Créances et dettes ──

/** What a side is owed, or owes, today: how many parties carry a balance above zero, and its sum. */
data class DebtTotals(val count: Int, val total: Double)

/** Payments over a period, counted and summed. */
data class PeriodPayments(val count: Int, val total: Double)

/**
 * The credit one party's unpaid documents left, in one age band: 0 within 30 days, 1 from 31 to 60,
 * 2 from 61 to 90, 3 beyond. The repository spends a party's balance on these newest first.
 */
data class DebtAge(val party: Int, val band: Int, val credit: Double)

/** A party with a balance above zero, and when it last paid — an instant for a client or a supplier. */
data class Debtor(val id: Int, val name: String, val balance: Double, val last_payment: String?)

/** One product's sales over a period, as ProductReport reads them. */
data class ProductSalesRow(
    val product_id: Int, val name: String, val unit: String, val image_uri: String?,
    val category: String?, val brand: String?, val supplier: String?,
    val quantity: Double, val total: Double, val cost: Double,
)

/** A product in stock, with what one unit cost, for the report's "Sans vente". */
data class StockedProductRow(val id: Int, val name: String, val unit: String, val image_uri: String?, val stock: Double, val purchase_price: Double)

/** The stock's worth at the dépôt and in the camion, and how many products hold some where looked at. */
data class StockValueRow(val depot: Double, val camion: Double, val products: Int)

/** A product out of stock or under its minimum. */
data class RestockRow(val id: Int, val name: String, val unit: String, val image_uri: String?, val stock: Double, val min_stock: Double)

data class LossTypeRow(val type: String, val count: Int, val value: Double)
data class LossProductRow(val product_id: Int, val name: String, val unit: String, val image_uri: String?, val quantity: Double, val value: Double)

@Dao
interface ReportDao {

    /**
     * What the stock is worth today, at purchase price: the dépôt's share and the camion's, each only
     * where it is above zero — a stock sold below zero is owed goods, not worth — and how many
     * products hold some where [source] says (both when null).
     */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN :source IS NULL OR :source = 'depot' THEN MAX(stock - camion_stock, 0) * purchase_price ELSE 0 END), 0) AS depot,
               COALESCE(SUM(CASE WHEN :source IS NULL OR :source = 'camion' THEN MAX(camion_stock, 0) * purchase_price ELSE 0 END), 0) AS camion,
               COALESCE(SUM(CASE :source WHEN 'depot' THEN stock - camion_stock > 0.0005 WHEN 'camion' THEN camion_stock > 0.0005 ELSE stock > 0.0005 END), 0) AS products
        FROM products WHERE deleted_at IS NULL
        """
    )
    suspend fun stockValue(source: String?): StockValueRow

    /**
     * The products to restock where [source] says: out of stock — of those that have a minimum or were
     * ever stocked — or under their minimum. Most urgent first: the emptiest, then furthest below.
     */
    @Query(
        """
        SELECT id, name, unit_type AS unit, image_uri,
               CASE :source WHEN 'depot' THEN stock - camion_stock WHEN 'camion' THEN camion_stock ELSE stock END AS stock,
               min_stock
        FROM products
        WHERE deleted_at IS NULL
          AND (CASE :source WHEN 'depot' THEN stock - camion_stock WHEN 'camion' THEN camion_stock ELSE stock END) < MAX(min_stock, 0.0005)
          AND (min_stock > 0 OR EXISTS (SELECT 1 FROM stock_movements m WHERE m.product_id = products.id))
        ORDER BY stock ASC, min_stock DESC
        """
    )
    suspend fun restock(source: String?): List<RestockRow>

    /** The period's pertes by type, from [source] or both, costliest first. */
    @Query(
        """
        SELECT type_name AS type, COUNT(*) AS count, COALESCE(SUM(valeur_totale), 0) AS value
        FROM pertes
        WHERE date_time >= :start AND date_time < :end AND (:source IS NULL OR source = :source)
        GROUP BY type_name ORDER BY value DESC
        """
    )
    suspend fun lossesByType(start: String, end: String, source: String?): List<LossTypeRow>

    /** The period's pertes by product, costliest first: what each lost, in its unit, and what it cost. */
    @Query(
        """
        SELECT l.product_id AS product_id, COALESCE(p.name, MAX(l.product_name)) AS name, MAX(l.unit) AS unit, p.image_uri AS image_uri,
               SUM(l.quantity) AS quantity, COALESCE(SUM(l.valeur_totale), 0) AS value
        FROM pertes l LEFT JOIN products p ON p.id = l.product_id
        WHERE l.date_time >= :start AND l.date_time < :end AND (:source IS NULL OR l.source = :source)
        GROUP BY l.product_id ORDER BY value DESC
        """
    )
    suspend fun lossesByProduct(start: String, end: String, source: String?): List<LossProductRow>

    /**
     * Each product sold between [start] and [end], from [source] or both: its quantity, what its lines
     * sold for and what they had cost — each at the purchase price it was sold at. Named, grouped and
     * pictured as the product is today; a product gone for good keeps the name its lines carry.
     */
    @Query(
        """
        SELECT i.product_id AS product_id, COALESCE(p.name, MAX(i.product_name)) AS name,
               COALESCE(p.unit_type, MAX(i.unit_type)) AS unit, p.image_uri AS image_uri,
               p.category_name AS category, p.marque_name AS brand, p.supplier_name AS supplier,
               SUM(i.quantity) AS quantity, COALESCE(SUM(i.total_price), 0) AS total,
               COALESCE(SUM(i.quantity * i.purchase_price_snapshot), 0) AS cost
        FROM vente_items i
        JOIN ventes v ON v.id = i.vente_id
        LEFT JOIN products p ON p.id = i.product_id
        WHERE v.created_at >= :start AND v.created_at < :end AND (:source IS NULL OR v.source = :source)
        GROUP BY i.product_id
        """
    )
    suspend fun productSales(start: String, end: String, source: String?): List<ProductSalesRow>

    /**
     * The live products holding stock where [source] says — the dépôt's share, the camion's, or all of
     * it when [source] is null — for the report to keep those that did not sell.
     */
    @Query(
        """
        SELECT id, name, unit_type AS unit, image_uri,
               CASE :source WHEN 'depot' THEN stock - camion_stock WHEN 'camion' THEN camion_stock ELSE stock END AS stock,
               purchase_price
        FROM products
        WHERE deleted_at IS NULL
          AND (CASE :source WHEN 'depot' THEN stock - camion_stock WHEN 'camion' THEN camion_stock ELSE stock END) > 0.0005
        """
    )
    suspend fun stockedProducts(source: String?): List<StockedProductRow>

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

    /** The sales between [start] and [end], newest first — one local day, from the Ventes report. */
    @Query(
        """
        SELECT id, numero, client_name, source, total, montant_paye, created_at FROM ventes
        WHERE created_at >= :start AND created_at < :end AND (:source IS NULL OR source = :source)
        ORDER BY created_at DESC, id DESC
        """
    )
    suspend fun salesBetween(start: String, end: String, source: String?): List<DaySale>

    // ── Créances et dettes ──
    // Balances are the stored caches ClientDao and SupplierDao.recomputeBalance keep; a balance above
    // half a centime is owed. Parties in the bin are left out: one with a balance cannot be put there.

    @Query("SELECT COUNT(*) AS count, COALESCE(SUM(balance), 0) AS total FROM clients WHERE deleted_at IS NULL AND balance > 0.005")
    suspend fun clientDebts(): DebtTotals

    @Query("SELECT COUNT(*) AS count, COALESCE(SUM(balance), 0) AS total FROM suppliers WHERE deleted_at IS NULL AND balance > 0.005")
    suspend fun supplierDebts(): DebtTotals

    /** The credit left by the period's sales: what each was not paid at the moment it was made. */
    @Query("SELECT COALESCE(SUM(MAX(total - montant_paye, 0)), 0) FROM ventes WHERE created_at >= :start AND created_at < :end")
    suspend fun creditGiven(start: String, end: String): Double

    /** The credit left by the period's purchases, by the bon's date — a calendar day, both ends included. */
    @Query("SELECT COALESCE(SUM(MAX(total - montant_paye, 0)), 0) FROM purchase_orders WHERE date >= :firstDay AND date <= :lastDay")
    suspend fun creditTaken(firstDay: String, lastDay: String): Double

    @Query("SELECT COUNT(*) AS count, COALESCE(SUM(amount), 0) AS total FROM client_payments WHERE created_at >= :start AND created_at < :end")
    suspend fun clientPayments(start: String, end: String): PeriodPayments

    @Query("SELECT COUNT(*) AS count, COALESCE(SUM(amount), 0) AS total FROM supplier_payments WHERE created_at >= :start AND created_at < :end")
    suspend fun supplierPayments(start: String, end: String): PeriodPayments

    @Query("SELECT COUNT(*) AS count, COALESCE(SUM(total), 0) AS total FROM retour_fournisseur WHERE date >= :firstDay AND date <= :lastDay")
    suspend fun supplierReturns(firstDay: String, lastDay: String): ReturnTotals

    /**
     * Each debtor client's unpaid sale credit, by age band. [b30], [b60] and [b90] are the instants
     * 30, 60 and 90 local days before today began (BusinessDates.dayStart).
     */
    @Query(
        """
        SELECT v.client_id AS party,
               CASE WHEN v.created_at >= :b30 THEN 0 WHEN v.created_at >= :b60 THEN 1 WHEN v.created_at >= :b90 THEN 2 ELSE 3 END AS band,
               SUM(v.total - v.montant_paye) AS credit
        FROM ventes v JOIN clients c ON c.id = v.client_id
        WHERE c.deleted_at IS NULL AND c.balance > 0.005 AND v.total > v.montant_paye
        GROUP BY party, band
        """
    )
    suspend fun clientDebtAges(b30: String, b60: String, b90: String): List<DebtAge>

    /** The same for suppliers, by the bon's calendar date: [d30], [d60] and [d90] are `yyyy-MM-dd`. */
    @Query(
        """
        SELECT p.supplier_id AS party,
               CASE WHEN p.date >= :d30 THEN 0 WHEN p.date >= :d60 THEN 1 WHEN p.date >= :d90 THEN 2 ELSE 3 END AS band,
               SUM(p.total - p.montant_paye) AS credit
        FROM purchase_orders p JOIN suppliers s ON s.id = p.supplier_id
        WHERE s.deleted_at IS NULL AND s.balance > 0.005 AND p.total > p.montant_paye
        GROUP BY party, band
        """
    )
    suspend fun supplierDebtAges(d30: String, d60: String, d90: String): List<DebtAge>

    @Query(
        """
        SELECT c.id, c.name, c.balance,
               (SELECT MAX(p.created_at) FROM client_payments p WHERE p.client_id = c.id) AS last_payment
        FROM clients c WHERE c.deleted_at IS NULL AND c.balance > 0.005
        ORDER BY c.balance DESC
        """
    )
    suspend fun clientDebtors(): List<Debtor>

    @Query(
        """
        SELECT s.id, s.name, s.balance,
               (SELECT MAX(p.created_at) FROM supplier_payments p WHERE p.supplier_id = s.id) AS last_payment
        FROM suppliers s WHERE s.deleted_at IS NULL AND s.balance > 0.005
        ORDER BY s.balance DESC
        """
    )
    suspend fun supplierDebtors(): List<Debtor>
}
