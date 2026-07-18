package com.distrigo.app.data.model

data class PerteType(
    val id           : Int,
    val name         : String,
    val icon         : String,
    val color_hex    : String,
    val is_default   : Boolean,
    val count        : Int = 0,
    val total_value  : Double = 0.0,
    val total_qty    : Int = 0,
    val description : String?
)

data class Perte(
    val id                     : Int,
    val type_id                : Int,
    val type_name              : String,
    val product_id             : Int,
    val product_name           : String,
    val product_image_uri      : String?,
    val quantity                : Int,
    val unit                    : String,
    val source                  : String,
    val purchase_price_snapshot : Double,
    val valeur_totale            : Double,
    val date_time                : String,
    val motif                    : String?,
    val photo_path                : String?,
    val created_at                : String
)