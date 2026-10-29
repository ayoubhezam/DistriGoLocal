package com.distrigo.app.ui.designsystem

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.distrigo.app.R
import com.distrigo.app.core.locale.AppLanguage
import java.util.Locale

/**
 * The app's Arabic type (the owner, 2026-10-29, after trying it on the Dashboard). Three roles:
 * - [display], main headers: Cairo Black. Asked for: beIN Black, which has no open license to ship in an app; Cairo at
 *   its Black weight stands in until a licensed file is supplied.
 * - [ui], section and card titles, buttons, tabs, chips, navigation: Cairo Bold (SIL OFL 1.1, docs/licenses/Cairo-OFL.txt).
 * - [text], body and descriptions: IBM Plex Sans Arabic Medium (SIL OFL 1.1, docs/licenses/IBMPlexSansArabic-OFL.txt).
 *
 * [text] is the family every Arabic screen draws in ([RtlTypography][com.distrigo.app.ui.theme.RtlTypography] and
 * [DsBrand.Font]), and it carries the hierarchy by weight: a regular or medium text draws IBM Plex Sans Arabic Medium,
 * a semi-bold or heavier one Cairo (Bold, ExtraBold, Black), so every bold title, button and chip already written takes
 * Cairo without naming it. Both fonts carry Latin letters and digits, so a French name or an amount matches the Arabic
 * beside it. French keeps Plus Jakarta Sans ([DsBrand.Jakarta]).
 */
object ArabicFonts {

    // Cairo is one variable file; each weight is asked of it through its axis, so none is faked
    @OptIn(ExperimentalTextApi::class)
    private fun cairo(weight: FontWeight, axis: Int = weight.weight) =
        Font(R.font.cairo, weight, variationSettings = FontVariation.Settings(FontVariation.weight(axis)))

    private fun plexMedium(weight: FontWeight) = Font(R.font.ibm_plex_sans_arabic_medium, weight)

    val text: FontFamily by lazy {
        FontFamily(
            plexMedium(FontWeight.Light), plexMedium(FontWeight.Normal), plexMedium(FontWeight.Medium),
            // A semi-bold label (a button, a chip) is a title for the hierarchy: Cairo Bold
            cairo(FontWeight.SemiBold, 700), cairo(FontWeight.Bold), cairo(FontWeight.ExtraBold), cairo(FontWeight.Black),
        )
    }

    // Every weight a main header asks for draws Black, as beIN Black has one weight
    val display: FontFamily by lazy {
        FontFamily(cairo(FontWeight.SemiBold, 900), cairo(FontWeight.Bold, 900), cairo(FontWeight.ExtraBold, 900), cairo(FontWeight.Black, 900))
    }

    val ui: FontFamily by lazy {
        FontFamily(cairo(FontWeight.Normal), cairo(FontWeight.Medium), cairo(FontWeight.SemiBold, 700), cairo(FontWeight.Bold), cairo(FontWeight.ExtraBold))
    }

    /** A main header's size: a step above the 20 sp headline, so it reads first over a card's 17 sp section title. */
    val mainHeaderSize = 23.sp

    /** The app speaks Arabic: the default locale follows the app's language (MainActivity). */
    fun active(): Boolean = AppLanguage.of(Locale.getDefault()) == AppLanguage.ARABIC
}

/** [ArabicFonts] while the app is in Arabic, null in French: a component reads it to pick its role's family, or keeps its own. */
val LocalArabicFonts = staticCompositionLocalOf<ArabicFonts?> { null }

/** [content] drawn in the display face, for a main header that does not take a font family (an animated amount). */
@Composable
fun ArabicDisplay(content: @Composable () -> Unit) {
    if (LocalArabicFonts.current == null) return content()
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = ArabicFonts.display), content = content)
}

/** The whole app's content, given [LocalArabicFonts] in Arabic (DistriGoTheme). In French, [content] as is. */
@Composable
fun ProvideArabicFonts(content: @Composable () -> Unit) {
    if (!ArabicFonts.active()) return content()
    CompositionLocalProvider(LocalArabicFonts provides ArabicFonts, content = content)
}
