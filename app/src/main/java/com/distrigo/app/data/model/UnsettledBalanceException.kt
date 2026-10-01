package com.distrigo.app.data.model

/**
 * A client or supplier cannot go to the bin while its account is not settled: [balance] is what is
 * still owed (or, negative, the advance held).
 *
 * The amount travels as a number, not in the message: how it is written is the screen's business —
 * see com.distrigo.app.ui.common.refusalMessage. The message is the sentence without it, for anything
 * that only logs.
 */
class UnsettledBalanceException(val balance: Double) :
    IllegalStateException("Impossible de supprimer : le solde n'est pas nul.")
