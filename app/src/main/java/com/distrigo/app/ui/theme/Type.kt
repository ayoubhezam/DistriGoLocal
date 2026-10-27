package com.distrigo.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.distrigo.app.ui.designsystem.DsBrand

/**
 * The app's type: Plus Jakarta Sans, the onboarding's and Compte's font, on every Material role (the owner's choice,
 * 2026-10-16), so every Text and every Material component — buttons, fields, menus, dialogs — takes it without a
 * screen naming it. Sizes, weights and line heights stay Material's, as before: only the face changes.
 *
 * Tabular figures throughout: every digit as wide as the others, so amounts and quantities line up in lists, tables
 * and reports.
 *
 * Line height in proportion to the size (1.4×) and tighter letter spacing (the owner's choice, 2026-10-17):
 * Material's fixed line heights and tracking were drawn for Roboto at its role's size, so the screens' smaller
 * Jakarta text (13–14 sp, given at the call site) wrapped onto loose, airy second lines. Headings keep a tighter
 * 1.25× and their own (zero or negative) tracking.
 *
 * Text with no letter of its own — an amount's figures, a time, a percentage, a phone number — reads left to right on
 * a right-to-left screen too (ContentOrLtr, the owner's rule of 2026-10-27): `75 %` stays `75 %` rather than
 * `% 75`, `05 55 12 34 56` keeps its pairs in order, `-3` its sign before the digit. A line's first letter still
 * decides the rest: Arabic reads right to left, French left to right. On a left-to-right screen this is what Compose
 * already does, so nothing there changes.
 */
private fun TextStyle.brand(lineHeight: TextUnit = 1.4.em, letterSpacing: TextUnit = this.letterSpacing) =
    copy(
        fontFamily = DsBrand.Font, fontFeatureSettings = "tnum", lineHeight = lineHeight, letterSpacing = letterSpacing,
        textDirection = TextDirection.ContentOrLtr,
    )

private val BodyTracking = 0.15.sp
private val LabelTracking = 0.1.sp

val Typography = Typography().run {
    copy(
        displayLarge   = displayLarge.brand(lineHeight = 1.25.em),
        displayMedium  = displayMedium.brand(lineHeight = 1.25.em),
        displaySmall   = displaySmall.brand(lineHeight = 1.25.em),
        headlineLarge  = headlineLarge.brand(lineHeight = 1.25.em),
        headlineMedium = headlineMedium.brand(lineHeight = 1.25.em),
        headlineSmall  = headlineSmall.brand(lineHeight = 1.25.em),
        titleLarge     = titleLarge.brand(),
        titleMedium    = titleMedium.brand(),
        titleSmall     = titleSmall.brand(),
        bodyLarge      = bodyLarge.brand(letterSpacing = BodyTracking),
        bodyMedium     = bodyMedium.brand(letterSpacing = BodyTracking),
        bodySmall      = bodySmall.brand(letterSpacing = BodyTracking),
        labelLarge     = labelLarge.brand(letterSpacing = LabelTracking),
        labelMedium    = labelMedium.brand(letterSpacing = LabelTracking),
        labelSmall     = labelSmall.brand(letterSpacing = LabelTracking),
    )
}
