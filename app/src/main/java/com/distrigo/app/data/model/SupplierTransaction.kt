package com.distrigo.app.data.model
import com.google.gson.annotations.SerializedName

data class SupplierTransaction(
    val type         : String,
    val id           : Int,
    val amount       : Double?,
    val montant_paye : Double?,
    val status       : String?,
    val note         : String?,
    val created_at   : String,
    /** A bon's number; null for a payment or the opening balance. See [numberLabel]. */
    val numero       : String? = null,
    /** A bon's amount still to pay once later payments are spent (REP-02); null otherwise. */
    val reste        : Double? = null
)


