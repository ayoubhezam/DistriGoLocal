package com.distrigo.app.data.repository

import androidx.room.withTransaction
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.PerteEntity
import com.distrigo.app.data.local.entity.PerteTypeEntity
import com.distrigo.app.data.model.Perte
import com.distrigo.app.data.model.PerteType

class PerteRepository(
    private val db: AppDatabase
) {
    private val perteDao  = db.perteDao()
    private val productDao = db.productDao()

    // ── Mapping ──
    private fun PerteTypeEntity.toPerteType(count: Int = 0, totalValue: Double = 0.0, totalQty: Int = 0) = PerteType(
        id = this.id, name = this.name, icon = this.icon, color_hex = this.color_hex,
        description = this.description,   // ← السطر الوحيد المضاف هنا
        is_default = this.is_default, count = count, total_value = totalValue, total_qty = totalQty
    )

    private fun PerteEntity.toPerte() = Perte(
        id = this.id, type_id = this.type_id, type_name = this.type_name,
        product_id = this.product_id, product_name = this.product_name, product_image_uri = this.product_image_uri,
        quantity = this.quantity, unit = this.unit, source = this.source,
        purchase_price_snapshot = this.purchase_price_snapshot, valeur_totale = this.valeur_totale,
        date_time = this.date_time, motif = this.motif, photo_path = this.photo_path, created_at = this.created_at
    )


    private fun currentMonth(): String = java.time.LocalDate.now().toString().take(7)

    // ── Seed Data ──
    private data class SeedType(val name: String, val icon: String, val colorHex: String, val description: String)
    private val DEFAULT_PERTE_TYPES = listOf(
        SeedType("Casse", "broken_image", "#F04438", "Produits cassés ou endommagés"),
        SeedType("Péremption", "event_busy", "#F79009", "Produits périmés"),
        SeedType("Vol", "report", "#5B6EF5", "Produits volés"),
        SeedType("Perte de transport", "local_shipping", "#12B76A", "Perdus pendant le transport"),
        SeedType("Don", "card_giftcard", "#E91E63", "Dons et échantillons"),
        SeedType("Autre", "category", "#98A2B3", "Autres pertes")
    )

    suspend fun seedDefaultPerteTypesIfNeeded() {
        if (perteDao.getAllPerteTypes().isNotEmpty()) return
        val now = java.time.Instant.now().toString()
        DEFAULT_PERTE_TYPES.forEach { seed ->
            perteDao.insertPerteType(
                PerteTypeEntity(
                    name = seed.name, icon = seed.icon, color_hex = seed.colorHex,
                    description = seed.description,   // ← السطر الوحيد المضاف هنا
                    is_default = true, created_at = now
                )
            )
        }
    }

    // ── Perte Types ──
    suspend fun getPerteTypesWithStats(month: String? = null): List<PerteType> {
        val types = perteDao.getAllPerteTypes()
        val allPertes = perteDao.getAllPertes()
        val targetMonth = month ?: currentMonth()
        return types.map { type ->
            val monthPertes = allPertes.filter { it.type_id == type.id && it.date_time.take(7) == targetMonth }
            type.toPerteType(
                count      = monthPertes.size,
                totalValue = monthPertes.sumOf { it.valeur_totale },
                totalQty   = monthPertes.sumOf { it.quantity }
            )
        }
    }

    suspend fun addPerteType(name: String, icon: String, colorHex: String): Long {
        return perteDao.insertPerteType(
            PerteTypeEntity(name = name, icon = icon, color_hex = colorHex, is_default = false, created_at = java.time.Instant.now().toString())
        )
    }

    suspend fun deletePerteType(id: Int) {
        val type = perteDao.getPerteTypeById(id) ?: return
        if (type.is_default) throw IllegalStateException("Impossible de supprimer un type par défaut")
        val pertes = perteDao.getPertesForType(id)
        if (pertes.isNotEmpty()) throw IllegalStateException("Impossible de supprimer : des pertes existent déjà sous ce type")
        perteDao.deletePerteTypeById(id)
    }

    // ── Pertes ──
    suspend fun getPertes(typeId: Int, month: String? = null): List<Perte> {
        val pertes = perteDao.getPertesForType(typeId)
        return (if (month != null) pertes.filter { it.date_time.take(7) == month } else pertes).map { it.toPerte() }
    }

    suspend fun addPerte(
        typeId    : Int,
        productId : Int,
        quantity  : Int,
        source    : String,   // "depot" | "camion"
        dateTime  : String,
        motif     : String?,
        photoPath : String?
    ): Map<String, Any> {
        val type = perteDao.getPerteTypeById(typeId) ?: return mapOf("error" to "Type introuvable")
        val product = productDao.getProductById(productId) ?: return mapOf("error" to "Produit introuvable")

        if (quantity <= 0) return mapOf("error" to "Quantité invalide")

        if (source == "camion" && quantity > product.camion_stock) {
            return mapOf("error" to "Stock camion insuffisant : disponible ${product.camion_stock}, demandé $quantity")
        }

        val valeurTotale = product.purchase_price * quantity

        db.withTransaction {
            perteDao.insertPerte(
                PerteEntity(
                    type_id = type.id, type_name = type.name,
                    product_id = product.id, product_name = product.name, product_image_uri = product.image_uri,
                    quantity = quantity, unit = product.unit_type, source = source,
                    purchase_price_snapshot = product.purchase_price, valeur_totale = valeurTotale,
                    date_time = dateTime, motif = motif, photo_path = photoPath,
                    created_at = java.time.Instant.now().toString()
                )
            )
            val updatedProduct = if (source == "camion") {
                product.copy(camion_stock = product.camion_stock - quantity)
            } else {
                product.copy(stock = product.stock - quantity)
            }
            productDao.updateProduct(updatedProduct)
        }
        return mapOf("message" to "Perte enregistrée avec succès")
    }

    suspend fun deletePerte(id: Int): Map<String, Any> {
        val perte = perteDao.getPerteById(id) ?: return mapOf("error" to "Perte introuvable")
        db.withTransaction {
            productDao.getProductById(perte.product_id)?.let { product ->
                val restored = if (perte.source == "camion") {
                    product.copy(camion_stock = product.camion_stock + perte.quantity)
                } else {
                    product.copy(stock = product.stock + perte.quantity)
                }
                productDao.updateProduct(restored)
            }
            perteDao.deletePerteById(id)
        }
        return mapOf("message" to "Perte supprimée, stock restauré")
    }

    suspend fun updatePerte(
        id        : Int,
        productId : Int,
        quantity  : Int,
        source    : String,
        dateTime  : String,
        motif     : String?,
        photoPath : String?
    ): Map<String, Any> {
        val existing = perteDao.getPerteById(id) ?: return mapOf("error" to "Perte introuvable")
        if (quantity <= 0) return mapOf("error" to "Quantité invalide")

        return try {
            db.withTransaction {
                // 1) إعادة الكمية القديمة إلى مصدرها ومنتجها الأصليين
                productDao.getProductById(existing.product_id)?.let { oldProduct ->
                    val reverted = if (existing.source == "camion") {
                        oldProduct.copy(camion_stock = oldProduct.camion_stock + existing.quantity)
                    } else {
                        oldProduct.copy(stock = oldProduct.stock + existing.quantity)
                    }
                    productDao.updateProduct(reverted)
                }

                // 2) إعادة جلب المنتج الجديد (بعد الاستعادة، مهم لو كان نفس المنتج)
                val product = productDao.getProductById(productId)
                    ?: throw IllegalStateException("Produit introuvable")

                if (source == "camion" && quantity > product.camion_stock) {
                    throw IllegalStateException("Stock camion insuffisant : disponible ${product.camion_stock}, demandé $quantity")
                }

                val valeurTotale = product.purchase_price * quantity

                perteDao.updatePerte(
                    existing.copy(
                        product_id = product.id, product_name = product.name, product_image_uri = product.image_uri,
                        quantity = quantity, unit = product.unit_type, source = source,
                        purchase_price_snapshot = product.purchase_price, valeur_totale = valeurTotale,
                        date_time = dateTime, motif = motif, photo_path = photoPath
                    )
                )

                // 3) خصم الكمية الجديدة من المصدر الجديد
                val updatedProduct = if (source == "camion") {
                    product.copy(camion_stock = product.camion_stock - quantity)
                } else {
                    product.copy(stock = product.stock - quantity)
                }
                productDao.updateProduct(updatedProduct)
            }
            mapOf("message" to "Perte mise à jour avec succès")
        } catch (e: IllegalStateException) {
            mapOf("error" to (e.message ?: "Erreur inconnue"))
        }
    }

    // ── لاستخدام Rapports/État de Stock لاحقاً ──
    suspend fun getMonthlyTotal(month: String): Double {
        return perteDao.getAllPertes().filter { it.date_time.take(7) == month }.sumOf { it.valeur_totale }
    }
}