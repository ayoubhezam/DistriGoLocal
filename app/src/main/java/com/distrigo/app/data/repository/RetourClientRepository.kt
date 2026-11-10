package com.distrigo.app.data.repository

import com.distrigo.app.core.text.refuse
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
import com.distrigo.app.data.model.Amount
import com.distrigo.app.data.model.Quantity
import com.distrigo.app.data.model.numberLabel
import com.distrigo.app.R

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
        unit_price = this.unit_price, total_price = this.total_price, motif = this.motif
    )

    private suspend fun RetourClientEntity.toRetour(items: List<RetourClientItem>? = null): RetourClient =
        toRetourWith(
            clientName = clientDao.getClientById(this.client_id)?.name ?: "Client inconnu",
            itemsCount = items?.size,
            items      = items
        ).copy(vente_numero = vente_id?.let { numberLabel(retourDao.getVenteNumero(it), it) })

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
        numero = this.numero, vente_id = this.vente_id, emplacement = this.emplacement
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
            ?: refuse(R.string.error_return_not_found, id)
        val items = retourDao.getItemsForRetour(id).map { it.toItem() }
        return entity.toRetour(items)
    }

    suspend fun getRetourDetail(id: Int): RetourClient? {
        val retour = retourDao.getRetourById(id) ?: return null
        val items = retourDao.getItemsForRetour(id).map { it.toItem() }
        return retour.toRetour(items)
    }

    // ── Écriture ──
    /**
     * A client return, made from one of the client's sales [venteId] (FIN-02, STK-02; owner, 2026-11-10).
     *
     * Each item names a line of that sale (`vente_item_id`) with a `quantity` and a `motif` (a caller giving none takes
     * [motif]). A returned line is valued at its sale line's price and keeps that line's cost, so the client is credited
     * what they paid and the Résultat takes back what the goods cost. Of each sale line, no more may come back than
     * was sold less what earlier returns took back from it. The goods come back to [emplacement] — "depot" or
     * "camion", the sale's source unless the user changed it — and a line returned as a perte goes out of it again.
     * The whole write goes through the dépôt guard, as every write that moves stock does.
     */
    suspend fun createRetour(
        clientId   : Int,
        venteId    : Int,
        emplacement: String,
        date       : String,
        motif      : String?,
        note       : String?,
        items      : List<Map<String, Any?>>,
        userName   : String? = null
    ): Map<String, Any> {
        if (items.isEmpty()) return mapOf("error" to com.distrigo.app.core.text.appText(R.string.retours_add_one))
        require(emplacement == "depot" || emplacement == "camion") { "emplacement: $emplacement" }

        PerteRepository(db).seedDefaultPerteTypesIfNeeded()

        val saleLines = retourDao.getSaleLinesWithReturned(venteId).associateBy { it.vente_item_id }
        val productIds = items.mapNotNull { saleLines[(it["vente_item_id"] as Number).toInt()]?.product_id }
        return DepotStockGuard(db).guardOrError(productIds) {
            val client = clientDao.getClientById(clientId)
                ?: refuse(R.string.error_client_not_found, clientId)
            val vente = db.venteDao().getVenteById(venteId)
            if (vente == null || vente.client_id != clientId) refuse(R.string.error_vente_not_found, venteId)
            val now = java.time.Instant.now().toString()

            // Each line with its own motif — what decides its stock effect and its linked perte — or,
            // a caller giving none, the return's.
            val lines = items.map { map ->
                val itemId = (map["vente_item_id"] as Number).toInt()
                val sale = saleLines[itemId] ?: refuse(R.string.error_vente_not_found, venteId)
                val product = productDao.getProductById(sale.product_id)
                    ?: refuse(R.string.error_product_not_found, sale.product_id)
                val quantity = map.lineQuantity()
                unitError(product.name, product.unit_type, quantity)?.let { return@guardOrError mapOf("error" to it) }
                ReturnedLine(sale, product, quantity, (map["motif"] as? String) ?: motif)
            }
            // Of each sale line, at most what is left of it — whatever motifs it is split across.
            lines.groupBy { it.sale.vente_item_id }.values.forEach { same ->
                val sale = same.first().sale
                if (Quantity.exceeds(same.sumOf { it.quantity }, sale.left)) {
                    return@guardOrError mapOf("error" to com.distrigo.app.core.text.appText(
                        R.string.retours_exceeds_sale, same.first().product.name, Quantity.format(sale.left)
                    ))
                }
            }
            // The return's own motif: its lines' when they share one, none when they differ.
            val documentMotif = lines.map { it.motif }.distinct().singleOrNull()
            val total = Amount.documentTotal(lines.map { Amount.lineTotal(it.quantity, it.sale.unit_price) })

            val retourId = retourDao.insertRetour(
                RetourClientEntity(
                    client_id = clientId, tournee_id = null, date = date, motif = documentMotif, note = note,
                    total = total, created_at = now, vente_id = venteId, emplacement = emplacement
                )
            ).toInt()

            val itemEntities = lines.map { line ->
                val product    = line.product
                val definition = RetourClientMotifs.resolve(line.motif)
                val unitPrice  = line.sale.unit_price
                val totalPrice = Amount.lineTotal(line.quantity, unitPrice)

                // The movement is the stock change, into [emplacement]. NONE still comes in — the physical item
                // really did come back — and the linked Perte below takes it out again, giving a full paper trail
                // instead of a silent no-op. Inserted here, not after the loop, so that Perte's stock check already
                // sees the returned quantity.
                db.stockMovementDao().insert(StockMovementEntity(
                    product_id   = product.id,
                    product_name = product.name,
                    type         = "retour_client",
                    direction    = if (definition.stockEffect == StockEffect.DECREASE) "sortie" else "entree",
                    quantity     = line.quantity,
                    emplacement  = emplacement,
                    source_label = client.name,
                    source_type  = "retour_client",
                    source_id    = retourId,
                    unit_price   = unitPrice,
                    total_value  = totalPrice,
                    user_name    = userName,
                    note         = line.motif,
                    created_at   = now
                ))

                definition.perteType?.let { builtIn ->
                    val perteType = PerteRepository(db).findDefaultPerteType(builtIn)
                        ?: throw IllegalStateException("Type de perte introuvable : ${builtIn.seedName}. Assurez-vous que seedDefaultPerteTypesIfNeeded() a été exécuté.")
                    PerteRepository(db).addPerte(
                        typeId = perteType.id, productId = product.id, quantity = line.quantity, source = emplacement,
                        dateTime = now, motif = "Retour client ${numberLabel(retourDao.getNumero(retourId), retourId)}", photoPath = null, userName = userName,
                        sourceType = "retour_client", sourceId = retourId
                    )
                }

                RetourClientItemEntity(
                    retour_id = retourId, product_id = product.id, product_name = product.name,
                    unit_type = product.unit_type, quantity = line.quantity,
                    unit_price = unitPrice, total_price = totalPrice, motif = line.motif,
                    vente_item_id = line.sale.vente_item_id, purchase_price_snapshot = line.sale.purchase_price_snapshot
                )
            }
            retourDao.insertItems(itemEntities)
            clientDao.recomputeBalance(clientId)
            mapOf("message" to "Retour enregistré avec succès")
        }
    }

    /** A line of a return being written: the sale line it comes from, its product, how much, and why. */
    private data class ReturnedLine(
        val sale    : com.distrigo.app.data.local.dao.SaleLineReturned,
        val product : ProductEntity,
        val quantity: Double,
        val motif   : String?,
    )

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