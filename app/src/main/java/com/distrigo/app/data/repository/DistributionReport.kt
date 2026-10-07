package com.distrigo.app.data.repository

import java.time.LocalDate

/**
 * A sector over the period: its [clients], those [served] — at least one sale — those [withoutSale],
 * and [rate], served ÷ clients, 0 without a client.
 */
data class SectorStat(
    val id: Int,
    val name: String,
    val commune: String,
    val clients: Int,
    val served: Int,
    val withoutSale: Int,
    val rate: Double,
)

/** A commune over the period, as [SectorStat]; [name] is null for the clients without a commune. */
data class CommuneStat(
    val name: String?,
    val clients: Int,
    val served: Int,
    val withoutSale: Int,
    val sectors: Int,
    val rate: Double,
)

/** A client of a sector who bought nothing over the period; [lastSale], its last sale ever, if any. */
data class UnservedClient(val id: Int, val name: String, val imageUri: String?, val lastSale: LocalDate?)

/**
 * Where the clients are, and which of them bought — the Ventes report's distribution. A commune —
 * chosen by its name; the wilaya is left out — narrows the figures and the sectors, not [communes].
 *
 * - [clients]: the clients there by the period's end, not deleted, in the commune when one is chosen;
 * - [served]: those of them with at least one sale over the period (dépôt, camion, or both);
 * - [rate]: served ÷ clients, 0 without a client;
 * - [unsectored]: the clients with no sector, which no sector bar shows;
 * - [sectors]: every sector, the most clients first; [communes]: every commune, whichever is chosen,
 *   the most clients first, those without a commune last.
 *
 * Counted, divided and sorted by ReportDao; nothing here adds up.
 */
data class DistributionReport(
    val clients: Int,
    val served: Int,
    val rate: Double,
    val unsectored: Int,
    val sectors: List<SectorStat>,
    val communes: List<CommuneStat>,
) {
    /** The sectors a bar is drawn for: the [TOP_SECTORS] with the most clients, none without a client. */
    val topSectors: List<SectorStat> get() = sectors.filter { it.clients > 0 }.take(TOP_SECTORS)

    /** The communes a selector offers, by name: those with clients, without the clients without one. */
    val communeNames: List<String> get() = communes.mapNotNull { it.name }.sortedWith(String.CASE_INSENSITIVE_ORDER)

    companion object {
        const val TOP_SECTORS = 7
    }
}
