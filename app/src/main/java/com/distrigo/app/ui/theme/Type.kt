package com.distrigo.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import com.distrigo.app.ui.designsystem.DsBrand

/**
 * The app's type: Plus Jakarta Sans, the onboarding's and Compte's font, on every Material role (the owner's choice,
 * 2026-10-16), so every Text and every Material component — buttons, fields, menus, dialogs — takes it without a
 * screen naming it. Sizes, weights and line heights stay Material's, as before: only the face changes.
 *
 * Tabular figures throughout: every digit as wide as the others, so amounts and quantities line up in lists, tables
 * and reports.
 */
private fun TextStyle.brand() = copy(fontFamily = DsBrand.Font, fontFeatureSettings = "tnum")

val Typography = Typography().run {
    copy(
        displayLarge   = displayLarge.brand(),
        displayMedium  = displayMedium.brand(),
        displaySmall   = displaySmall.brand(),
        headlineLarge  = headlineLarge.brand(),
        headlineMedium = headlineMedium.brand(),
        headlineSmall  = headlineSmall.brand(),
        titleLarge     = titleLarge.brand(),
        titleMedium    = titleMedium.brand(),
        titleSmall     = titleSmall.brand(),
        bodyLarge      = bodyLarge.brand(),
        bodyMedium     = bodyMedium.brand(),
        bodySmall      = bodySmall.brand(),
        labelLarge     = labelLarge.brand(),
        labelMedium    = labelMedium.brand(),
        labelSmall     = labelSmall.brand(),
    )
}
