package com.distrigo.app.data.model

data class ChargementSession(
    val id           : Int,
    val session_date : String,
    val note         : String?,
    val created_at   : String?,
    val chargements  : List<Chargement>? = null
)

data class Chargement(
    val id         : Int,
    val note       : String?,
    val created_at : String?,
    val session_id : Int? = null,
    val items      : List<ChargementItem>? = null
)

data class ChargementItem(
    val id           : Int,
    val product_id   : Int,
    val quantity     : Double,
    val direction    : String,
    val product_name : String,
    val unit_type    : String
)