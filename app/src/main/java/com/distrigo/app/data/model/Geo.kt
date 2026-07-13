package com.distrigo.app.data.model

data class Wilaya(
    val id   : Int,
    val name : String
)

data class Commune(
    val id         : Int,
    val wilaya_id  : Int,
    val name       : String
)