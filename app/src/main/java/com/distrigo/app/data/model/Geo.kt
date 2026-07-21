package com.distrigo.app.data.model

data class Wilaya(
    val wilayaCode : Int,
    val nameFr      : String,
    val nameAr      : String,
    val communes    : List<Commune>
)

data class Commune(
    val id     : Int,
    val nameFr : String,
    val nameAr : String
)