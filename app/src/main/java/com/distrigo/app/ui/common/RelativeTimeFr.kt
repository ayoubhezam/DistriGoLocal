package com.distrigo.app.ui.common

import com.distrigo.app.R
import com.distrigo.app.core.text.UiText
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

/**
 * [formatRelativeFr] in the app's language: "il y a 12 min", "منذ 12 دقيقة". The draft cards use it; Paramètres'
 * backup lines keep [formatRelativeFr] until that section's text moves (docs/i18n_rtl_plan.md, Phase 4).
 */
fun relativeTime(isoInstant: String, now: Instant = Instant.now()): UiText? {
    val then = runCatching { Instant.parse(isoInstant) }.getOrNull() ?: return null
    val seconds = Duration.between(then, now).seconds.coerceAtLeast(0)
    return when {
        seconds < 60      -> UiText.Res(R.string.common_time_just_now)
        seconds < 3_600   -> UiText.Res(R.string.common_time_minutes_ago, (seconds / 60).toInt())
        seconds < 86_400  -> UiText.Res(R.string.common_time_hours_ago, (seconds / 3_600).toInt())
        seconds < 172_800 -> UiText.Res(R.string.common_time_yesterday)
        else              -> (seconds / 86_400).toInt().let { UiText.Plural(R.plurals.common_time_days_ago, it) }
    }
}
