package com.distrigo.app.data.model

data class ClientTransaction(
    val type          : String,
    val id            : Int,
    val amount        : Double?,
    val total         : Double? = null,
    val montant_paye  : Double? = null,
    val status        : String? = null,
    val note          : String?,
    val created_at    : String
)