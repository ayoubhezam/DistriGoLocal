package com.distrigo.app.data.repository

import com.distrigo.app.data.model.Quantity
import androidx.room.withTransaction
import com.distrigo.app.data.local.dao.DepotStockRow
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.R
import com.distrigo.app.core.text.UiText

/**
 * Strict stock: with "Autoriser le stock négatif" off, no write may leave a product's dépôt stock
 * (`stock - camion_stock`) below zero. And whatever the setting, no write may leave the camion below zero
 * (STK-04, audit 2026-11-09): what a truck does not hold cannot leave it — a tournée sale and a camion perte
 * already refuse it, and this catches the paths that take truck stock back, such as deleting a client return
 * that went into the camion after the truck was unloaded.
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

    /**
     * Runs [block] in a transaction and refuses it if, for any of [productIds], it leaves the camion — always — or the
     * dépôt — under strict stock — below zero and lower than it started.
     */
    suspend fun <T> guard(productIds: Collection<Int>, block: suspend () -> T): T = db.withTransaction {
        if (productIds.isEmpty()) return@withTransaction block()
        val strict = db.businessSettingsDao().allowNegativeStock() == false
        val ids = productIds.distinct()
        val before = stocks(ids).associateBy { it.id }
        val result = block()
        val after = stocks(ids)
        val camionBroken = after.filter { row -> worse(row.camion, before[row.id]?.camion ?: 0.0) }
        if (camionBroken.isNotEmpty()) throw CamionStockException(camionText(camionBroken))
        if (strict) {
            val broken = after.filter { row -> worse(row.depot, before[row.id]?.depot ?: 0.0) }
            if (broken.isNotEmpty()) throw DepotStockException(text(broken))
        }
        result
    }

    /** Below zero and lower than it was: stock already negative may still be restocked or edited without being made worse. */
    private fun worse(now: Double, was: Double): Boolean = Quantity.isBelow(now, 0.0) && Quantity.isBelow(now, was)

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
    private suspend fun stocks(ids: List<Int>): List<DepotStockRow> =
        ids.chunked(900).flatMap { db.productDao().getDepotStocks(it) }

    private fun camionText(broken: List<DepotStockRow>): UiText {
        val first = broken.first()
        // The quantity with its unit word in the app's language (« -1 carton », « -1 علبة »)
        return UiText.Res(R.string.error_camion_stock_negative, first.name, com.distrigo.app.ui.common.quantityText(first.camion, first.unit_type))
    }

    private fun text(broken: List<DepotStockRow>): UiText {
        val first = broken.first()
        // The quantity with its unit word in the app's language (« -2 cartons », « -2 علبة »), as the camion's refusal
        val left = com.distrigo.app.ui.common.quantityText(first.depot, first.unit_type)
        return if (broken.size > 1) UiText.Res(R.string.error_depot_stock_insufficient_more, first.name, broken.size - 1, left)
        else UiText.Res(R.string.error_depot_stock_insufficient, first.name, left)
    }
}

/** A write refused by strict stock. An IllegalStateException, so the forms show its message as they show any other. */
open class DepotStockException(text: UiText) : com.distrigo.app.core.text.UserFacingException(text)

/**
 * A write that would leave the camion below zero (STK-04). A [DepotStockException], so whatever already reports a
 * stock refusal reports this one the same way.
 */
class CamionStockException(text: UiText) : DepotStockException(text)
