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
 * a query over a row's `total` and `reste`; [sql] writes them for a table alias.
 *
 * A document's state counts the payments made after it too (REP-02, owner 2026-11-11): `reste` is what is still to be
 * paid on it once the party's versements are spent on its documents, oldest first — see PaymentAllocation and [ofLeft].
 */
enum class PaymentState {
    PAID, PARTIAL, UNPAID;

    companion object {
        fun of(total: Double, paid: Double): PaymentState = when {
            !Amount.owing(total - paid) -> PAID
            paid < Amount.HALF_CENTIME -> UNPAID
            else -> PARTIAL
        }

        /**
         * A sale's or a bon's state once the party's later payments are spent on its documents, oldest first: from what
         * is still [left] to pay on it (its `reste`, REP-02). What was paid with it and later both count as paid.
         */
        fun ofLeft(total: Double, left: Double): PaymentState =
            // As decimals: 1000 − 999.995 is 0.004999… in binary, and would read as nothing paid
            of(total, java.math.BigDecimal.valueOf(total).subtract(java.math.BigDecimal.valueOf(left)).toDouble())

        /** A document's state: from its [reste] when it is known, from what was [paidWithIt] otherwise. */
        fun ofDocument(total: Double, paidWithIt: Double?, reste: Double?): PaymentState =
            if (reste != null) ofLeft(total, reste) else of(total, paidWithIt ?: 0.0)

        /** What has been paid on a document so far: with it and, spent oldest first, after it (REP-02). */
        fun paidSoFar(total: Double, paidWithIt: Double?, reste: Double?): Double =
            if (reste != null) total - reste else paidWithIt ?: 0.0

        /** [Amount.HALF_CENTIME] as SQL text; PaymentStateTest checks the two agree. */
        const val HALF_CENTIME_SQL = "0.005"

        /**
         * Half a centime, less the binary error of a difference: what was paid is `total − reste`, and 1000 − 999.995
         * is 0.004999… in a double — half a centime, as [ofLeft] reads it with decimals.
         */
        const val PAID_SOMETHING_SQL = "0.0049999999"

        const val SQL_PAID = "(reste < $HALF_CENTIME_SQL)"
        const val SQL_UNPAID = "(reste >= $HALF_CENTIME_SQL AND total - reste < $PAID_SOMETHING_SQL)"
        const val SQL_PARTIAL = "(reste >= $HALF_CENTIME_SQL AND total - reste >= $PAID_SOMETHING_SQL)"

        /** The condition for [state] over the columns of the table aliased [alias] (`v`, `o`…). */
        fun sql(state: PaymentState, alias: String): String {
            val condition = when (state) {
                PAID -> SQL_PAID
                UNPAID -> SQL_UNPAID
                PARTIAL -> SQL_PARTIAL
            }
            return condition.replace("total", "$alias.total").replace("reste", "$alias.reste")
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
