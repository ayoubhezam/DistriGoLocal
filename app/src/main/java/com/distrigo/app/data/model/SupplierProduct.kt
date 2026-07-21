package com.distrigo.app.data.model

data class SupplierProduct(
    val id             : Int,
    val name           : String,
    val stock: Double,
    val unit_type      : String,
    val purchase_price : Double,
    val is_default     : Int
)