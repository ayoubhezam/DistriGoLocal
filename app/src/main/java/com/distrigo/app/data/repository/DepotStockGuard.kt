package com.distrigo.app.data.repository

import com.distrigo.app.data.model.Quantity
import androidx.room.withTransaction
import com.distrigo.app.data.local.dao.DepotStockRow
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.R
import com.distrigo.app.core.text.UiText

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
        if (broken.isNotEmpty()) throw DepotStockException(text(broken))
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
            mapOf("error" to e.message)
        }

    // SQLite takes at most 999 bound parameters.
    private suspend fun depots(ids: List<Int>): List<DepotStockRow> =
        ids.chunked(900).flatMap { db.productDao().getDepotStocks(it) }

    private fun text(broken: List<DepotStockRow>): UiText {
        val first = broken.first()
        val left = Quantity.format(first.depot)
        return if (broken.size > 1) UiText.Res(R.string.error_depot_stock_insufficient_more, first.name, broken.size - 1, left, first.unit_type)
        else UiText.Res(R.string.error_depot_stock_insufficient, first.name, left, first.unit_type)
    }
}

/** A write refused by strict stock. An IllegalStateException, so the forms show its message as they show any other. */
class DepotStockException(text: UiText) : com.distrigo.app.core.text.UserFacingException(text)
