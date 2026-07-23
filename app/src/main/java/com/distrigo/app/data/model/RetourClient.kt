package com.distrigo.app.data.model

data class RetourClientItem(
    val id           : Int,
    val product_id   : Int,
    val product_name : String,
    val unit_type    : String,
    val quantity     : Double,
    val unit_price   : Double,
    val total_price  : Double
)

data class RetourClient(
    val id          : Int,
    val client_id   : Int,
    val client_name : String,
    val tournee_id  : Int?,
    val date        : String,
    val motif       : String?,
    val note        : String?,
    val total       : Double,
    val created_at  : String,
    val items_count : Int? = null,
    val items       : List<RetourClientItem>? = null
)