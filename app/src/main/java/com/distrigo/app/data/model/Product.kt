package com.distrigo.app.data.model

data class Product(
    val id          : Int,
    val name        : String,
    val barcode     : String?,
    val selling_price  : Double,
    val purchase_price : Double,
    val stock       : Double,
    val min_stock   : Int,
    val unit_type   : String,
    val packages    : Int,
    val pack_size   : Int,
    val has_expiry  : Int,
    val expiry_date : String?,
    val image_uri   : String?,
    val category_name : String?,
    val category_id    : Int?,
    val supplier_name  : String?,
    val supplier_id    : Int?,
    val camion_stock: Double = 0.0



)