package com.distrigo.app.ui.common

import com.distrigo.app.core.format.MoneyFormatter
import com.distrigo.app.data.model.UnsettledBalanceException

/**
 * What a refused operation tells the user. An exception that carries an amount gets it written in the
 * business's format; any other says its own message.
 */
fun refusalMessage(error: Throwable, money: MoneyFormatter): String = when (error) {
    is UnsettledBalanceException ->
        "Impossible de supprimer : le solde n'est pas nul (${money.da(error.balance)})."
    else -> error.message ?: "Erreur inconnue"
}
