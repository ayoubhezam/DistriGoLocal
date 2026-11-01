package com.distrigo.app.ui.common

import com.distrigo.app.R
import com.distrigo.app.core.text.UiText
import java.time.Duration
import java.time.Instant

/**
 * How long ago, in the app's language: "il y a 12 min", "منذ 12 دقيقة" — the draft cards, the backup lines. Coarse on
 * purpose: "hier" says more than a timestamp to subtract; an unparseable [isoInstant] gives null rather than an
 * exception, so a card still renders.
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
