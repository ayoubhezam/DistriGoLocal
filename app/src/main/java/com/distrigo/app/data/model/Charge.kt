package com.distrigo.app.data.model

data class ChargeType(
    val id               : Int,
    val name             : String,
    val icon             : String,
    val color_hex        : String,
    val is_default       : Boolean,
    val subtypes_count   : Int = 0,
    val total_this_month : Double = 0.0
)

data class ChargeSubType(
    val id               : Int,
    val type_id          : Int,
    val name             : String,
    val icon             : String,
    val has_fournisseur  : Boolean,
    val is_default       : Boolean,
    val expenses_count   : Int = 0,
    val total_this_month : Double = 0.0
)

data class Charge(
    val id           : Int,
    val type_id      : Int,
    val type_name    : String,
    val subtype_id   : Int,
    val subtype_name : String,
    val montant      : Double,
    val date_time    : String,
    val fournisseur  : String?,
    val note         : String?,
    val created_at   : String
)