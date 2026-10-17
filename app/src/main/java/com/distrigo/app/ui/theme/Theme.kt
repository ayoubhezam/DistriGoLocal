package com.distrigo.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.distrigo.app.ui.designsystem.DsColors

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

/**
 * The brand's scheme, every role from DsColors (the owner's choice, 2026-10-17): no colour comes from the
 * wallpaper any more (Android 12+ dynamic colour), so every Material component — buttons, switches, checkboxes,
 * chips, menus, dialogs, date pickers, snackbars — looks the same on every phone, in the onboarding's and
 * Compte's colours.
 */
private val BrandColorScheme = lightColorScheme(
    primary                = DsColors.Primary,
    onPrimary              = DsColors.Surface,
    primaryContainer       = DsColors.PrimaryLight,
    onPrimaryContainer     = DsColors.PrimaryDark,
    inversePrimary         = DsColors.PrimaryLight,
    // Tonal buttons and selected chips: the blue's light tint, navy on it
    secondary              = DsColors.TextSecondary,
    onSecondary            = DsColors.Surface,
    secondaryContainer     = DsColors.PrimaryLight,
    onSecondaryContainer   = DsColors.PrimaryDark,
    tertiary               = DsColors.PrimaryDark,
    onTertiary             = DsColors.Surface,
    tertiaryContainer      = DsColors.PrimaryLight,
    onTertiaryContainer    = DsColors.PrimaryDark,
    background             = DsColors.Surface,
    onBackground           = DsColors.TextPrimary,
    // Default content colour: date picker day numbers, dropdown item labels
    surface                = DsColors.Surface,
    onSurface              = DsColors.TextPrimary,
    surfaceVariant         = DsColors.SurfaceMuted,
    // Untinted icons, dropdown item icons, bottom sheet drag handles
    onSurfaceVariant       = DsColors.TextSecondary,
    // No blue cast on raised surfaces: white stays white
    surfaceTint            = DsColors.Surface,
    // Snackbars: the near-black navy
    inverseSurface         = DsColors.TextPrimary,
    inverseOnSurface       = DsColors.Surface,
    error                  = DsColors.Danger,
    onError                = DsColors.Surface,
    errorContainer         = DsColors.DangerLight,
    onErrorContainer       = DsColors.Danger,
    // OutlinedButton borders and dividers: the cards' hairline
    outline                = DsColors.Border,
    outlineVariant         = DsColors.Border,
    // Menus (surfaceContainer), dialogs and date pickers (High), sheets (Low): white, as Compte's dialogs. Menus
    // keep their shadow, and dialogs and sheets their scrim, to stand off the page
    surfaceBright          = DsColors.Surface,
    surfaceDim             = DsColors.SurfaceSunken,
    surfaceContainerLowest = DsColors.Surface,
    surfaceContainerLow    = DsColors.Surface,
    surfaceContainer       = DsColors.Surface,
    surfaceContainerHigh   = DsColors.Surface,
    // A switch's track when off
    surfaceContainerHighest = DsColors.SurfaceSunken,
)

@Composable
fun DistriGoTheme(
    // Forced light, deliberately NOT isSystemInDarkTheme(). The app has no dark design: every
    // screen paints DsColors.Surface and dark text explicitly, so on a device with system dark
    // mode on only the Material component defaults flipped — dialogs, menus and date pickers
    // rendered dark against hand-painted white screens. The manifest theme is already
    // Theme.Material.Light with no values-night, so this aligns Compose with the platform side.
    // Switch back to isSystemInDarkTheme() once a real dark palette exists.
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else BrandColorScheme

    // The system still applies its own dark-mode appearance to the status/navigation bar icons.
    // With the app forced light those white icons land on a white background and vanish
    // (measured 1.0:1 contrast), so ask for dark system-bar icons to match the light content.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars     = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}