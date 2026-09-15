package com.distrigo.app.data.model

// What the client and supplier detail screens show of a party's history (audit item C9): figures
// over all of it, and only its latest entries. The history itself stays in the paged
// "Voir tout l'historique" screens.

/** A client's sales and payments as its detail screen shows them. */
data class ClientLedgerPreview(
    val totalFacture : Double = 0.0,                  // the sales' totals
    val totalPaye    : Double = 0.0,                  // amounts paid at sale, plus separate payments
    val count        : Int = 0,                       // sales + payments
    val latest       : List<ClientTransaction> = emptyList()
)

/** A supplier's purchase orders and payments as its detail screen shows them. */
data class SupplierLedgerPreview(
    val totalFacture : Double = 0.0,                  // the orders' totals
    val totalPaye    : Double = 0.0,                  // amounts paid on orders, plus separate payments
    val count        : Int = 0,                       // orders + payments + the "Solde initial" entry
    val latest       : List<SupplierTransaction> = emptyList()
)

/** A party's returns as its detail screen shows them. */
data class RetourPreview<T>(
    val count  : Int = 0,
    val total  : Double = 0.0,
    val latest : List<T> = emptyList()
)
