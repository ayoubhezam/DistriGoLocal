package com.distrigo.app.ui.components

private val UNITS = arrayOf(
    "zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
    "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize",
    "dix-sept", "dix-huit", "dix-neuf"
)

private val TENS = arrayOf(
    "", "dix", "vingt", "trente", "quarante", "cinquante", "soixante", "soixante-dix", "quatre-vingt", "quatre-vingt-dix"
)

private fun convertBelowHundred(n: Int): String {
    if (n < 20) return UNITS[n]

    val tensIndex = n / 10
    val remainder = n % 10

    return when (tensIndex) {
        // 70-79: soixante + (dix..dix-neuf), with "et" only before onze (71)
        7 -> {
            val teenPart = UNITS[10 + remainder]
            if (remainder == 1) "soixante et onze" else "soixante-$teenPart"
        }
        // 90-99: quatre-vingt + (dix..dix-neuf), never uses "et"
        9 -> "quatre-vingt-${UNITS[10 + remainder]}"
        // 80-89: quatre-vingts alone (with s), quatre-vingt-X otherwise (no s, no et)
        8 -> if (remainder == 0) "quatre-vingts" else "quatre-vingt-${UNITS[remainder]}"
        // 20,30,40,50,60: standard tens, "et un" for remainder 1
        else -> when (remainder) {
            0 -> TENS[tensIndex]
            1 -> "${TENS[tensIndex]} et un"
            else -> "${TENS[tensIndex]}-${UNITS[remainder]}"
        }
    }
}

private fun convertBelowThousand(n: Int): String {
    if (n < 100) return convertBelowHundred(n)

    val hundreds = n / 100
    val remainder = n % 100

    val hundredsPart = when {
        hundreds == 1 -> "cent"
        remainder == 0 -> "${UNITS[hundreds]} cents"
        else -> "${UNITS[hundreds]} cent"
    }

    return if (remainder == 0) hundredsPart else "$hundredsPart ${convertBelowHundred(remainder)}"
}

private fun convertNumberToFrenchWords(n: Int): String {
    if (n == 0) return "zéro"

    var remaining = n
    val parts = mutableListOf<String>()

    val millions = remaining / 1_000_000
    remaining %= 1_000_000
    val thousands = remaining / 1_000
    remaining %= 1_000
    val units = remaining

    if (millions > 0) {
        parts.add(if (millions == 1) "un million" else "${convertBelowThousand(millions)} millions")
    }

    if (thousands > 0) {
        parts.add(if (thousands == 1) "mille" else "${convertBelowThousand(thousands)} mille")
    }

    if (units > 0) {
        parts.add(convertBelowThousand(units))
    }

    return parts.joinToString(" ")
}

fun numberToFrenchWords(amount: Double): String {
    val n = Math.round(amount).toInt()
    if (n == 0) return "ZÉRO DINAR ALGÉRIEN"
    val words = convertNumberToFrenchWords(n).uppercase()
    val suffix = if (n > 1) "DINARS ALGÉRIENS" else "DINAR ALGÉRIEN"
    return "$words $suffix"
}