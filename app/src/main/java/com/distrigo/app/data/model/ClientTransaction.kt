package com.distrigo.app.data.model

data class ClientTransaction(
    val type          : String,
    val id            : Int,
    val amount        : Double?,
    val total         : Double? = null,
    val montant_paye  : Double? = null,
    val status        : String? = null,
    val note          : String?,
    val created_at    : String,
    /** A vente's number; null for a payment. See [numberLabel]. */
    val numero        : String? = null,
    /** A vente's amount still to pay once later payments are spent (REP-02); null for a payment. */
    val reste         : Double? = null
)