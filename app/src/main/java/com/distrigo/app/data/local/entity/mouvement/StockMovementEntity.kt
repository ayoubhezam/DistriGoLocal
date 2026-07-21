package com.distrigo.app.data.local.entity.mouvement

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_movements")
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val product_id: Int,
    val product_name: String,      // denormalized — نفس منطق ChargementItemEntity

    val type: String,              // "achat" | "vente" | "chargement" | "perte" | "ajustement"
    val direction: String,         // "entree" | "sortie"
    val quantity: Double,           // موجب دائماً
    val emplacement: String,       // "depot" | "camion"

    val source_label: String,      // fournisseur / "VENTE COMPTOIR" / client / إلخ
    val source_type: String,       // "purchase_order" | "vente" | "chargement" | "perte" | "ajustement"
    val source_id: Int,            // للربط مع السجل الأصلي + "Voir le document"

    val unit_price: Double?,
    val total_value: Double,

    val user_name: String?,
    val note: String?,
    val created_at: String
)