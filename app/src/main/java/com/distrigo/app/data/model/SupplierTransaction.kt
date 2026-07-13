package com.distrigo.app.data.model
import com.google.gson.annotations.SerializedName

data class SupplierTransaction(
    val type         : String,
    val id           : Int,
    val amount       : Double?,
    val montant_paye : Double?,
    val status       : String?,
    val note         : String?,
    val created_at   : String
)


