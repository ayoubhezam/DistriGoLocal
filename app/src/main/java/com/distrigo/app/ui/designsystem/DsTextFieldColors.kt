package com.distrigo.app.ui.designsystem

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Standard [androidx.compose.material3.OutlinedTextField] colors for the app.
 *
 * Every colour Material 3 leaves unset falls back to the active `colorScheme`, which is
 * wallpaper-derived on Android 12+ (`dynamicColor = true` in `DistriGoTheme`). Only
 * `background`/`surface` are pinned there, so anything resolved from `onSurface`,
 * `onSurfaceVariant` or `primary` drifts per device — input text, labels, icons and the
 * cursor included. Pinning them here keeps text fields identical on every device.
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
