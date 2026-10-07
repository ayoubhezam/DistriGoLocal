package com.distrigo.app.data.repository

import com.distrigo.app.data.local.dao.ReportDao
import com.distrigo.app.data.local.dao.SalesHour
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Sales from one place, or from both: counted, totalled, and split into paid at the sale and left on credit. */
data class SalesFigures(val count: Int, val total: Double, val paid: Double) {
    val credit: Double get() = total - paid

    operator fun plus(other: SalesFigures) =
        SalesFigures(count + other.count, total + other.total, paid + other.paid)

    companion object {
        val ZERO = SalesFigures(0, 0.0, 0.0)
    }
}

/** One local day of a sales report. Every day of the period has one, a day without sales included. */
data class SalesDay(val day: LocalDate, val depot: SalesFigures, val camion: SalesFigures) {
    val all: SalesFigures get() = depot + camion
}

/**
 * The Ventes report for one filter.
 *
 * [cost] is what the goods sold cost, at the purchase price each line was sold at; [estimatedCost] is
 * the part of it guessed for lines sold before that price was recorded, so a margin that leans on it is
 * shown as approximate.
 *
 * [returns] is null when the report is limited to the dépôt or the camion: a return does not record
 * where the goods were sold, so it cannot be put on either side.
 */
data class SalesReport(
    val range: ReportRange,
    val depot: SalesFigures,
    val camion: SalesFigures,
    val clientsServed: Int,
    val cost: Double,
    val estimatedCost: Double,
    val returns: ReturnFigures?,
    val days: List<SalesDay>,
) {
    val all: SalesFigures get() = depot + camion
    val grossMargin: Double get() = all.total - cost
    /**
     * The margin as a share of what the goods cost — (ventes − coût) ÷ coût, the rate the Direction du
     * Commerce reasons in — or null with no cost to measure it against.
     */
    val marginRate: Double? get() = if (cost > 0) grossMargin / cost else null
    val isMarginEstimated: Boolean get() = estimatedCost > 0
    /** Sales less the returns, or null when [returns] is. */
    val netTotal: Double? get() = returns?.let { all.total - it.total }
}

data class ReturnFigures(val count: Int, val total: Double)

/**
 * Reads the Rapports figures: SQL aggregates from ReportDao, put together for one ReportFilter.
 *
 * [writes] tells which of the given tables were written — Room's invalidation tracker in the app. A
 * report reloads when its own tables change, not each time its screen reappears: coming back from a
 * client opened from a report runs no query unless something was recorded meanwhile.
 */
class ReportRepository(
    private val dao: ReportDao,
    private val writes: (Array<String>) -> Flow<Set<String>> = { emptyFlow() },
) {

    /** A signal each time the Ventes report's tables are written. */
    fun salesChanges(): Flow<Unit> = writes(SALES_TABLES).map { }

    /** A signal each time the Créances et dettes report's tables are written. */
    fun debtChanges(): Flow<Unit> = writes(DEBT_TABLES).map { }

    suspend fun salesReport(
        filter: ReportFilter,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): SalesReport {
        val range = filter.resolve(today, zone)
        val bySource = dao.salesBySource(range.start, range.end, range.source).associateBy { it.source }
        fun figures(source: String) = bySource[source]?.let { SalesFigures(it.count, it.total, it.paid) } ?: SalesFigures.ZERO
        val cost = dao.salesCost(range.start, range.end, range.source)
        val returns = if (range.source == null) {
            dao.clientReturns(range.firstDay.toString(), range.lastDay.toString()).let { ReturnFigures(it.count, it.total) }
        } else null

        return SalesReport(
            range = range,
            depot = figures("depot"),
            camion = figures("camion"),
            clientsServed = dao.clientsServed(range.start, range.end, range.source),
            cost = cost.cost,
            estimatedCost = cost.estimated,
            returns = returns,
            days = foldIntoDays(dao.salesByHour(range.start, range.end, range.source), range, zone),
        )
    }

    /** A signal each time the Produits report's tables are written. */
    fun productChanges(): Flow<Unit> = writes(PRODUCT_TABLES).map { }

    /** The Produits report: what each product sold, and the stocked ones that did not. */
    suspend fun productReport(
        filter: ReportFilter,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ProductReport {
        val range = filter.resolve(today, zone)
        val lines = dao.productSales(range.start, range.end, range.source).map {
            ProductSales(it.product_id, it.name, it.unit, it.image_uri, it.category, it.brand, it.supplier, it.quantity, it.total, it.cost)
        }
        val sold = lines.mapTo(HashSet()) { it.productId }
        val dormant = dao.stockedProducts(range.source)
            .filter { it.id !in sold }
            .map { DormantProduct(it.id, it.name, it.unit, it.image_uri, it.stock, it.stock * it.purchase_price) }
            .sortedByDescending { it.value }
        return ProductReport(range, lines, dormant)
    }

    /** A signal each time the Stock et pertes report's tables are written. */
    fun stockChanges(): Flow<Unit> = writes(STOCK_TABLES).map { }

    /** The Stock et pertes report: the stock as of today, the period's pertes. */
    suspend fun stockReport(
        filter: ReportFilter,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): StockReport {
        val range = filter.resolve(today, zone)
        val value = dao.stockValue(range.source)
        return StockReport(
            range = range,
            stock = StockValue(value.depot, value.camion, value.products),
            restock = dao.restock(range.source).map { RestockLine(it.id, it.name, it.unit, it.image_uri, it.stock, it.min_stock) },
            losses = dao.lossesByType(range.start, range.end, range.source).map { LossByType(it.type, it.count, it.value) },
            lostProducts = dao.lossesByProduct(range.start, range.end, range.source)
                .map { LossByProduct(it.product_id, it.name, it.unit, it.image_uri, it.quantity, it.value) },
            salesCost = dao.salesCost(range.start, range.end, range.source).cost,
        )
    }

    /** A signal each time the Clients et fournisseurs report's tables are written. */
    fun partyChanges(): Flow<Unit> = writes(PARTY_TABLES).map { }

    /** The Clients et fournisseurs report for one [side]. */
    suspend fun partyReport(
        side: DebtSide,
        filter: ReportFilter,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): PartyReport {
        val range = filter.resolve(today, zone)
        if (side == DebtSide.FOURNISSEURS) {
            val parties = dao.supplierPurchases(range.firstDay.toString(), range.lastDay.toString())
                .map { PartyFigures(it.id, it.name, it.image_uri, it.count, it.total) }
            return PartyReport(side, range, parties)
        }
        val costs = dao.clientCosts(range.start, range.end).associate { it.id to it.cost }
        val parties = dao.clientSales(range.start, range.end)
            .map { PartyFigures(it.id, it.name, it.image_uri, it.count, it.total, costs[it.id] ?: 0.0) }
        val inactive = dao.inactiveClients(range.start).map {
            InactiveClient(it.id, it.name, it.image_uri, java.time.Instant.parse(it.last_sale).atZone(zone).toLocalDate(), it.total)
        }
        return PartyReport(side, range, parties, dao.newClients(range.start, range.end), inactive)
    }

    /** A signal each time the Résultat report's tables are written. */
    fun profitChanges(): Flow<Unit> = writes(PROFIT_TABLES).map { }

    /**
     * The Résultat report. The whole business, dépôt and camion together: a charge belongs to neither,
     * so the report is not split by source.
     */
    suspend fun profitReport(
        filter: ReportFilter,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ProfitReport {
        val range = filter.resolve(today, zone)
        val sales = dao.salesBySource(range.start, range.end, null).sumOf { it.total }
        val cost = dao.salesCost(range.start, range.end, null).cost
        val returns = dao.returnsWithCost(range.firstDay.toString(), range.lastDay.toString())
        return ProfitReport(
            range = range,
            sales = sales,
            cost = cost,
            returnsCount = returns.count,
            returns = returns.total,
            returnsCost = returns.cost,
            charges = dao.chargesByType(range.start, range.end).map { ChargeByType(it.type, it.count, it.value) },
            losses = dao.lossesByType(range.start, range.end, null).map { LossByType(it.type, it.count, it.value) },
        )
    }

    /** The sales of one local [day], from [source] or both, newest first. */
    suspend fun salesOfDay(day: LocalDate, source: String?, zone: ZoneId = ZoneId.systemDefault()) =
        dao.salesBetween(
            com.distrigo.app.data.time.BusinessDates.dayStart(day, zone),
            com.distrigo.app.data.time.BusinessDates.dayStart(day.plusDays(1), zone),
            source,
        )

    /** The Créances et dettes report for one [side] — see DebtReport. */
    suspend fun debtReport(
        side: DebtSide,
        filter: ReportFilter,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): DebtReport = dao.debtReport(side, filter, today, zone)
}

/**
 * The UTC hours of [hours] gathered into the local days of [range], each day of the range present.
 *
 * An hour belongs wholly to one local day in every zone whose offset is whole hours, Algeria's included.
 */
internal fun foldIntoDays(hours: List<SalesHour>, range: ReportRange, zone: ZoneId): List<SalesDay> {
    val depot = mutableMapOf<LocalDate, SalesFigures>()
    val camion = mutableMapOf<LocalDate, SalesFigures>()
    for (h in hours) {
        val day = Instant.parse("${h.hour}:00:00Z").atZone(zone).toLocalDate()
        val into = if (h.source == "camion") camion else depot
        into[day] = (into[day] ?: SalesFigures.ZERO) + SalesFigures(h.count, h.total, h.paid)
    }
    return generateSequence(range.firstDay) { it.plusDays(1) }
        .takeWhile { it <= range.lastDay }
        .map { SalesDay(it, depot[it] ?: SalesFigures.ZERO, camion[it] ?: SalesFigures.ZERO) }
        .toList()
}

/** The tables the Ventes report reads. */
private val SALES_TABLES = arrayOf("ventes", "vente_items", "retour_client")

/** The tables the Résultat report reads. */
private val PROFIT_TABLES = arrayOf("ventes", "vente_items", "retour_client", "retour_client_items", "charges", "pertes", "products")

/** The tables the Clients et fournisseurs report reads. */
private val PARTY_TABLES = arrayOf("ventes", "vente_items", "clients", "purchase_orders", "suppliers")

/** The tables the Stock et pertes report reads. */
private val STOCK_TABLES = arrayOf("products", "stock_movements", "pertes", "ventes", "vente_items")

/** The tables the Produits report reads. */
private val PRODUCT_TABLES = arrayOf("ventes", "vente_items", "products")

/** The tables the Créances et dettes report reads. */
private val DEBT_TABLES = arrayOf(
    "clients", "suppliers", "ventes", "purchase_orders",
    "client_payments", "supplier_payments", "retour_client", "retour_fournisseur",
)
