package com.distrigo.app.data.repository

import androidx.room.withTransaction
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.ProductEntity
import com.distrigo.app.data.local.entity.RetourFournisseurEntity
import com.distrigo.app.data.local.entity.RetourFournisseurItemEntity
import com.distrigo.app.data.local.entity.mouvement.StockMovementEntity
import com.distrigo.app.data.model.RetourFournisseur
import com.distrigo.app.data.model.RetourFournisseurItem
import com.distrigo.app.data.model.RetourFournisseurMotifs
import com.distrigo.app.data.model.StockEffect

class RetourFournisseurRepository(
    private val db: AppDatabase
) {
    private val retourDao   = db.retourFournisseurDao()
    private val productDao  = db.productDao()
    private val supplierDao = db.supplierDao()

    // ── Mapping ──
    private fun RetourFournisseurItemEntity.toItem() = RetourFournisseurItem(
        id = this.id, product_id = this.product_id, product_name = this.product_name,
        unit_type = this.unit_type, quantity = this.quantity,
        unit_price = this.unit_price, total_price = this.total_price
    )

    private suspend fun RetourFournisseurEntity.toRetour(items: List<RetourFournisseurItem>? = null): RetourFournisseur {
        val supplierName = supplierDao.getSupplierById(this.supplier_id)?.name ?: "Fournisseur inconnu"
        return RetourFournisseur(
            id = this.id, supplier_id = this.supplier_id, supplier_name = supplierName,
            date = this.date, motif = this.motif, note = this.note, total = this.total,
            created_at = this.created_at, items_count = items?.size, items = items
        )
    }

    // ── Lecture ──
    suspend fun getRetours(supplierId: Int? = null): List<RetourFournisseur> {
        val entities = if (supplierId != null) retourDao.getRetoursForSupplier(supplierId)
                       else retourDao.getAllRetours()
        return entities.map { entity ->
            val count = retourDao.getItemsForRetour(entity.id).size
            entity.toRetour().copy(items_count = count)
        }
    }

    suspend fun getRetour(id: Int): RetourFournisseur {
        val entity = retourDao.getRetourById(id)
            ?: throw IllegalStateException("Retour introuvable: $id")
        val items = retourDao.getItemsForRetour(id).map { it.toItem() }
        return entity.toRetour(items)
    }

    // ── Écriture ──
    suspend fun createRetour(
        supplierId : Int,
        date       : String,
        motif      : String?,
        note       : String?,
        items      : List<Map<String, Any?>>,
        userName   : String? = null
    ): Map<String, Any> {
        if (items.isEmpty()) return mapOf("error" to "Ajoutez au moins un produit")

        db.withTransaction {
            val supplier = supplierDao.getSupplierById(supplierId)
                ?: throw IllegalStateException("Fournisseur introuvable: $supplierId")
            val now = java.time.Instant.now().toString()

            val lines: List<Pair<ProductEntity, Double>> = items.map { map ->
                val productId = (map["product_id"] as Number).toInt()
                val quantity  = (map["quantity"] as Number).toDouble()
                val product = productDao.getProductById(productId)
                    ?: throw IllegalStateException("Produit introuvable: $productId")
                product to quantity
            }
            val total = lines.sumOf { (product, quantity) -> quantity * product.purchase_price }

            val retourId = retourDao.insertRetour(
                RetourFournisseurEntity(
                    supplier_id = supplierId, date = date, motif = motif, note = note,
                    total = total, created_at = now
                )
            ).toInt()

            val definition = RetourFournisseurMotifs.resolve(motif)
            val movementEntities = mutableListOf<StockMovementEntity>()
            val itemEntities = lines.map { (product, quantity) ->
                val unitPrice  = product.purchase_price
                val totalPrice = quantity * unitPrice

                when (definition.stockEffect) {
                    StockEffect.DECREASE -> productDao.updateProduct(product.copy(stock = product.stock - quantity))
                    StockEffect.INCREASE -> productDao.updateProduct(product.copy(stock = product.stock + quantity))
                    StockEffect.NONE     -> { /* no-op */ }
                }

                movementEntities += StockMovementEntity(
                    product_id   = product.id,
                    product_name = product.name,
                    type         = "retour_fournisseur",
                    direction    = "sortie",
                    quantity     = quantity,
                    emplacement  = "depot",
                    source_label = supplier.name,
                    source_type  = "retour_fournisseur",
                    source_id    = retourId,
                    unit_price   = unitPrice,
                    total_value  = totalPrice,
                    user_name    = userName,
                    note         = motif,
                    created_at   = now
                )

                if (definition.perteTypeName != null) {
                    val perteType = db.perteDao().getAllPerteTypes().find { it.name == definition.perteTypeName }
                        ?: throw IllegalStateException("Type de perte introuvable : ${definition.perteTypeName}")
                    PerteRepository(db).addPerte(
                        typeId = perteType.id, productId = product.id, quantity = quantity, source = "depot",
                        dateTime = now, motif = "Retour fournisseur #$retourId — refusé", photoPath = null, userName = userName,
                        affectsStock = false,
                        sourceType = "retour_fournisseur", sourceId = retourId
                    )
                }

                RetourFournisseurItemEntity(
                    retour_id = retourId, product_id = product.id, product_name = product.name,
                    unit_type = product.unit_type, quantity = quantity,
                    unit_price = unitPrice, total_price = totalPrice
                )
            }
            retourDao.insertItems(itemEntities)
            db.stockMovementDao().insertAll(movementEntities)
            recalculateSupplierBalance(supplierId)
        }
        return mapOf("message" to "Retour enregistré avec succès")
    }

    suspend fun deleteRetour(id: Int): Map<String, Any> {
        val retour = retourDao.getRetourById(id) ?: return mapOf("error" to "Retour introuvable")
        db.withTransaction {
            val definition = RetourFournisseurMotifs.resolve(retour.motif)

            // Raw DAO delete, not PerteRepository.deletePerte: these linked pertes have affectsStock=false,
            // so their stock was never separately decremented — restoring would incorrectly add quantity back.
            db.perteDao().getPertesBySource("retour_fournisseur", id).forEach { db.perteDao().deletePerteById(it.id) }

            val items = retourDao.getItemsForRetour(id)
            for (item in items) {
                productDao.getProductById(item.product_id)?.let { product ->
                    val reversed = when (definition.stockEffect) {
                        StockEffect.DECREASE -> product.copy(stock = product.stock + item.quantity)
                        StockEffect.INCREASE -> product.copy(stock = product.stock - item.quantity)
                        StockEffect.NONE      -> product
                    }
                    productDao.updateProduct(reversed)
                }
            }
            retourDao.deleteItemsForRetour(id)
            retourDao.deleteRetourById(id)
            db.stockMovementDao().deleteBySource("retour_fournisseur", id)
            recalculateSupplierBalance(retour.supplier_id)
        }
        return mapOf("message" to "Retour supprimé, stock restauré")
    }

    // ── Solde fournisseur — formule dupliquée de ProductRepository.recalculateSupplierBalance ──
    private suspend fun recalculateSupplierBalance(supplierId: Int) {
        val supplier = supplierDao.getSupplierById(supplierId) ?: return
        val orders = db.purchaseDao().getAllOrders().filter { it.supplier_id == supplierId }
        val ordersTotal          = orders.sumOf { it.total }
        val ordersPaidAtCreation = orders.sumOf { it.montant_paye }
        val separatePayments     = db.supplierPaymentDao().getPaymentsForSupplier(supplierId).sumOf { it.amount }
        val retoursTotal         = retourDao.getRetoursForSupplier(supplierId).sumOf { it.total }

        val newBalance = supplier.initial_balance + ordersTotal - ordersPaidAtCreation - separatePayments - retoursTotal
        supplierDao.updateSupplier(supplier.copy(balance = newBalance))
    }
}