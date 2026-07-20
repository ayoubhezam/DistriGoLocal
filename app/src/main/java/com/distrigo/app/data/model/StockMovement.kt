package com.distrigo.app.data.model

data class StockMovement(
    val id: Int,
    val product_id: Int,
    val product_name: String,
    val type: String,          // "achat" | "vente" | "chargement" | "perte" | "ajustement"
    val direction: String,     // "entree" | "sortie"
    val quantity: Int,
    val emplacement: String,   // "depot" | "camion"
    val source_label: String,
    val source_type: String,
    val source_id: Int,
    val unit_price: Double?,
    val total_value: Double,
    val user_name: String?,
    val note: String?,
    val created_at: String
)

data class StockMovementStats(
    val entrees: Int,
    val sorties: Int,
    val total: Int
)