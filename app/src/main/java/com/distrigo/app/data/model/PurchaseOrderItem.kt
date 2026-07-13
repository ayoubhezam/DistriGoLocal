package com.distrigo.app.data.model

data class PurchaseOrderItem(
    val id           : Int,
    val quantity     : Int,
    val unit_cost    : Double,
    val total_cost   : Double,
    val product_id   : Int,
    val product_name : String,
    val unit_type    : String,
    val nb_colis : Int = 1,
    val unite_par_colis : Int = 1
)