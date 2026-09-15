package com.distrigo.app.data.local.dao

// Aggregate rows for the client and supplier detail screens (audit item C9): the figures they show
// over a party's whole history, computed in SQL instead of by loading that history.

/** A party's invoices — its sales, or its purchase orders — counted, with totals and amounts paid summed. */
data class InvoiceTotals(val count: Int, val total: Double, val paid: Double)

/** A party's separate payments, counted and summed. */
data class PaymentTotals(val count: Int, val total: Double)

/** A party's returns, counted and summed. */
data class RetourTotals(val count: Int, val total: Double)

/** How many lines one return has. */
data class RetourItemCount(val retour_id: Int, val count: Int)
