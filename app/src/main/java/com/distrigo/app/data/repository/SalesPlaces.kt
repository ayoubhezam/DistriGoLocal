package com.distrigo.app.data.repository

import com.distrigo.app.data.local.dao.ClientPlaceSalesRow
import java.text.Collator
import java.util.Locale

/** The levels of « Répartition des ventes », from the whole business down to a client. */
enum class PlaceLevel(val one: String, val many: String) {
    ALL("ensemble", "ensembles"),
    WILAYA("wilaya", "wilayas"),
    COMMUNE("commune", "communes"),
    SECTEUR("secteur", "secteurs"),
    QUARTIER("quartier", "quartiers"),
    CLIENT("client", "clients");

    /** The level under this one; a client has none. */
    val next: PlaceLevel? get() = entries.getOrNull(ordinal + 1)
}

/**
 * A place's sales over the report's period: [total] and [sales] of its clients, and its [children] — the places
 * one level down — the largest first. [key] is unique in the whole tree; [clientId] is set on a client only.
 */
data class PlaceNode(
    val key: String,
    val name: String,
    val level: PlaceLevel,
    val total: Double,
    val sales: Int,
    val children: List<PlaceNode> = emptyList(),
    val clientId: Int? = null,
) {
    /** The node with [key] here or below, null when none. */
    fun find(key: String): PlaceNode? =
        if (this.key == key) this else children.firstNotNullOfOrNull { it.find(key) }
}

/** What a level is called when its clients have none. */
const val NO_WILAYA = "Sans wilaya"
const val NO_COMMUNE_NAME = "Sans commune"
const val NO_SECTEUR = "Sans secteur"
const val NO_QUARTIER = "Sans quartier"

/**
 * The report's sales by place: wilaya › commune › secteur › quartier › client, from one row per client who bought.
 * Every level adds up to the one above it, and the root to the report's « Chiffre d'affaires »: a client with no wilaya,
 * commune, secteur or quartier counts under « Sans … » at that level, never nowhere.
 *
 * A wilaya and a commune are told apart by name (a commune within its wilaya); a secteur and a quartier by id, so two
 * secteurs with one name stay two.
 */
fun salesPlaces(rows: List<ClientPlaceSalesRow>): PlaceNode {
    fun text(value: String?) = value?.trim()?.takeIf { it.isNotEmpty() }

    data class Placed(val row: ClientPlaceSalesRow, val path: List<Pair<String, String>>)

    val placed = rows.map { row ->
        val wilaya = text(row.wilaya)
        val commune = text(row.commune)
        val wKey = "w:" + (wilaya?.lowercase() ?: "-")
        val cKey = "$wKey|c:" + (commune?.lowercase() ?: "-")
        val sKey = "$cKey|s:" + (row.secteur_id?.toString() ?: "-")
        val qKey = "$sKey|q:" + (row.quartier_id?.toString() ?: "-")
        Placed(
            row,
            listOf(
                wKey to (wilaya ?: NO_WILAYA),
                cKey to (commune ?: NO_COMMUNE_NAME),
                sKey to (if (row.secteur_id == null) NO_SECTEUR else text(row.secteur) ?: NO_SECTEUR),
                qKey to (if (row.quartier_id == null) NO_QUARTIER else text(row.quartier) ?: NO_QUARTIER),
            ),
        )
    }
    val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.PRIMARY }
    val order = compareByDescending<PlaceNode> { it.total }.thenBy(collator) { it.name }

    fun build(group: List<Placed>, depth: Int, key: String, name: String, level: PlaceLevel): PlaceNode {
        val children = if (depth == 4) {
            group.map { p ->
                PlaceNode(
                    "$key|k:${p.row.client_id}", text(p.row.name) ?: "Client supprimé", PlaceLevel.CLIENT,
                    p.row.total, p.row.count, clientId = p.row.client_id,
                )
            }
        } else {
            group.groupBy { it.path[depth].first }.map { (childKey, members) ->
                build(members, depth + 1, childKey, members.first().path[depth].second, PlaceLevel.entries[depth + 1])
            }
        }
        return PlaceNode(key, name, level, group.sumOf { it.row.total }, group.sumOf { it.row.count }, children.sortedWith(order))
    }
    return build(placed, 0, "all", "Wilayas", PlaceLevel.ALL)
}

/** A piece of a place's ring: one child, or « Autres » gathering [others]. */
data class PlaceSlice(
    val node: PlaceNode?,
    val name: String,
    val total: Double,
    /** Its part of the place's total, 0 to 1. */
    val share: Double,
    val others: List<PlaceNode> = emptyList(),
) {
    val isOthers: Boolean get() = node == null
}

/** How many children a ring shows on their own before « Autres » (owner's prototype). */
const val RING_SLICES = 5

/**
 * [place]'s ring: its [RING_SLICES] largest children, then « Autres » for the rest when there are more. Shares are of
 * the place's total; a place without sales has none.
 */
fun ringSlices(place: PlaceNode, limit: Int = RING_SLICES): List<PlaceSlice> {
    val whole = place.total
    fun share(v: Double) = if (whole > 0) v / whole else 0.0
    val kids = place.children
    if (kids.size <= limit) return kids.map { PlaceSlice(it, it.name, it.total, share(it.total)) }
    val rest = kids.drop(limit)
    val othersTotal = rest.sumOf { it.total }
    return kids.take(limit).map { PlaceSlice(it, it.name, it.total, share(it.total)) } +
        PlaceSlice(null, "Autres", othersTotal, share(othersTotal), rest)
}
