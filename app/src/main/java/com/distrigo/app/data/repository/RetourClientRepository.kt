package com.distrigo.app.data.repository

import androidx.room.withTransaction
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.ProductEntity
import com.distrigo.app.data.local.entity.RetourClientEntity
import com.distrigo.app.data.local.entity.RetourClientItemEntity
import com.distrigo.app.data.local.entity.mouvement.StockMovementEntity
import com.distrigo.app.data.model.RetourClient
import com.distrigo.app.data.model.RetourClientItem
import com.distrigo.app.data.model.RetourClientMotifs
import com.distrigo.app.data.model.StockEffect

class RetourClientRepository(
    private val db: AppDatabase
) {
    private val retourDao  = db.retourClientDao()
    private val productDao = db.productDao()
    private val clientDao  = db.clientDao()

    // ── Mapping ──
    private fun RetourClientItemEntity.toItem() = RetourClientItem(
        id = this.id, product_id = this.product_id, product_name = this.product_name,
        unit_type = this.unit_type, quantity = this.quantity,
        unit_price = this.unit_price, total_price = this.total_price
    )

    private suspend fun RetourClientEntity.toRetour(items: List<RetourClientItem>? = null): RetourClient {
        val clientName = clientDao.getClientById(this.client_id)?.name ?: "Client inconnu"
        return RetourClient(
            id = this.id, client_id = this.client_id, client_name = clientName,
            tournee_id = this.tournee_id, date = this.date, motif = this.motif, note = this.note,
            total = this.total, created_at = this.created_at, items_count = items?.size, items = items
        )
    }

    // ── Lecture ──
    suspend fun getRetours(clientId: Int? = null): List<RetourClient> {
        val entities = if (clientId != null) retourDao.getRetoursForClient(clientId)
                       else retourDao.getAllRetours()
        return entities.map { entity ->
            val count = retourDao.getItemsForRetour(entity.id).size
            entity.toRetour().copy(items_count = count)
        }
    }

    suspend fun getRetour(id: Int): RetourClient {
        val entity = retourDao.getRetourById(id)
            ?: throw IllegalStateException("Retour introuvable: $id")
        val items = retourDao.getItemsForRetour(id).map { it.toItem() }
        return entity.toRetour(items)
    }

    suspend fun getRetourDetail(id: Int): RetourClient? {
        val retour = retourDao.getRetourById(id) ?: return null
        val items = retourDao.getItemsForRetour(id).map { it.toItem() }
        return retour.toRetour(items)
    }

    // ── Écriture ──
    suspend fun createRetour(
        clientId  : Int,
        tourneeId : Int?,
        date      : String,
        motif     : String?,
        note      : String?,
        items     : List<Map<String, Any?>>,
        userName  : String? = null
    ): Map<String, Any> {
        if (items.isEmpty()) return mapOf("error" to "Ajoutez au moins un produit")

        PerteRepository(db).seedDefaultPerteTypesIfNeeded()

        db.withTransaction {
            val client = clientDao.getClientById(clientId)
                ?: throw IllegalStateException("Client introuvable: $clientId")
            val now = java.time.Instant.now().toString()

            val lines: List<Pair<ProductEntity, Double>> = items.map { map ->
                val productId = (map["product_id"] as Number).toInt()
                val quantity  = (map["quantity"] as Number).toDouble()
                val product = productDao.getProductById(productId)
                    ?: throw IllegalStateException("Produit introuvable: $productId")
                product to quantity
            }
            val total = lines.sumOf { (product, quantity) -> quantity * product.selling_price }

            val retourId = retourDao.insertRetour(
                RetourClientEntity(
                    client_id = clientId, tournee_id = tourneeId, date = date, motif = motif, note = note,
                    total = total, created_at = now
                )
            ).toInt()

            val definition = RetourClientMotifs.resolve(motif)
            val movementEntities = mutableListOf<StockMovementEntity>()
            val itemEntities = lines.map { (product, quantity) ->
                val unitPrice  = product.selling_price
                val totalPrice = quantity * unitPrice

                when (definition.stockEffect) {
                    StockEffect.INCREASE -> productDao.updateProduct(product.copy(stock = product.stock + quantity, camion_stock = product.camion_stock + quantity))
                    StockEffect.DECREASE -> productDao.updateProduct(product.copy(stock = product.stock - quantity, camion_stock = product.camion_stock - quantity))
                    StockEffect.NONE     -> productDao.updateProduct(product.copy(stock = product.stock + quantity, camion_stock = product.camion_stock + quantity))
                    // NONE still increases here — the physical item really did come back — the offsetting decrease happens immediately below via the linked Perte, giving a full paper trail instead of a silent no-op.
                }

                movementEntities += StockMovementEntity(
                    product_id   = product.id,
                    product_name = product.name,
                    type         = "retour_client",
                    direction    = "entree",
                    quantity     = quantity,
                    emplacement  = "camion",
                    source_label = client.name,
                    source_type  = "retour_client",
                    source_id    = retourId,
                    unit_price   = unitPrice,
                    total_value  = totalPrice,
                    user_name    = userName,
                    note         = motif,
                    created_at   = now
                )

                if (definition.perteTypeName != null) {
                    val perteType = db.perteDao().getAllPerteTypes().find { it.name == definition.perteTypeName }
                        ?: throw IllegalStateException("Type de perte introuvable : ${definition.perteTypeName}. Assurez-vous que seedDefaultPerteTypesIfNeeded() a été exécuté.")
                    PerteRepository(db).addPerte(
                        typeId = perteType.id, productId = product.id, quantity = quantity, source = "camion",
                        dateTime = now, motif = "Retour client #$retourId", photoPath = null, userName = userName,
                        sourceType = "retour_client", sourceId = retourId
                    )
                }

                RetourClientItemEntity(
                    retour_id = retourId, product_id = product.id, product_name = product.name,
                    unit_type = product.unit_type, quantity = quantity,
                    unit_price = unitPrice, total_price = totalPrice
                )
            }
            retourDao.insertItems(itemEntities)
            db.stockMovementDao().insertAll(movementEntities)
            recalculateClientBalance(clientId)
        }
        return mapOf("message" to "Retour enregistré avec succès")
    }

    suspend fun deleteRetour(id: Int): Map<String, Any> {
        val retour = retourDao.getRetourById(id) ?: return mapOf("error" to "Retour introuvable")
        db.withTransaction {
            val definition = RetourClientMotifs.resolve(retour.motif)

            if (definition.perteTypeName != null) {
                // Restores the quantity that addPerte had subtracted, bringing stock back to the "just increased" state.
                db.perteDao().getPertesBySource("retour_client", id).forEach { PerteRepository(db).deletePerte(it.id) }
            }

            val items = retourDao.getItemsForRetour(id)
            for (item in items) {
                productDao.getProductById(item.product_id)?.let { product ->
                    val reversed = when (definition.stockEffect) {
                        // Step 6 always increases first regardless of stockEffect, so NONE reverses the same way as INCREASE.
                        StockEffect.INCREASE, StockEffect.NONE -> product.copy(
                            stock        = product.stock - item.quantity,
                            camion_stock = product.camion_stock - item.quantity
                        )
                        StockEffect.DECREASE -> product.copy(
                            stock        = product.stock + item.quantity,
                            camion_stock = product.camion_stock + item.quantity
                        )
                    }
                    productDao.updateProduct(reversed)
                }
            }
            retourDao.deleteItemsForRetour(id)
            retourDao.deleteRetourById(id)
            db.stockMovementDao().deleteBySource("retour_client", id)
            recalculateClientBalance(retour.client_id)
        }
        return mapOf("message" to "Retour supprimé, stock restauré")
    }

    // ── Solde client — formule dupliquée de ProductRepository.recalculateClientBalance ──
    private suspend fun recalculateClientBalance(clientId: Int) {
        val client = clientDao.getClientById(clientId) ?: return
        val ventes = db.venteDao().getVentesForClient(clientId)
        val ventesTotal       = ventes.sumOf { it.total }
        val ventesPaid        = ventes.sumOf { it.montant_paye }
        val separatePayments  = db.clientPaymentDao().getPaymentsForClient(clientId).sumOf { it.amount }
        val retoursTotal      = retourDao.getRetoursForClient(clientId).sumOf { it.total }

        val newBalance = ventesTotal - ventesPaid - separatePayments - retoursTotal
        clientDao.updateClient(client.copy(balance = newBalance))
    }
}