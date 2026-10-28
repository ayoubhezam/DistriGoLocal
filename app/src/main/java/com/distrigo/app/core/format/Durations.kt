package com.distrigo.app.core.format

import com.distrigo.app.R
import com.distrigo.app.core.text.UiText
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A subscription's length as a merchant says it: « 15 jours », « 3 mois », « 2 ans ». Exact, for a number of days
 * someone chose (an activation code's): the presets read as their names, and any other count as days.
 */
fun subscriptionLength(days: Int): String = when {
    days in 365..366 -> "1 an"
    days > 0 && days % 365 == 0 -> "${days / 365} ans"
    // The « 1 mois » and « 6 mois » presets, as the codes count them
    days == 31 -> "1 mois"
    days in 182..183 -> "6 mois"
    days > 0 && days % 30 == 0 -> "${days / 30} mois"
    else -> dayCount(days)
}

/**
 * The same for a period read between two dates, which a renewal or the months' lengths make uneven: rounded to
 * the year when near one, else to the month, and in days under two months (« 30 jours » for a trial).
 */
fun approximateLength(days: Int): String {
    val years = (days / 365.25).roundToInt()
    return when {
        days < 60 -> dayCount(days)
        years >= 1 && abs(days - years * 365.25) <= 35 -> if (years == 1) "1 an" else "$years ans"
        else -> "${(days / 30.44).roundToInt()} mois"
    }
}

private fun dayCount(days: Int) = if (days == 1) "1 jour" else "$days jours"

/**
 * [approximateLength] in the app's language, for the screens: « 6 mois », « 6 أشهر ». The French functions above stay
 * for Administration (D10) and until the onboarding's text moves (docs/i18n_rtl_plan.md, Phase 4).
 */
fun approximateLengthText(days: Int): UiText {
    val years = (days / 365.25).roundToInt()
    return when {
        days < 60 -> UiText.Plural(R.plurals.common_duration_days, days)
        years >= 1 && abs(days - years * 365.25) <= 35 -> UiText.Plural(R.plurals.common_duration_years, years)
        else -> (days / 30.44).roundToInt().let { UiText.Plural(R.plurals.common_duration_months, it) }
    }
}
