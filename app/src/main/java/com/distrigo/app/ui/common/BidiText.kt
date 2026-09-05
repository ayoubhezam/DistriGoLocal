package com.distrigo.app.ui.common

/**
 * Fences a name off so it cannot reorder the text around it.
 *
 * Client and supplier names in this app are frequently Arabic, and they get joined to French text —
 * "جلال · 4 produits · il y a 3 h". Left alone, the bidirectional algorithm resolves the neutral
 * "·" separators against the *strongest* neighbouring direction rather than the paragraph's, so the
 * RTL name pulls the separator and the digit after it into its own run and the line renders as
 * "4 · جلال produits · il y a 3 h" — the count torn away from its noun.
 *
 * The isolate marks (`U+2068` FIRST STRONG ISOLATE / `U+2069` POP DIRECTIONAL ISOLATE) tell the
 * algorithm to resolve the name's direction from its own first strong character and then treat the
 * whole thing as a single neutral object in the surrounding run. The name still renders right to
 * left internally; it just stops moving anything else. Both marks are zero-width and invisible.
 *
 * Apply this to any name interpolated into a sentence or joined to other text. A name that stands
 * alone as an entire string does not need it — nothing is next to it to reorder — but wrapping it
 * anyway is harmless, and cheaper than deciding case by case at each call site.
 */
fun bidiIsolate(text: String): String = "\u2068$text\u2069"
