package com.distrigo.app.data.local.entity.mouvement

import androidx.room.ColumnInfo
import com.distrigo.app.data.local.entity.newRowUuid
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stock_movements",
    indices = [
        Index(value = ["product_id", "created_at"]),
        Index(value = ["source_type", "source_id"]),
        Index(value = ["created_at"]),
        Index(value = ["uuid"], unique = true),
    ]
)
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
    val created_at: String,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis()
)