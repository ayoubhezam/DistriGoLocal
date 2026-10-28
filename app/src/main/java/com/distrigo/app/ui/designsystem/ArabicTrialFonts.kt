package com.distrigo.app.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.LocalTextStyle
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
 * The Arabic type the owner is trying on the Dashboard (2026-10-28), before deciding for the rest of the app:
 * - [display] for the main headers (larger, Black); section titles take [ui] at Bold. Asked for: beIN Black, which has no open license to ship in an app;
 *   until a licensed file is supplied, Cairo at its Black weight stands in.
 * - [ui] for buttons, tiles, chips and menus: Cairo (SIL OFL 1.1, docs/licenses/Cairo-OFL.txt).
 * - [body] for the rest: IBM Plex Sans Arabic (SIL OFL 1.1, docs/licenses/IBMPlexSansArabic-OFL.txt).
 * Both fonts carry Latin letters and digits too, so a French name or an amount on the card draws in the same face.
 * Only [ProvideArabicTrialFonts] gives them, and only in Arabic: every other screen, and French, keep Jakarta.
 */
class ArabicTrialFonts(val display: FontFamily, val ui: FontFamily, val body: FontFamily) {
    /** A main header's size: a step above the 20 sp headline, so it reads first over a card's 17 sp section title. */
    val mainHeaderSize = 23.sp
}

/** Null except inside [ProvideArabicTrialFonts]: a component reads it to pick its role's family, or keeps its own. */
val LocalArabicTrialFonts = staticCompositionLocalOf<ArabicTrialFonts?> { null }

// Cairo is one variable file; each weight is asked of it through its axis, so none is faked
@OptIn(ExperimentalTextApi::class)
private fun cairo(weight: FontWeight, axis: Int = weight.weight) =
    Font(R.font.cairo, weight, variationSettings = FontVariation.Settings(FontVariation.weight(axis)))

private val TrialFonts by lazy {
    ArabicTrialFonts(
        // Every weight a title asks for draws Black, as beIN Black has one weight
        display = FontFamily(
            cairo(FontWeight.SemiBold, 900), cairo(FontWeight.Bold, 900),
            cairo(FontWeight.ExtraBold, 900), cairo(FontWeight.Black, 900),
        ),
        ui = FontFamily(
            cairo(FontWeight.Normal), cairo(FontWeight.Medium), cairo(FontWeight.SemiBold),
            cairo(FontWeight.Bold), cairo(FontWeight.ExtraBold),
        ),
        body = FontFamily(
            Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
            Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
            Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
            Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
        ),
    )
}

/** [content] drawn in the trial's display face, for a title that does not take a font family (an animated amount). */
@Composable
fun ArabicTrialDisplay(content: @Composable () -> Unit) {
    val display = LocalArabicTrialFonts.current?.display ?: return content()
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = display), content = content)
}

/**
 * [content] with the trial fonts when the app is in Arabic: its text defaults to [ArabicTrialFonts.body] at Medium (the
 * descriptions), and the components that know their role read [LocalArabicTrialFonts]: main headers [display]
 * (Cairo Black), section titles, tiles and chips [ui] (Cairo, Bold for a title). In French, [content] as is.
 */
@Composable
fun ProvideArabicTrialFonts(content: @Composable () -> Unit) {
    if (AppLanguage.of(Locale.getDefault()) != AppLanguage.ARABIC) return content()
    val fonts = TrialFonts
    CompositionLocalProvider(
        LocalArabicTrialFonts provides fonts,
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = fonts.body, fontWeight = FontWeight.Medium),
        content = content,
    )
}
