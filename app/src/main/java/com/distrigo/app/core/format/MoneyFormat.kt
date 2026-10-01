package com.distrigo.app.core.format

/**
 * How the business writes an amount: which mark separates the thousands, which one the centimes.
 *
 * A business setting, not a phone's: every receipt any rep prints must read the same. [key] is what the
 * settings row stores — never the ordinal, so reordering or adding a format cannot change a saved choice.
 *
 * Display only. Exports, imports, drafts and QR contents keep their own fixed, machine-readable shapes
 * whatever is chosen here.
 */
enum class MoneyFormat(val key: String, val thousands: Char, val decimal: Char) {
    /** `1 236 790,50 DA` — the French and Algerian way. */
    SPACES("spaces", ' ', ','),

    /** `1,236,790.50 DA` */
    COMMAS("commas", ',', '.'),

    /** `1.236.790,50 DA` */
    DOTS("dots", '.', ',');

    companion object {
        val DEFAULT = SPACES

        /** The format a stored [key] names; anything unknown, missing or from a newer version is the default. */
        fun fromKey(key: String?): MoneyFormat = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
