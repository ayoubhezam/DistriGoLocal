package com.distrigo.app.data.model

data class Client(
    val id            : Int,
    val name          : String,
    val phone         : String?,
    val wilaya_id     : Int?,
    val commune_id    : Int?,
    val wilaya_name   : String?,
    val commune_name  : String?,
    val address       : String?,
    val note          : String?,
    val balance       : Double,
    val customer_type : String = "retail",
    val image_uri     : String?,
    val latitude      : Double?,
    val longitude     : Double?
)