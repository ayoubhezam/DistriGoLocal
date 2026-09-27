package com.distrigo.app.data.model

/**
 * What the forms may take out of the dépôt, from "Autoriser le stock négatif".
 *
 * The caps here only shape the controls — a stepper's `max`, a disabled "add" — so the user rarely
 * meets a refusal. The rule itself is enforced on every write by DepotStockGuard.
 *
 * Every cap takes the product as the form holds it. When a document is being edited, that copy
 * already has the document's own quantity added back, so the cap is what this document may take.
 */
data class StockPolicy(val allowNegative: Boolean) {

    val isStrict: Boolean get() = !allowNegative

    /**
     * The dépôt stock of [product], never below zero; null when negative stock is allowed.
     *
     * [ownQuantity] is what the document being edited already took from the dépôt, for a form whose
     * product copy is the live one rather than one with the document's own quantity added back.
     */
    fun depotCap(product: Product, ownQuantity: Double = 0.0): Double? =
        if (allowNegative) null else maxOf(0.0, product.stock - product.camion_stock + ownQuantity)

    /**
     * The most a chargement may leave in the camion: all of the stock, which leaves the dépôt at zero.
     * Never below what the camion already holds, so a dépôt that is already negative does not force
     * an unload the user did not ask for.
     */
    fun camionTargetCap(product: Product): Double? =
        if (allowNegative) null else maxOf(product.stock, product.camion_stock)

    /** True when [quantity] of [product] is more than strict stock lets the dépôt give. */
    fun exceedsDepot(product: Product, quantity: Double): Boolean =
        depotCap(product)?.let { quantity > it + EPSILON } ?: false

    companion object {
        /** Until the setting is read: the app's behaviour before strict stock existed. */
        val ALLOWED = StockPolicy(allowNegative = true)
        private const val EPSILON = 1e-6
    }
}
