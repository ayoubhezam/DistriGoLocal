package com.distrigo.app.data.repository

import com.distrigo.app.data.model.Quantity
import androidx.room.withTransaction
import com.distrigo.app.data.local.dao.DepotStockRow
import com.distrigo.app.data.local.database.AppDatabase

/**
 * Strict stock: with "Autoriser le stock négatif" off, no write may leave a product's dépôt stock
 * (`stock - camion_stock`) below zero.
 *
 * ### Why a net check around the whole write
 *
 * Every write path moves stock through the ledger (StockLedger.kt), and several rewrite a document by
 * removing its movements and recording new ones. Checking each statement would refuse a harmless edit
 * that dips below zero halfway — a received bon rewritten line by line — so [guard] compares the
 * products' dépôt stock before the write with what it is once the write is done, inside the same
 * transaction, and throws (rolling everything back) when a product ends below zero *and lower than it
 * started*. That second condition is what lets a product already below zero — stock the app allowed
 * before strict mode was turned on — still be restocked, returned to or edited without being made
 * worse.
 *
 * The forms cap their steppers with [StockPolicy] so the user rarely meets this; it is the rule that
 * holds whatever a screen, a stale draft or a second draft holding the same stock tries.
 */
internal class DepotStockGuard(private val db: AppDatabase) {

    /** Runs [block] in a transaction and refuses it if it breaks strict stock for any of [productIds]. */
    suspend fun <T> guard(productIds: Collection<Int>, block: suspend () -> T): T = db.withTransaction {
        if (db.businessSettingsDao().allowNegativeStock() != false || productIds.isEmpty()) {
            return@withTransaction block()
        }
        val ids = productIds.distinct()
        val before = depots(ids).associate { it.id to it.depot }
        val result = block()
        val broken = depots(ids).filter { row ->
            val was = before[row.id] ?: 0.0
            Quantity.isBelow(row.depot, 0.0) && Quantity.isBelow(row.depot, was)
        }
        if (broken.isNotEmpty()) throw DepotStockException(message(broken))
        result
    }

    /**
     * [guard], for the repositories that report a failure as an `"error"` entry rather than by
     * throwing: a refusal becomes that entry. Anything else [block] throws still propagates.
     */
    suspend fun guardOrError(productIds: Collection<Int>, block: suspend () -> Map<String, Any>): Map<String, Any> =
        try {
            guard(productIds, block)
        } catch (e: DepotStockException) {
            mapOf("error" to (e.message ?: "Stock dépôt insuffisant"))
        }

    // SQLite takes at most 999 bound parameters.
    private suspend fun depots(ids: List<Int>): List<DepotStockRow> =
        ids.chunked(900).flatMap { db.productDao().getDepotStocks(it) }

    private fun message(broken: List<DepotStockRow>): String {
        val first = broken.first()
        val others = if (broken.size > 1) " (et ${broken.size - 1} autre(s) produit(s))" else ""
        return "Stock dépôt insuffisant pour « ${first.name} »$others : l'opération le laisserait à " +
            "${Quantity.format(first.depot)} ${first.unit_type}. Le stock négatif est désactivé dans Paramètres."
    }
}

/** A write refused by strict stock. An IllegalStateException, so the forms show its message as they show any other. */
class DepotStockException(message: String) : IllegalStateException(message)
