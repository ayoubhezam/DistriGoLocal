package com.distrigo.app.data.model

data class PurchaseOrderItem(
    val id           : Int,
    val quantity     : Double,
    val unit_cost    : Double,
    val total_cost   : Double,
    val product_id   : Int,
    val product_name : String,
    val unit_type    : String,
    val nb_colis : Double = 1.0,
    val unite_par_colis : Int = 1
)