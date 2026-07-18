package com.distrigo.app.data.model

data class InventorySession(
    val id           : Int,
    val status       : String,   // "draft" | "completed"
    val started_at   : String,
    val completed_at : String?
)

data class InventoryItem(
    val id                      : Int,
    val session_id              : Int,
    val product_id              : Int,
    val product_name            : String,
    val product_image_uri       : String?,
    val qte_systeme              : Int,
    val qte_physique              : Int,
    val ecart                      : Int,     // + surplus, - manque
    val purchase_price_snapshot    : Double,
    val valeur_ecart                 : Double,   // signé
    val created_at                    : String
)

data class InventorySessionSummary(
    val total_products     : Int,
    val total_ecarts       : Int,
    val total_value_ecarts : Double   // somme des valeurs absolues
)

data class InventorySessionHistory(
    val session : InventorySession,
    val summary : InventorySessionSummary
)