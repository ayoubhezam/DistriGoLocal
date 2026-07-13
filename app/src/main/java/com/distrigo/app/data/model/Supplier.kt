package com.distrigo.app.data.model

data class Supplier(
    val id      : Int,
    val name    : String,
    val phone   : String?,
    val address : String?,
    val note    : String?,
    val balance : Double,
    val initial_balance : Double = 0.0,
    val latitude  : Double?,
    val longitude : Double?,
    val wilaya_name  : String? = null,
    val commune_name : String? = null
)