package com.distrigo.app.ui.common

import com.distrigo.app.R
import com.distrigo.app.core.format.MoneyFormat
import com.distrigo.app.core.format.MoneyFormatter
import com.distrigo.app.core.text.UiText
import com.distrigo.app.data.model.UnsettledBalanceException
import org.junit.Assert.assertEquals
import org.junit.Test

class RefusalMessageTest {

    @Test
    fun anUnsettledBalanceIsWrittenInTheBusinesssFormat() {
        val error = UnsettledBalanceException(1236790.5)
        assertEquals(
            UiText.Res(R.string.common_refusal_unsettled_balance, "1 236 790,50 DA"),
            refusalText(error, MoneyFormatter.plain(MoneyFormat.SPACES))
        )
        assertEquals(
            UiText.Res(R.string.common_refusal_unsettled_balance, "1,236,790.50 DA"),
            refusalText(error, MoneyFormatter.plain(MoneyFormat.COMMAS))
        )
    }

    @Test
    fun anAdvanceKeepsItsSign() {
        assertEquals(
            UiText.Res(R.string.common_refusal_unsettled_balance, "-80,00 DA"),
            refusalText(UnsettledBalanceException(-80.0), MoneyFormatter.plain(MoneyFormat.SPACES))
        )
    }

    @Test
    fun anyOtherErrorSaysItsOwnMessage() {
        val money = MoneyFormatter.plain(MoneyFormat.SPACES)
        assertEquals(UiText.Raw("Stock insuffisant"), refusalText(IllegalStateException("Stock insuffisant"), money))
        assertEquals(UiText.Res(R.string.common_error_unknown), refusalText(RuntimeException(), money))
    }

    /** Whatever only logs the exception still reads a sentence, with no amount written by the data layer. */
    @Test
    fun theExceptionsOwnMessageCarriesNoAmount() {
        assertEquals("Impossible de supprimer : le solde n'est pas nul.", UnsettledBalanceException(12.0).message)
    }
}
