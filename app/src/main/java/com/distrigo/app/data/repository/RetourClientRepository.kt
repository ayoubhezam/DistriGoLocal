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
import com.distrigo.app.data.model.RetourPreview
import com.distrigo.app.data.model.StockEffect
import com.distrigo.app.data.model.numberLabel

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

    private suspend fun RetourClientEntity.toRetour(items: List<RetourClientItem>? = null): RetourClient =
        toRetourWith(
            clientName = clientDao.getClientById(this.client_id)?.name ?: "Client inconnu",
            itemsCount = items?.size,
            items      = items
        )

    /**
     * The one place a retour row becomes a RetourClient. [toRetour] looks the client up itself; a
     * caller listing one client's returns already knows the name and has the line counts, and
     * passes them in, so the two paths cannot drift apart field by field.
     */
    private fun RetourClientEntity.toRetourWith(
        clientName: String,
        itemsCount: Int?,
        items: List<RetourClientItem>?
    ) = RetourClient(
        id = this.id, client_id = this.client_id, client_name = clientName,
        tournee_id = this.tournee_id, date = this.date, motif = this.motif, note = this.note,
        total = this.total, created_at = this.created_at, items_count = itemsCount, items = items,
        numero = this.numero
    )

    // ── Lecture ──
    /**
     * One client's returns for its list screen: the rows filtered in SQL, every line count in one
     * query, and the client's name looked up once.
     *
     * This used to load every client's returns — an items query and a client lookup for each, so
     * 1 + 2R queries over the whole table — and filter them down to this client in Compose.
     */
    suspend fun getRetoursForClient(clientId: Int): List<RetourClient> {
        val entities = retourDao.getRetoursForClient(clientId)
        if (entities.isEmpty()) return emptyList()
        val counts = retourDao.getItemCountsForRetours(entities.map { it.id })
            .associate { it.retour_id to it.count }
        val clientName = clientDao.getClientById(clientId)?.name ?: "Client inconnu"
        return entities.map { it.toRetourWith(clientName, counts[it.id] ?: 0, items = null) }
    }

    /**
     * A client's returns as its detail screen shows them: how many, their total, and the [limit]
     * latest with their line counts. The detail screen used to load every client's returns, with one
     * items query each, and filter them down to this client in Kotlin.
     */
    suspend fun getDetailPreview(clientId: Int, limit: Int): RetourPreview<RetourClient> {
        val totals = retourDao.getRetourTotalsForClient(clientId)
        val latest = retourDao.getLatestRetoursForClient(clientId, limit)
        val lineCounts = if (latest.isEmpty()) emptyMap()
                         else retourDao.getItemCountsForRetours(latest.map { it.id }).associate { it.retour_id to it.count }
        return RetourPreview(
            count  = totals.count,
            total  = totals.total,
            latest = latest.map { it.toRetour().copy(items_count = lineCounts[it.id] ?: 0) }
        )
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
            val itemEntities = lines.map { (product, quantity) ->
                val unitPrice  = product.selling_price
                val totalPrice = quantity * unitPrice

                // The movement is the stock change, into or out of the camion. NONE still comes in —
                // the physical item really did come back — and the linked Perte below takes it out
                // again, giving a full paper trail instead of a silent no-op. Inserted here, not after
                // the loop, so that Perte's camion check already sees the returned quantity.
                db.stockMovementDao().insert(StockMovementEntity(
                    product_id   = product.id,
                    product_name = product.name,
                    type         = "retour_client",
                    direction    = if (definition.stockEffect == StockEffect.DECREASE) "sortie" else "entree",
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
                ))

                definition.perteType?.let { builtIn ->
                    val perteType = PerteRepository(db).findDefaultPerteType(builtIn)
                        ?: throw IllegalStateException("Type de perte introuvable : ${builtIn.seedName}. Assurez-vous que seedDefaultPerteTypesIfNeeded() a été exécuté.")
                    PerteRepository(db).addPerte(
                        typeId = perteType.id, productId = product.id, quantity = quantity, source = "camion",
                        dateTime = now, motif = "Retour client ${numberLabel(retourDao.getNumero(retourId), retourId)}", photoPath = null, userName = userName,
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
            clientDao.recomputeBalance(clientId)
        }
        return mapOf("message" to "Retour enregistré avec succès")
    }

    suspend fun deleteRetour(id: Int): Map<String, Any> {
        val retour = retourDao.getRetourById(id) ?: return mapOf("error" to "Retour introuvable")
        db.withTransaction {
            // The linked pertes go with their movements, which puts back what they took out. Deleted
            // here rather than through PerteRepository.deletePerte, which refuses any perte linked to
            // a return — so deleting a "Produit défectueux" or "Produit périmé" return used to throw.
            db.perteDao().getPertesBySource("retour_client", id).forEach { perte ->
                db.stockMovementDao().deleteBySource("perte", perte.id)
                db.perteDao().deletePerteById(perte.id)
            }
            // And the return's own movements, which takes back what it brought in.
            db.stockMovementDao().deleteBySource("retour_client", id)
            retourDao.deleteItemsForRetour(id)
            retourDao.deleteRetourById(id)
            clientDao.recomputeBalance(retour.client_id)
        }
        return mapOf("message" to "Retour supprimé, stock restauré")
    }
}