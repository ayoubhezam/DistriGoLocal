package com.distrigo.app.data.repository

import com.distrigo.app.data.model.Quantity
import androidx.room.withTransaction
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.InventoryItemEntity
import com.distrigo.app.data.local.entity.InventorySessionEntity
import com.distrigo.app.data.model.InventoryItem
import com.distrigo.app.data.model.InventorySession
import com.distrigo.app.data.model.InventorySessionSummary
import kotlin.math.abs
import com.distrigo.app.data.local.entity.mouvement.StockMovementEntity
import com.distrigo.app.data.local.paging.InventoryItemPagingSource
import com.distrigo.app.data.local.paging.InventorySessionListQuery
import com.distrigo.app.data.local.paging.InventorySessionPagingSource
import com.distrigo.app.core.paging.PagingDefaults
import androidx.paging.Pager
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
class InventoryRepository(
    private val db: AppDatabase
) {
    private val inventoryDao = db.inventoryDao()
    private val productDao   = db.productDao()
    private val depotGuard   = DepotStockGuard(db)

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

    /**
     * Closes the count. With [zeroUncounted], every product it did not count that still holds stock at
     * the dépôt is found empty there: a line is written for it — counted at what the camions still hold,
     * since a dépôt count does not see them — with the adjustment that takes the dépôt to zero. Without
     * it, the products not counted keep their stock and get no line. One transaction: all or nothing.
     *
     * [at] is the inventory's date — the day it is closed under in the history, chosen on the summary —
     * and the date of the adjustments that zero. The lines counted before keep the moment they were.
     */
    suspend fun finishSession(
        sessionId: Int,
        zeroUncounted: Boolean,
        userName: String? = null,
        at: String = java.time.Instant.now().toString(),
    ): Map<String, Any> =
        db.withTransaction {
            val session = inventoryDao.getSessionById(sessionId) ?: return@withTransaction mapOf("error" to "Session introuvable")
            var zeroed = 0
            if (zeroUncounted) {
                val now = at
                val products = inventoryDao.uncountedWithDepotStock(sessionId)
                val lines = products.map { p ->
                    val counted = Quantity.normalize(p.camion_stock)
                    val ecart = Quantity.normalize(counted - p.stock)
                    InventoryItemEntity(
                        session_id = sessionId, product_id = p.id, product_name = p.name,
                        product_image_uri = p.image_uri,
                        qte_systeme = p.stock, qte_physique = counted, ecart = ecart,
                        purchase_price_snapshot = p.purchase_price, valeur_ecart = ecart * p.purchase_price,
                        created_at = now
                    )
                }
                val ids = inventoryDao.insertItems(lines)
                db.stockMovementDao().insertAll(lines.zip(ids).map { (line, id) ->
                    StockMovementEntity(
                        product_id   = line.product_id,
                        product_name = line.product_name,
                        type         = "ajustement",
                        direction    = "sortie",
                        quantity     = abs(line.ecart),
                        emplacement  = "depot",
                        source_label = "Inventaire session #$sessionId",
                        source_type  = "inventory_item",
                        source_id    = id.toInt(),
                        unit_price   = line.purchase_price_snapshot,
                        total_value  = abs(line.valeur_ecart),
                        user_name    = userName,
                        note         = "Non inventorié, mis à zéro",
                        created_at   = now
                    )
                })
                zeroed = lines.size
            }
            inventoryDao.updateSession(
                session.copy(status = "completed", completed_at = at)
            )
            mapOf("message" to "Inventaire terminé avec succès", "zeroed" to zeroed)
        }

    // ── Items ──
    suspend fun isProductAlreadyScanned(sessionId: Int, productId: Int): Boolean {
        return inventoryDao.getItemForSessionAndProduct(sessionId, productId) != null
    }

    suspend fun recordScan(sessionId: Int, productId: Int, qtePhysique: Double, userName: String? = null): Map<String, Any> {
        if (qtePhysique < 0) return mapOf("error" to "Quantité invalide")
        val counted = Quantity.normalize(qtePhysique)   // to the thousandth, as every quantity is written

        // The "already scanned" check, the stock it measures against and the insert are one
        // transaction: two taps can no longer both pass the check. The unique index on
        // (session_id, product_id) is what guarantees it; this is what turns a second scan into the
        // message rather than a constraint error.
        //
        // The count is of the total stock and its adjustment is booked at the dépôt, so a count below
        // what the camion holds would leave the dépôt negative: strict stock refuses it.
        return depotGuard.guardOrError(listOf(productId)) {
            if (inventoryDao.getItemForSessionAndProduct(sessionId, productId) != null) {
                return@guardOrError mapOf("error" to "Ce produit a déjà été scanné dans cette session")
            }
            val product = productDao.getProductById(productId)
                ?: return@guardOrError mapOf("error" to "Produit introuvable")
            unitError(product.name, product.unit_type, counted)?.let { return@guardOrError mapOf("error" to it) }

            val qteSysteme  = product.stock
            val ecart       = Quantity.normalize(counted - qteSysteme)
            val valeurEcart = ecart * product.purchase_price
            val now = java.time.Instant.now().toString()

            val itemId = inventoryDao.insertItem(
                InventoryItemEntity(
                    session_id = sessionId, product_id = product.id, product_name = product.name,
                    product_image_uri = product.image_uri,
                    qte_systeme = qteSysteme, qte_physique = counted, ecart = ecart,
                    purchase_price_snapshot = product.purchase_price, valeur_ecart = valeurEcart,
                    created_at = now
                )
            ).toInt()

            // The adjustment is what brings the stock to the counted quantity.
            if (ecart != 0.0) {
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

            mapOf(
                "message" to "Produit enregistré avec succès",
                "qte_systeme" to qteSysteme,
                "ecart" to ecart,
                "valeur_ecart" to valeurEcart
            )
        }
    }
    suspend fun updateScan(itemId: Int, newQtePhysique: Double, userName: String? = null): Map<String, Any> {
        if (newQtePhysique < 0) return mapOf("error" to "Quantité invalide")
        val counted = Quantity.normalize(newQtePhysique)   // to the thousandth, as every quantity is written
        val item = inventoryDao.getItemById(itemId) ?: return mapOf("error" to "Élément introuvable")
        productDao.getProductById(item.product_id)?.let { p ->
            unitError(p.name, p.unit_type, counted)?.let { return mapOf("error" to it) }
        }

        val newEcart       = Quantity.normalize(counted - item.qte_systeme)
        val newValeurEcart = newEcart * item.purchase_price_snapshot

        return depotGuard.guardOrError(listOf(item.product_id)) {
            inventoryDao.updateItem(
                item.copy(qte_physique = counted, ecart = newEcart, valeur_ecart = newValeurEcart)
            )
            // The scan's adjustment is replaced by one measured against the same qte_systeme, so the
            // stock becomes the corrected count plus whatever moved since the scan — not the count
            // alone, which would erase a sale made in between.
            val product = productDao.getProductById(item.product_id)

            db.stockMovementDao().deleteBySource("inventory_item", itemId)
            if (newEcart != 0.0 && product != null) {
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
            // What the line was and is now, so the count's figures can be corrected without re-summing.
            mapOf(
                "message" to "Modifié avec succès",
                "old_ecart" to item.ecart, "old_valeur_ecart" to item.valeur_ecart,
                "ecart" to newEcart, "valeur_ecart" to newValeurEcart
            )
        }
    }

    suspend fun deleteScan(itemId: Int): Map<String, Any> {
        val item = inventoryDao.getItemById(itemId) ?: return mapOf("error" to "Élément introuvable")

        // Removing a scan that raised the stock takes that back out of the dépôt.
        return depotGuard.guardOrError(listOf(item.product_id)) {
            // ── Restaure le stock — removing the scan's adjustment undoes it, keeping anything that
            // moved since, where setting it back to qte_systeme would have erased that too ──
            db.stockMovementDao().deleteBySource("inventory_item", itemId)
            inventoryDao.deleteItem(itemId)
            // What was removed, so the count's figures can take it off without re-summing.
            mapOf("message" to "Supprimé, stock restauré", "ecart" to item.ecart, "valeur_ecart" to item.valeur_ecart)
        }
    }

    // ── Résumé ──
    /** Summed in SQL: this used to load every line of the count to count them. */
    suspend fun getSessionSummary(sessionId: Int): InventorySessionSummary =
        inventoryDao.getSessionSummary(sessionId)

    // -- History --
    //
    // Paged: the history used to read every session and sum every line of every inventory each time
    // it opened, and a session's detail read all its lines, up to the whole catalogue.

    /** A new history source for [query]. The caller keeps it to invalidate — see [InventorySessionPagingSource]. */
    fun sessionHistorySource(query: InventorySessionListQuery): InventorySessionPagingSource =
        InventorySessionPagingSource(db, query)

    /**
     * A session's lines, newest scanned first, a page at a time — [live] for the count in progress,
     * whose lines can still be corrected; see [InventoryItemPagingSource].
     */
    fun pageSessionItems(sessionId: Int, live: Boolean): Flow<PagingData<InventoryItem>> =
        Pager(PagingDefaults.config) { InventoryItemPagingSource(db, sessionId, live) { it.toInventoryItem() } }.flow
}