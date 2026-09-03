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
    val unite_par_colis : Int = 1,
    // Carried so that editing a bon round-trips what was recorded instead of writing defaults
    // back over it. `quantity` is the product of the two colis fields, not a replacement for
    // them: a line of 25 pièces is "1 colis × 25", and collapsing it to "25 colis × 1" changes
    // what the stored bon says even though the total is unchanged.
    val has_expiry   : Boolean = false,
    val expiry_date  : String? = null   // "yyyy-MM-dd"
)
