package com.distrigo.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.distrigo.app.ui.designsystem.DsColors

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
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
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val rawColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Both the static LightColorScheme and Android 12+ dynamic color (wallpaper-derived) can hand
    // back tinted roles. Any role left unpinned leaks the wallpaper palette into every Material
    // component that falls back to it — TextButton labels, OutlinedButton borders, icon buttons,
    // dropdown menus and the date pickers all did. Pin the roles the app actually relies on to
    // design system tokens so components look the same on every device.
    // Dark theme keeps its own scheme untouched; the app is light-only for now.
    val colorScheme = if (darkTheme) rawColorScheme else rawColorScheme.copy(
        background           = DsColors.Surface,
        surface              = DsColors.Surface,
        // Default content colour: date picker day numbers, dropdown item labels
        onSurface            = DsColors.TextPrimary,
        // TextButton / OutlinedButton labels, selection handles, date picker accents
        primary              = DsColors.Primary,
        // Untinted icons, dropdown item icons, bottom sheet drag handles
        onSurfaceVariant     = DsColors.TextSecondary,
        // Raised containers. Deliberately not Surface: these sit on top of an already-white page
        // and would lose all separation but the shadow. Material maps them separately —
        // menus read surfaceContainer, dialogs and date pickers read surfaceContainerHigh.
        surfaceContainer     = DsColors.SurfaceSunken,
        surfaceContainerHigh = DsColors.SurfaceSunken,
        // Bottom sheets and elevated cards — white, matching the sites that set it explicitly
        surfaceContainerLow  = DsColors.Surface,
        // OutlinedButton borders — matches the border of every other outlined element in the app
        outline              = DsColors.Border
    )

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