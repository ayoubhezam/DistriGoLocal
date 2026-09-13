package com.distrigo.app.data.model

data class Tournee(
    val id             : Int,
    val session_id     : Int,
    val status         : String,
    val date_debut     : String?,
    val date_fin       : String?,
    val note           : String?,
    val nom            : String,
    val wilaya_id      : Int?,
    val commune_id     : Int?,
    val wilaya_name    : String?,
    val commune_name   : String?,
    val secteurs       : List<TourneeSecteur> = emptyList(),
    val clients_count  : Int? = null,
    val ventes_count   : Int? = null,
    val total_ventes   : Double? = null,
    val reste_total    : Double? = null,
    val ventes         : List<Vente>? = null
)

/**
 * How a tournée names its ground: the commune, then its secteurs in parentheses.
 *
 *     Souk Ahras                                 — no secteur
 *     Souk Ahras (secteur 01)                    — one
 *     Souk Ahras (secteur 01, secteur 02)        — two, and so on
 *
 * Shared by the Tournée card and the detail screen's top bar so the two never drift. Falls back to
 * [nom] when no commune was recorded — `nom` is set to the commune name on creation, so for every
 * tournée made through the form the two agree anyway.
 */
fun Tournee.locationLabel(): String {
    val base = commune_name?.takeIf { it.isNotBlank() } ?: nom
    if (secteurs.isEmpty()) return base
    return "$base (${secteurs.joinToString(", ") { it.nom }})"
}
