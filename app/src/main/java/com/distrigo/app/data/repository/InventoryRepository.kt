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

    suspend fun recordScan(sessionId: Int, productId: Int, qtePhysique: Int): Map<String, Any> {
        if (qtePhysique < 0) return mapOf("error" to "Quantité invalide")

        if (inventoryDao.getItemForSessionAndProduct(sessionId, productId) != null) {
            return mapOf("error" to "Ce produit a déjà été scanné dans cette session")
        }

        val product = productDao.getProductById(productId) ?: return mapOf("error" to "Produit introuvable")

        val qteSysteme  = product.stock
        val ecart       = qtePhysique - qteSysteme
        val valeurEcart = ecart * product.purchase_price

        db.withTransaction {
            inventoryDao.insertItem(
                InventoryItemEntity(
                    session_id = sessionId, product_id = product.id, product_name = product.name,
                    product_image_uri = product.image_uri,
                    qte_systeme = qteSysteme, qte_physique = qtePhysique, ecart = ecart,
                    purchase_price_snapshot = product.purchase_price, valeur_ecart = valeurEcart,
                    created_at = java.time.Instant.now().toString()
                )
            )
            productDao.updateProduct(product.copy(stock = qtePhysique))
        }

        return mapOf(
            "message" to "Produit enregistré avec succès",
            "qte_systeme" to qteSysteme,
            "ecart" to ecart,
            "valeur_ecart" to valeurEcart
        )
    }
    suspend fun updateScan(itemId: Int, newQtePhysique: Int): Map<String, Any> {
        if (newQtePhysique < 0) return mapOf("error" to "Quantité invalide")
        val item = inventoryDao.getItemById(itemId) ?: return mapOf("error" to "Élément introuvable")

        val newEcart       = newQtePhysique - item.qte_systeme
        val newValeurEcart = newEcart * item.purchase_price_snapshot

        db.withTransaction {
            inventoryDao.updateItem(
                item.copy(qte_physique = newQtePhysique, ecart = newEcart, valeur_ecart = newValeurEcart)
            )
            // ── Set direct (pas de delta) — même logique que recordScan ──
            productDao.getProductById(item.product_id)?.let { product ->
                productDao.updateProduct(product.copy(stock = newQtePhysique))
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