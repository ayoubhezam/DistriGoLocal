package com.distrigo.app.data.model

/**
 * Where a sale or a bon stands against what was paid with it: « Payée », « Partielle » or « Impayée ». One rule for
 * every list, filter, ribbon, detail and report (FIN-05, docs/fin05_payment_state_plan.md §3).
 *
 * Totals are sums of decimals: 3 × 150,15 is 450.45000000000005, and « Tout réglé » pays 450.45. Compared exactly,
 * that sale was « Partielle » with « Reste 0,00 DA ». So the rule reads the amount left to pay to the half centime,
 * as the parties' balances already are (Amount.owing):
 *
 *  - [PAID]: less than half a centime left — settled, paid ahead, or nothing to pay (a total of 0, owner D2);
 *  - [UNPAID]: something left, and less than half a centime paid;
 *  - [PARTIAL]: something left, and something paid.
 *
 * The three cover every document exactly once. [SQL_PAID], [SQL_UNPAID] and [SQL_PARTIAL] are the same conditions for
 * a query over a row's `total` and `montant_paye`; [sql] writes them for a table alias.
 */
enum class PaymentState {
    PAID, PARTIAL, UNPAID;

    companion object {
        fun of(total: Double, paid: Double): PaymentState = when {
            !Amount.owing(total - paid) -> PAID
            paid < Amount.HALF_CENTIME -> UNPAID
            else -> PARTIAL
        }

        /** [Amount.HALF_CENTIME] as SQL text; PaymentStateTest checks the two agree. */
        const val HALF_CENTIME_SQL = "0.005"

        const val SQL_PAID = "(total - montant_paye < $HALF_CENTIME_SQL)"
        const val SQL_UNPAID = "(total - montant_paye >= $HALF_CENTIME_SQL AND montant_paye < $HALF_CENTIME_SQL)"
        const val SQL_PARTIAL = "(total - montant_paye >= $HALF_CENTIME_SQL AND montant_paye >= $HALF_CENTIME_SQL)"

        /** The condition for [state] over the columns of the table aliased [alias] (`v`, `o`…). */
        fun sql(state: PaymentState, alias: String): String {
            val condition = when (state) {
                PAID -> SQL_PAID
                UNPAID -> SQL_UNPAID
                PARTIAL -> SQL_PARTIAL
            }
            return condition.replace("total", "$alias.total").replace("montant_paye", "$alias.montant_paye")
        }

        /**
         * A document's state from the keys the filters and ribbons use — « paye », « partiel », « impaye » — or
         * the ledgers' « PAYEE », « PARTIELLE », « IMPAYEE »; null for anything else (« TOUTES »).
         */
        fun fromKey(key: String?): PaymentState? = when (key) {
            "paye", "PAYEE" -> PAID
            "partiel", "PARTIELLE" -> PARTIAL
            "impaye", "IMPAYEE" -> UNPAID
            else -> null
        }

        /** The key the Ventes, Achats and tournée filters and ribbons use. */
        fun keyOf(state: PaymentState): String = when (state) {
            PAID -> "paye"
            PARTIAL -> "partiel"
            UNPAID -> "impaye"
        }
    }
}
