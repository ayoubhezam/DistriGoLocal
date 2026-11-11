package com.distrigo.app.data.model

/**
 * What each of a party's documents still has to be paid, once its balance is spent on them (REP-02, audit
 * 2026-11-09) — the rule the debt ageing already follows (spendByAge): the balance is what payments have not yet
 * covered, and a payment settles the oldest debt first, so what is still owed is the most recent credit.
 *
 * A document's credit is what it left unpaid when it was made (`total − montant_paye`). Walking the documents newest
 * first, each takes from the balance as much of its credit as is left, and the rest are settled: a client who paid
 * the whole balance by a versement has every sale « Payée »; one who paid part of it has the oldest sales settled
 * first. A balance larger than every credit (an opening balance no document explains) leaves each its own credit.
 */
object PaymentAllocation {

    /** One document's credit, as the walk reads it: its id and what it left unpaid. */
    data class Credit(val id: Int, val credit: Double)

    /**
     * Spends [balance] on [newestFirst], to the centime: each document's amount still owed, keyed by its id, for those
     * that still owe something. A document missing from the result owes nothing.
     */
    fun spend(balance: Double, newestFirst: List<Credit>): Map<Int, Double> {
        val owed = LinkedHashMap<Int, Double>()
        var left = balance
        for (doc in newestFirst) {
            if (!Amount.owing(left)) break
            val take = Amount.normalize(minOf(left, doc.credit))
            if (Amount.owing(take)) owed[doc.id] = take
            left -= take
        }
        return owed
    }
}
