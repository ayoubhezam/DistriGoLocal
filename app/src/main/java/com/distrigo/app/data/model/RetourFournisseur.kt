package com.distrigo.app.data.model

data class RetourFournisseurItem(
    val id           : Int,
    val product_id   : Int,
    val product_name : String,
    val unit_type    : String,
    val quantity     : Double,
    val unit_price   : Double,
    val total_price  : Double
)

data class RetourFournisseur(
    val id            : Int,
    val supplier_id   : Int,
    val supplier_name : String,
    val date          : String,
    val motif         : String?,
    val note          : String?,
    val total         : Double,
    val created_at    : String,
    val items_count   : Int? = null,
    val items         : List<RetourFournisseurItem>? = null
)