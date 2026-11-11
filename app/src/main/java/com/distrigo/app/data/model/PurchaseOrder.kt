package com.distrigo.app.data.model

data class PurchaseOrder(
    val id                 : Int,
    val date               : String,
    val total              : Double,
    val status             : String,
    val note               : String?,
    val supplier_id        : Int,
    val supplier_name      : String,
    val items_count        : Int? = null,
    val created_at         : String? = null,
    val items              : List<PurchaseOrderItem>? = null,
    val montant_paye       : Double? = null,
    val supplier_image_uri : String? = null,  // ← جديد
    /** See [numberLabel]. */
    val numero             : String? = null,
    /** Still to be paid once the supplier's later payments are spent, oldest bons first (REP-02); null if unknown. */
    val reste              : Double? = null
)