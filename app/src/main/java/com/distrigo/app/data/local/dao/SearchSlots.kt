package com.distrigo.app.data.local.dao

/**
 * A search split for a Room query that cannot loop over words: three slots, each a word the row must
 * contain — the same "every word, any order" match as the rest of the app's searches. Words past the
 * second stay together, as one phrase, in the third; an unused slot is '' and matches everything.
 */
fun searchSlots(search: String): List<String> {
    val words = search.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return listOf(
        words.getOrElse(0) { "" },
        words.getOrElse(1) { "" },
        words.drop(2).joinToString(" "),
    )
}
