package com.distrigo.app.ui.designsystem

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Standard [androidx.compose.material3.OutlinedTextField] colors for the app.
 *
 * Every colour Material 3 leaves unset falls back to the active `colorScheme`, the brand's in
 * `DistriGoTheme` (no wallpaper colours since 2026-10-17). Naming them here all the same keeps a
 * field's look in one place: input text, labels, icons and the cursor included.
 *
 * Defaults cover the common case; pass named arguments to vary a single colour per call site.
 */
@Composable
fun dsTextFieldColors(
    focusedBorderColor        : Color = DsColors.Primary,
    unfocusedBorderColor      : Color = DsColors.Border,
    errorBorderColor          : Color = DsColors.Danger,
    disabledBorderColor       : Color = DsColors.Border,
    disabledTextColor         : Color = DsColors.TextPrimary,
    disabledPlaceholderColor  : Color = DsColors.TextSecondary,
    disabledLabelColor        : Color = DsColors.TextSecondary,
    disabledLeadingIconColor  : Color = DsColors.TextSecondary,
    disabledTrailingIconColor : Color = DsColors.TextSecondary
): TextFieldColors = OutlinedTextFieldDefaults.colors(
    // White inside: on the Mist pages (2026-10-19) a transparent field would show the Mist through
    focusedContainerColor      = DsColors.Surface,
    unfocusedContainerColor    = DsColors.Surface,
    errorContainerColor        = DsColors.Surface,
    disabledContainerColor     = DsColors.SurfaceMuted,

    // Input text
    focusedTextColor           = DsColors.TextPrimary,
    unfocusedTextColor         = DsColors.TextPrimary,
    errorTextColor             = DsColors.TextPrimary,
    disabledTextColor          = disabledTextColor,

    // Caret
    cursorColor                = DsColors.Primary,
    errorCursorColor           = DsColors.Danger,

    // Outline
    focusedBorderColor         = focusedBorderColor,
    unfocusedBorderColor       = unfocusedBorderColor,
    errorBorderColor           = errorBorderColor,
    disabledBorderColor        = disabledBorderColor,

    // Label — keeps Material's focus accent, in the app's own primary
    focusedLabelColor          = DsColors.Primary,
    unfocusedLabelColor        = DsColors.TextSecondary,
    errorLabelColor            = DsColors.Danger,
    disabledLabelColor         = disabledLabelColor,

    // Placeholder
    focusedPlaceholderColor    = DsColors.TextSecondary,
    unfocusedPlaceholderColor  = DsColors.TextSecondary,
    errorPlaceholderColor      = DsColors.TextSecondary,
    disabledPlaceholderColor   = disabledPlaceholderColor,

    // Leading / trailing icons — only applies where the Icon has no explicit tint
    focusedLeadingIconColor    = DsColors.TextSecondary,
    unfocusedLeadingIconColor  = DsColors.TextSecondary,
    errorLeadingIconColor      = DsColors.TextSecondary,
    disabledLeadingIconColor   = disabledLeadingIconColor,
    focusedTrailingIconColor   = DsColors.TextSecondary,
    unfocusedTrailingIconColor = DsColors.TextSecondary,
    errorTrailingIconColor     = DsColors.Danger,
    disabledTrailingIconColor  = disabledTrailingIconColor
)
