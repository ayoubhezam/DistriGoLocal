package com.distrigo.app.ui.common

import com.distrigo.app.R
import com.distrigo.app.core.format.MoneyFormatter
import com.distrigo.app.core.text.UiText
import com.distrigo.app.data.model.UnsettledBalanceException

/**
 * What a refused operation tells the user. An exception that carries an amount gets it written in the
 * business's format; any other says its own message.
 */
fun refusalText(error: Throwable, money: MoneyFormatter): UiText = when (error) {
    is UnsettledBalanceException -> UiText.Res(R.string.common_refusal_unsettled_balance, money.da(error.balance))
    else -> userText(error)
}
