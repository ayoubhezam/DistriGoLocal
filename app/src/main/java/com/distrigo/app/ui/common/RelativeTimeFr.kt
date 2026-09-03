package com.distrigo.app.ui.common

import java.time.Duration
import java.time.Instant

/**
 * "il y a 12 min" — how long ago a draft was last touched.
 *
 * Coarse on purpose: the reader is deciding which of several Brouillons to pick up, so "hier" is
 * more useful than a timestamp they have to subtract in their head. [isoInstant] is what
 * `Instant.now().toString()` produced on the way in; an unparseable value yields null rather than
 * an exception, since a card should still render if a row is somehow malformed.
 */
fun formatRelativeFr(isoInstant: String, now: Instant = Instant.now()): String? {
    val then = runCatching { Instant.parse(isoInstant) }.getOrNull() ?: return null
    val seconds = Duration.between(then, now).seconds.coerceAtLeast(0)

    return when {
        seconds < 60      -> "à l'instant"
        seconds < 3_600   -> "il y a ${seconds / 60} min"
        seconds < 86_400  -> "il y a ${seconds / 3_600} h"
        seconds < 172_800 -> "hier"
        else              -> "il y a ${seconds / 86_400} jours"
    }
}
