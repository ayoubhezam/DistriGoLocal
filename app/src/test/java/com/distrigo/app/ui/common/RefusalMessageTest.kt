package com.distrigo.app.ui.common

import com.distrigo.app.core.format.MoneyFormat
import com.distrigo.app.core.format.MoneyFormatter
import com.distrigo.app.data.model.UnsettledBalanceException
import org.junit.Assert.assertEquals
import org.junit.Test

class RefusalMessageTest {

    @Test
    fun anUnsettledBalanceIsWrittenInTheBusinesssFormat() {
        val error = UnsettledBalanceException(1236790.5)
        assertEquals(
            "Impossible de supprimer : le solde n'est pas nul (1 236 790,50 DA).",
            refusalMessage(error, MoneyFormatter.printer(MoneyFormat.SPACES))
        )
        assertEquals(
            "Impossible de supprimer : le solde n'est pas nul (1,236,790.50 DA).",
            refusalMessage(error, MoneyFormatter.printer(MoneyFormat.COMMAS))
        )
    }

    @Test
    fun anAdvanceKeepsItsSign() {
        assertEquals(
            "Impossible de supprimer : le solde n'est pas nul (-80,00 DA).",
            refusalMessage(UnsettledBalanceException(-80.0), MoneyFormatter.printer(MoneyFormat.SPACES))
        )
    }

    @Test
    fun anyOtherErrorSaysItsOwnMessage() {
        val money = MoneyFormatter.printer(MoneyFormat.SPACES)
        assertEquals("Stock insuffisant", refusalMessage(IllegalStateException("Stock insuffisant"), money))
        assertEquals("Erreur inconnue", refusalMessage(RuntimeException(), money))
    }

    /** Whatever only logs the exception still reads a sentence, with no amount written by the data layer. */
    @Test
    fun theExceptionsOwnMessageCarriesNoAmount() {
        assertEquals("Impossible de supprimer : le solde n'est pas nul.", UnsettledBalanceException(12.0).message)
    }
}
