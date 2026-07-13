package com.distrigo.app.data.model

data class PurchaseOrder(
    val id                : Int,
    val date              : String,
    val total             : Double,
    val status            : String,
    val note              : String?,
    val supplier_id       : Int,
    val supplier_name     : String,
    val items_count       : Int? = null,
    val created_at        : String? = null,
    val items             : List<PurchaseOrderItem>? = null,
    val montant_paye      : Double? = null)