package com.distrigo.app.data.repository

import androidx.room.withTransaction
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.InventoryItemEntity
import com.distrigo.app.data.local.entity.InventorySessionEntity
import com.distrigo.app.data.model.InventoryItem
import com.distrigo.app.data.model.InventorySession
import com.distrigo.app.data.model.InventorySessionSummary
import kotlin.math.abs
import com.distrigo.app.data.model.InventorySessionHistory
import com.distrigo.app.data.local.entity.mouvement.StockMovementEntity
class InventoryRepository(
    private val db: AppDatabase
) {
    private val inventoryDao = db.inventoryDao()
    private val productDao   = db.productDao()

    // ── Mapping ──
    private fun InventorySessionEntity.toInventorySession() = InventorySession(
        id = this.id, status = this.status, started_at = this.started_at, completed_at = this.completed_at
    )

    private fun InventoryItemEntity.toInventoryItem() = InventoryItem(
        id = this.id, session_id = this.session_id, product_id = this.product_id,
        product_name = this.product_name, product_image_uri = this.product_image_uri,
        qte_systeme = this.qte_systeme, qte_physique = this.qte_physique, ecart = this.ecart,
        purchase_price_snapshot = this.purchase_price_snapshot, valeur_ecart = this.valeur_ecart,
        created_at = this.created_at
    )

    // ── Session ──
    suspend fun getOrCreateActiveSession(): InventorySession {
        inventoryDao.getActiveSession()?.let { return it.toInventorySession() }
        val now = java.time.Instant.now().toString()
        val id = inventoryDao.insertSession(
            InventorySessionEntity(status = "draft", started_at = now, completed_at = null)
        )
        return InventorySession(id = id.toInt(), status = "draft", started_at = now, completed_at = null)
    }

    suspend fun finishSession(sessionId: Int): Map<String, Any> {
        val session = inventoryDao.getSessionById(sessionId) ?: return mapOf("error" to "Session introuvable")
        inventoryDao.updateSession(
            session.copy(status = "completed", completed_at = java.time.Instant.now().toString())
        )
        return mapOf("message" to "Inventaire terminé avec succès")
    }

    // ── Items ──
    suspend fun getSessionItems(sessionId: Int): List<InventoryItem> {
        return inventoryDao.getItemsForSession(sessionId).map { it.toInventoryItem() }
    }

    suspend fun isProductAlreadyScanned(sessionId: Int, productId: Int): Boolean {
        return inventoryDao.getItemForSessionAndProduct(sessionId, productId) != null
    }

    suspend fun recordScan(sessionId: Int, productId: Int, qtePhysique: Int, userName: String? = null): Map<String, Any> {
        if (qtePhysique < 0) return mapOf("error" to "Quantité invalide")

        if (inventoryDao.getItemForSessionAndProduct(sessionId, productId) != null) {
            return mapOf("error" to "Ce produit a déjà été scanné dans cette session")
        }

        val product = productDao.getProductById(productId) ?: return mapOf("error" to "Produit introuvable")

        val qteSysteme  = product.stock
        val ecart       = qtePhysique - qteSysteme
        val valeurEcart = ecart * product.purchase_price
        val now = java.time.Instant.now().toString()

        db.withTransaction {
            val itemId = inventoryDao.insertItem(
                InventoryItemEntity(
                    session_id = sessionId, product_id = product.id, product_name = product.name,
                    product_image_uri = product.image_uri,
                    qte_systeme = qteSysteme, qte_physique = qtePhysique, ecart = ecart,
                    purchase_price_snapshot = product.purchase_price, valeur_ecart = valeurEcart,
                    created_at = now
                )
            ).toInt()
            productDao.updateProduct(product.copy(stock = qtePhysique))

            if (ecart != 0) {
                db.stockMovementDao().insert(
                    StockMovementEntity(
                        product_id   = product.id,
                        product_name = product.name,
                        type         = "ajustement",
                        direction    = if (ecart > 0) "entree" else "sortie",
                        quantity     = abs(ecart),
                        emplacement  = "depot",
                        source_label = "Inventaire session #$sessionId",
                        source_type  = "inventory_item",
                        source_id    = itemId,
                        unit_price   = product.purchase_price,
                        total_value  = abs(valeurEcart),
                        user_name    = userName,
                        note         = null,
                        created_at   = now
                    )
                )
            }
        }

        return mapOf(
            "message" to "Produit enregistré avec succès",
            "qte_systeme" to qteSysteme,
            "ecart" to ecart,
            "valeur_ecart" to valeurEcart
        )
    }
    suspend fun updateScan(itemId: Int, newQtePhysique: Int, userName: String? = null): Map<String, Any> {
        if (newQtePhysique < 0) return mapOf("error" to "Quantité invalide")
        val item = inventoryDao.getItemById(itemId) ?: return mapOf("error" to "Élément introuvable")

        val newEcart       = newQtePhysique - item.qte_systeme
        val newValeurEcart = newEcart * item.purchase_price_snapshot

        db.withTransaction {
            inventoryDao.updateItem(
                item.copy(qte_physique = newQtePhysique, ecart = newEcart, valeur_ecart = newValeurEcart)
            )
            // ── Set direct (pas de delta) — même logique que recordScan ──
            val product = productDao.getProductById(item.product_id)
            product?.let {
                productDao.updateProduct(it.copy(stock = newQtePhysique))
            }

            db.stockMovementDao().deleteBySource("inventory_item", itemId)
            if (newEcart != 0 && product != null) {
                db.stockMovementDao().insert(
                    StockMovementEntity(
                        product_id   = item.product_id,
                        product_name = item.product_name,
                        type         = "ajustement",
                        direction    = if (newEcart > 0) "entree" else "sortie",
                        quantity     = abs(newEcart),
                        emplacement  = "depot",
                        source_label = "Inventaire session #${item.session_id}",
                        source_type  = "inventory_item",
                        source_id    = itemId,
                        unit_price   = item.purchase_price_snapshot,
                        total_value  = abs(newValeurEcart),
                        user_name    = userName,
                        note         = null,
                        created_at   = java.time.Instant.now().toString()
                    )
                )
            }
        }
        return mapOf("message" to "Modifié avec succès")
    }

    suspend fun deleteScan(itemId: Int): Map<String, Any> {
        val item = inventoryDao.getItemById(itemId) ?: return mapOf("error" to "Élément introuvable")

        db.withTransaction {
            // ── Restaure le stock à sa valeur d'avant ce scan (qte_systeme) ──
            productDao.getProductById(item.product_id)?.let { product ->
                productDao.updateProduct(product.copy(stock = item.qte_systeme))
            }
            inventoryDao.deleteItem(itemId)
            db.stockMovementDao().deleteBySource("inventory_item", itemId)   // ← جديد
        }
        return mapOf("message" to "Supprimé, stock restauré")
    }

    // ── Résumé ──
    suspend fun getSessionSummary(sessionId: Int): InventorySessionSummary {
        val items = inventoryDao.getItemsForSession(sessionId)
        return InventorySessionSummary(
            total_products     = items.size,
            total_ecarts       = items.count { it.ecart != 0 },
            total_value_ecarts = items.sumOf { abs(it.valeur_ecart) }
        )
    }
    suspend fun getCompletedSessionsHistory(): List<InventorySessionHistory> {
        val sessions = inventoryDao.getAllSessions().filter { it.status == "completed" }
        return sessions.map { session ->
            val items = inventoryDao.getItemsForSession(session.id)
            InventorySessionHistory(
                session = session.toInventorySession(),
                summary = InventorySessionSummary(
                    total_products     = items.size,
                    total_ecarts       = items.count { it.ecart != 0 },
                    total_value_ecarts = items.sumOf { abs(it.valeur_ecart) }
                )
            )
        }
    }

    suspend fun getAllSessionsHistory(): List<InventorySessionHistory> {
        val sessions = inventoryDao.getAllSessions()   // déjà ORDER BY started_at DESC
        return sessions.map { session ->
            val items = inventoryDao.getItemsForSession(session.id)
            InventorySessionHistory(
                session = session.toInventorySession(),
                summary = InventorySessionSummary(
                    total_products     = items.size,
                    total_ecarts       = items.count { it.ecart != 0 },
                    total_value_ecarts = items.sumOf { abs(it.valeur_ecart) }
                )
            )
        }
    }
}