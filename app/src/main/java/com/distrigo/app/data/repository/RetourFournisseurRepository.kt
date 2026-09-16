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
import com.distrigo.app.data.model.RetourPreview
import com.distrigo.app.data.model.StockEffect
import com.distrigo.app.data.model.numberLabel

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

    private suspend fun RetourFournisseurEntity.toRetour(items: List<RetourFournisseurItem>? = null): RetourFournisseur =
        toRetourWith(
            supplierName = supplierDao.getSupplierById(this.supplier_id)?.name ?: "Fournisseur inconnu",
            itemsCount   = items?.size,
            items        = items
        )

    /**
     * The one place a retour row becomes a RetourFournisseur. [toRetour] looks the supplier up
     * itself; a caller listing one supplier's returns already knows the name and has the line
     * counts, and passes them in, so the two paths cannot drift apart field by field.
     */
    private fun RetourFournisseurEntity.toRetourWith(
        supplierName: String,
        itemsCount: Int?,
        items: List<RetourFournisseurItem>?
    ) = RetourFournisseur(
        id = this.id, supplier_id = this.supplier_id, supplier_name = supplierName,
        date = this.date, motif = this.motif, note = this.note, total = this.total,
        created_at = this.created_at, items_count = itemsCount, items = items,
        numero = this.numero
    )

    // ── Lecture ──
    /**
     * One supplier's returns for its list screen: the rows filtered in SQL, every line count in one
     * query, and the supplier's name looked up once.
     *
     * The rows were already filtered in SQL, but each one cost an items query and a supplier lookup
     * — 1 + 2R queries. The supplier id was also optional, and passing none loaded every return;
     * nothing did, so it now has to be named.
     */
    suspend fun getRetoursForSupplier(supplierId: Int): List<RetourFournisseur> {
        val entities = retourDao.getRetoursForSupplier(supplierId)
        if (entities.isEmpty()) return emptyList()
        val counts = retourDao.getItemCountsForRetours(entities.map { it.id })
            .associate { it.retour_id to it.count }
        val supplierName = supplierDao.getSupplierById(supplierId)?.name ?: "Fournisseur inconnu"
        return entities.map { it.toRetourWith(supplierName, counts[it.id] ?: 0, items = null) }
    }

    /**
     * A supplier's returns as its detail screen shows them: how many, their total, and the [limit]
     * latest with their line counts, instead of all its returns with one items query each.
     */
    suspend fun getDetailPreview(supplierId: Int, limit: Int): RetourPreview<RetourFournisseur> {
        val totals = retourDao.getRetourTotalsForSupplier(supplierId)
        val latest = retourDao.getLatestRetoursForSupplier(supplierId, limit)
        val lineCounts = if (latest.isEmpty()) emptyMap()
                         else retourDao.getItemCountsForRetours(latest.map { it.id }).associate { it.retour_id to it.count }
        return RetourPreview(
            count  = totals.count,
            total  = totals.total,
            latest = latest.map { it.toRetour().copy(items_count = lineCounts[it.id] ?: 0) }
        )
    }

    suspend fun getRetour(id: Int): RetourFournisseur {
        val entity = retourDao.getRetourById(id)
            ?: throw IllegalStateException("Retour introuvable: $id")
        val items = retourDao.getItemsForRetour(id).map { it.toItem() }
        return entity.toRetour(items)
    }

    suspend fun getRetourDetail(id: Int): RetourFournisseur? {
        val retour = retourDao.getRetourById(id) ?: return null
        val items = retourDao.getItemsForRetour(id).map { it.toItem() }
        return retour.toRetour(items)
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

        PerteRepository(db).seedDefaultPerteTypesIfNeeded()

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

                // The movement is the stock change, at the dépôt: out for DECREASE (every motif today),
                // in for INCREASE, and none at all for NONE, which leaves the stock alone.
                if (definition.stockEffect != StockEffect.NONE) movementEntities += StockMovementEntity(
                    product_id   = product.id,
                    product_name = product.name,
                    type         = "retour_fournisseur",
                    direction    = if (definition.stockEffect == StockEffect.INCREASE) "entree" else "sortie",
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
                        dateTime = now, motif = "Retour fournisseur ${numberLabel(retourDao.getNumero(retourId), retourId)} — refusé", photoPath = null, userName = userName,
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
            supplierDao.recomputeBalance(supplierId)
        }
        return mapOf("message" to "Retour enregistré avec succès")
    }

    suspend fun deleteRetour(id: Int): Map<String, Any> {
        val retour = retourDao.getRetourById(id) ?: return mapOf("error" to "Retour introuvable")
        db.withTransaction {
            // Raw DAO delete, not PerteRepository.deletePerte: these linked pertes have affectsStock=false,
            // so their stock was never separately decremented — restoring would incorrectly add quantity back.
            db.perteDao().getPertesBySource("retour_fournisseur", id).forEach { db.perteDao().deletePerteById(it.id) }

            // Removing the return's movements reverses its effect on stock.
            db.stockMovementDao().deleteBySource("retour_fournisseur", id)
            retourDao.deleteItemsForRetour(id)
            retourDao.deleteRetourById(id)
            supplierDao.recomputeBalance(retour.supplier_id)
        }
        return mapOf("message" to "Retour supprimé, stock restauré")
    }
}