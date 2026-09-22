package com.distrigo.app.data.print.lang

import java.nio.charset.Charset

/**
 * The byte encoding a printer expects, and how French survives it.
 *
 * A thermal printer holds one single-byte code page at a time, selected with ESC/POS `ESC t n`. Get it
 * wrong and `é à ç` come out as box-drawing characters — the classic mojibake receipt. Which page a
 * printer honours is not discoverable, and inexpensive units disagree, so this is a per-printer
 * setting confirmed by the test print, like paper and language.
 *
 * [CP1252] is the assumption; [CP858] is the usual fallback on older thermal heads (it is CP850 with
 * the euro sign), and [CP437] is what a printer defaults to when it has never been told otherwise.
 *
 * @param escPosPage the `n` in `ESC t n`.
 * @param charsetName the JVM charset that produces those bytes. `windows-1252` and `IBM00858` are both
 *   in every Android runtime; [charsetOrNull] still guards, because a charset lookup that throws at
 *   print time would be a crash in front of a client.
 */
enum class PrinterCodePage(
    val storageCode: String,
    val label      : String,
    val escPosPage : Int,
    val charsetName: String,
) {
    CP1252("cp1252", "CP1252 (Europe de l'Ouest)", 16, "windows-1252"),
    CP858 ("cp858",  "CP858 (thermique ancien)",   19, "IBM00858"),
    CP437 ("cp437",  "CP437 (par défaut usine)",    0, "IBM437");

    fun charsetOrNull(): Charset? = runCatching { Charset.forName(charsetName) }.getOrNull()

    companion object {
        val DEFAULT = CP1252

        fun fromStorage(code: String?): PrinterCodePage =
            entries.firstOrNull { it.storageCode == code } ?: DEFAULT

        /**
         * Printed by the test print so the user sees at setup time whether their printer honours the
         * selected page — rather than finding out on a client's receipt.
         *
         * Every accented character a DistriGo receipt can actually produce: the French letters that
         * appear in product names, client names and the amount-in-words, plus the typographic dashes
         * and quotes that slip in through pasted notes.
         */
        const val ACCENT_PROBE = "ÉÈÀÇÊÛÔÎ àéèùçêâôîï «»°"
    }
}

/**
 * Text to bytes for a printer, with transliteration where the code page has no room for a character.
 *
 * The fallback matters more than it looks. `String.toByteArray(charset)` replaces anything unmappable
 * with `?`, so a note pasted from a phone keyboard — a curly apostrophe, a non-breaking space, an
 * emoji — turns a readable line into `L?ann?e proch?ine`. Stripping the accent instead leaves plain
 * ASCII, which a user reads without noticing. A receipt is a legal-ish document; legible beats
 * typographically faithful.
 *
 * Nothing calls this until the ESC/POS renderer lands in phase 2. It is here now because it is the
 * half of the accent problem that can be settled and tested without a printer.
 */
object ReceiptEncoding {

    /** Encodes [text] for [codePage], transliterating whatever the page cannot hold. */
    fun encode(text: String, codePage: PrinterCodePage): ByteArray {
        val charset = codePage.charsetOrNull() ?: return transliterate(text).toByteArray(Charsets.US_ASCII)
        val encoder = charset.newEncoder()
        val safe = buildString(text.length) {
            for (ch in text) {
                if (encoder.canEncode(ch)) append(ch) else append(transliterateChar(ch))
            }
        }
        return safe.toByteArray(charset)
    }

    /** Drops every character to plain ASCII. The last resort, and what the tests pin. */
    fun transliterate(text: String): String =
        buildString(text.length) { for (ch in text) append(transliterateChar(ch)) }

    private fun transliterateChar(ch: Char): String = when (ch) {
        'à', 'â', 'ä', 'á', 'ã', 'å' -> "a"
        'À', 'Â', 'Ä', 'Á', 'Ã', 'Å' -> "A"
        'é', 'è', 'ê', 'ë'           -> "e"
        'É', 'È', 'Ê', 'Ë'           -> "E"
        'î', 'ï', 'í', 'ì'           -> "i"
        'Î', 'Ï', 'Í', 'Ì'           -> "I"
        'ô', 'ö', 'ó', 'ò', 'õ'      -> "o"
        'Ô', 'Ö', 'Ó', 'Ò', 'Õ'      -> "O"
        'ù', 'û', 'ü', 'ú'           -> "u"
        'Ù', 'Û', 'Ü', 'Ú'           -> "U"
        'ç'                          -> "c"
        'Ç'                          -> "C"
        'ÿ'                          -> "y"
        'ñ'                          -> "n"
        'Ñ'                          -> "N"
        'œ'                          -> "oe"
        'Œ'                          -> "OE"
        'æ'                          -> "ae"
        'Æ'                          -> "AE"
        'ß'                          -> "ss"
        // Typography that arrives through pasted notes and product names.
        '’', '‘', '´' -> "'"
        '“', '”'           -> "\""
        '«', '»'           -> "\""
        '–', '—', '−' -> "-"
        '…'                     -> "..."
        ' ', ' ', ' ' -> " "
        '€'                     -> "EUR"
        '°'                     -> "deg"
        else -> if (ch.code in 0x20..0x7E) ch.toString() else ""
    }
}
