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
    val chauffeur      : String?,
    val vehicule       : String?,
    val clients_count  : Int? = null,
    val ventes_count   : Int? = null,
    val total_ventes   : Double? = null,
    val reste_total    : Double? = null,
    val ventes         : List<Vente>? = null
)