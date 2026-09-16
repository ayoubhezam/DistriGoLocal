package com.distrigo.app.data.repository

import androidx.room.withTransaction
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.DefaultPerteType
import com.distrigo.app.data.local.entity.PerteEntity
import com.distrigo.app.data.local.entity.PerteTypeEntity
import com.distrigo.app.data.model.Perte
import com.distrigo.app.data.model.PerteType
import com.distrigo.app.data.local.entity.mouvement.StockMovementEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
class PerteRepository(
    private val db: AppDatabase
) {
    private val perteDao  = db.perteDao()
    private val productDao = db.productDao()

    // ── Mapping ──
    private fun PerteTypeEntity.toPerteType(count: Int = 0, totalValue: Double = 0.0, totalQty: Double = 0.0) = PerteType(
        id = this.id, name = this.name, icon = this.icon, color_hex = this.color_hex,
        description = this.description,   // ← السطر الوحيد المضاف هنا
        is_default = this.is_default, count = count, total_value = totalValue, total_qty = totalQty
    )

    private fun PerteEntity.toPerte() = Perte(
        id = this.id, type_id = this.type_id, type_name = this.type_name,
        product_id = this.product_id, product_name = this.product_name, product_image_uri = this.product_image_uri,
        quantity = this.quantity, unit = this.unit, source = this.source,
        purchase_price_snapshot = this.purchase_price_snapshot, valeur_totale = this.valeur_totale,
        date_time = this.date_time, motif = this.motif, photo_path = this.photo_path, created_at = this.created_at,
        source_type = this.source_type, source_id = this.source_id
    )


    private fun currentMonth(): String = java.time.LocalDate.now().toString().take(7)

    // ── Seed Data ──
    // Named and identified by DefaultPerteType, so every phone gives "Casse" the same uuid.
    private data class SeedType(val type: DefaultPerteType, val icon: String, val colorHex: String, val description: String)
    private val DEFAULT_PERTE_TYPES = listOf(
        SeedType(DefaultPerteType.CASSE, "broken_image", "#F04438", "Produits cassés ou endommagés"),
        SeedType(DefaultPerteType.PEREMPTION, "event_busy", "#F79009", "Produits périmés"),
        SeedType(DefaultPerteType.VOL, "report", "#5B6EF5", "Produits volés"),
        SeedType(DefaultPerteType.PERTE_TRANSPORT, "local_shipping", "#12B76A", "Perdus pendant le transport"),
        SeedType(DefaultPerteType.DON, "card_giftcard", "#E91E63", "Dons et échantillons"),
        SeedType(DefaultPerteType.AUTRE, "category", "#98A2B3", "Autres pertes")
    )

    suspend fun seedDefaultPerteTypesIfNeeded() {
        if (perteDao.getAllPerteTypes().isNotEmpty()) return
        val now = java.time.Instant.now().toString()
        DEFAULT_PERTE_TYPES.forEach { seed ->
            perteDao.insertPerteType(
                PerteTypeEntity(
                    name = seed.type.seedName, icon = seed.icon, color_hex = seed.colorHex,
                    description = seed.description,   // ← السطر الوحيد المضاف هنا
                    is_default = true, created_at = now,
                    uuid = seed.type.uuid
                )
            )
        }
    }

    /**
     * A built-in perte type, by the uuid every phone gives it. Falls back to its name for a database
     * whose built-in types could not be given their fixed uuids — MIGRATION_49_50 matches them by the
     * names they were seeded with — which is how returns found their type before.
     */
    suspend fun findDefaultPerteType(type: DefaultPerteType): PerteTypeEntity? =
        perteDao.getPerteTypeByUuid(type.uuid) ?: perteDao.getAllPerteTypes().find { it.name == type.seedName }

    // ── Perte Types ──
    // The month's count and totals per type come from one GROUP BY query. This used to load every
    // perte ever recorded and filter them per type in Kotlin. A type with no perte that month gets
    // zeros, as before. On Dispatchers.Default, like every Kotlin step after a query here.
    suspend fun getPerteTypesWithStats(month: String? = null): List<PerteType> = withContext(Dispatchers.Default) {
        val types = perteDao.getAllPerteTypes()
        val targetMonth = month ?: currentMonth()
        val (start, end) = monthRange(targetMonth)
        val stats = perteDao.getMonthStatsByType(start, end).associateBy { it.type_id }
        types.map { type ->
            val s = stats[type.id]
            type.toPerteType(
                count      = s?.count ?: 0,
                totalValue = s?.total_value ?: 0.0,
                totalQty   = s?.total_qty ?: 0.0
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
        perteDao.softDeletePerteTypeById(id)
    }

    // ── Pertes ──
    suspend fun getPertes(typeId: Int, month: String? = null): List<Perte> {
        val pertes = if (month == null) {
            perteDao.getPertesForType(typeId)
        } else {
            val (start, end) = monthRange(month)
            perteDao.getPertesForTypeInRange(typeId, start, end)
        }
        return pertes.map { it.toPerte() }
    }

    suspend fun addPerte(
        typeId       : Int,
        productId    : Int,
        quantity     : Double,
        source       : String,   // "depot" | "camion"
        dateTime     : String,
        motif        : String?,
        photoPath    : String?,
        userName     : String? = null,
        affectsStock : Boolean = true,   // when false, the caller already recorded the physical stock movement itself; only the PerteEntity row is written
        sourceType   : String? = null,
        sourceId     : Int?    = null
    ): Map<String, Any> {
        val type = perteDao.getPerteTypeById(typeId) ?: return mapOf("error" to "Type introuvable")
        val product = productDao.getProductById(productId) ?: return mapOf("error" to "Produit introuvable")

        if (quantity <= 0) return mapOf("error" to "Quantité invalide")

        if (source == "camion" && quantity > product.camion_stock) {
            return mapOf("error" to "Stock camion insuffisant : disponible ${product.camion_stock}, demandé $quantity")
        }

        val valeurTotale = product.purchase_price * quantity
        val now = java.time.Instant.now().toString()

        db.withTransaction {
            val perteId = perteDao.insertPerte(
                PerteEntity(
                    type_id = type.id, type_name = type.name,
                    product_id = product.id, product_name = product.name, product_image_uri = product.image_uri,
                    quantity = quantity, unit = product.unit_type, source = source,
                    purchase_price_snapshot = product.purchase_price, valeur_totale = valeurTotale,
                    date_time = dateTime, motif = motif, photo_path = photoPath,
                    created_at = now, source_type = sourceType, source_id = sourceId
                )
            ).toInt()

            if (affectsStock) {
                // The movement is the stock change: the ledger triggers take it out of stock, and out
                // of the camion's share when that is where it was lost.
                db.stockMovementDao().insert(
                    StockMovementEntity(
                        product_id   = product.id,
                        product_name = product.name,
                        type         = "perte",
                        direction    = "sortie",
                        quantity     = quantity,
                        emplacement  = source,
                        source_label = type.name,
                        source_type  = "perte",
                        source_id    = perteId,
                        unit_price   = product.purchase_price,
                        total_value  = valeurTotale,
                        user_name    = userName,
                        note         = motif,
                        created_at   = now
                    )
                )
            }
        }
        return mapOf("message" to "Perte enregistrée avec succès")
    }

    suspend fun deletePerte(id: Int): Map<String, Any> {
        val perte = perteDao.getPerteById(id) ?: return mapOf("error" to "Perte introuvable")
        if (perte.source_type != null) {
            throw IllegalStateException("Cette perte est liée à un retour fournisseur — supprimez le retour concerné")
        }
        db.withTransaction {
            // Removing the perte's movement puts its quantity back in stock.
            db.stockMovementDao().deleteBySource("perte", id)
            perteDao.deletePerteById(id)
        }
        return mapOf("message" to "Perte supprimée, stock restauré")
    }
    suspend fun updatePerte(
        id        : Int,
        productId : Int,
        quantity  : Double,
        source    : String,
        dateTime  : String,
        motif     : String?,
        photoPath : String?,
        userName  : String? = null
    ): Map<String, Any> {
        val existing = perteDao.getPerteById(id) ?: return mapOf("error" to "Perte introuvable")
        if (quantity <= 0) return mapOf("error" to "Quantité invalide")

        return try {
            db.withTransaction {
                // 1) إعادة الكمية القديمة إلى مصدرها ومنتجها الأصليين — removing the old movement
                //    puts the old quantity back, so the camion check below sees the stock without it.
                db.stockMovementDao().deleteBySource("perte", id)

                // 2) إعادة جلب المنتج الجديد (بعد الاستعادة، مهم لو كان نفس المنتج)
                val product = productDao.getProductById(productId)
                    ?: throw IllegalStateException("Produit introuvable")

                if (source == "camion" && quantity > product.camion_stock) {
                    throw IllegalStateException("Stock camion insuffisant : disponible ${product.camion_stock}, demandé $quantity")
                }

                val valeurTotale = product.purchase_price * quantity
                val type = perteDao.getPerteTypeById(existing.type_id)

                perteDao.updatePerte(
                    existing.copy(
                        product_id = product.id, product_name = product.name, product_image_uri = product.image_uri,
                        quantity = quantity, unit = product.unit_type, source = source,
                        purchase_price_snapshot = product.purchase_price, valeur_totale = valeurTotale,
                        date_time = dateTime, motif = motif, photo_path = photoPath
                    )
                )

                // 3) خصم الكمية الجديدة من المصدر الجديد — by recording the new movement.
                db.stockMovementDao().insert(
                    StockMovementEntity(
                        product_id   = product.id,
                        product_name = product.name,
                        type         = "perte",
                        direction    = "sortie",
                        quantity     = quantity,
                        emplacement  = source,
                        source_label = type?.name ?: existing.type_name,
                        source_type  = "perte",
                        source_id    = id,
                        unit_price   = product.purchase_price,
                        total_value  = valeurTotale,
                        user_name    = userName,
                        note         = motif,
                        created_at   = java.time.Instant.now().toString()
                    )
                )
            }
            mapOf("message" to "Perte mise à jour avec succès")
        } catch (e: IllegalStateException) {
            mapOf("error" to (e.message ?: "Erreur inconnue"))
        }
    }

}