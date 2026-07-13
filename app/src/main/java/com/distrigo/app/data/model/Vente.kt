package com.distrigo.app.data.model

data class Vente(
    val id           : Int,
    val client_id    : Int,
    val client_name  : String,
    val tournee_id   : Int?,
    val source       : String,
    val total        : Double,
    val montant_paye : Double?,
    val status       : String,
    val note         : String?,
    val created_at   : String?,
    val items_count  : Int? = null,
    val items        : List<VenteItem>? = null
)

data class VenteItem(
    val id           : Int,
    val product_id   : Int,
    val product_name : String,
    val unit_type    : String,
    val quantity     : Int,
    val unit_price   : Double,
    val total_price  : Double
)