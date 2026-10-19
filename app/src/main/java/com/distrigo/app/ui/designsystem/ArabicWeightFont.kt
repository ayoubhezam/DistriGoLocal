package com.distrigo.app.ui.designsystem

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.distrigo.app.R

/**
 * Plus Jakarta Sans at [weight], with Arabic drawn heavier than the Latin beside it.
 *
 * Jakarta has no Arabic letters, so Android takes them from the system's Arabic font, at the
 * typeface's weight. At the same weight Arabic reads lighter than Jakarta, so product and client
 * names in Arabic looked faded next to French ones (the owner, 2026-10-19). Here the typeface
 * carries [arabicWeight]: the system picks its Arabic font at that weight, while Jakarta keeps
 * drawing [weight] through its variation axis. Jakarta is declared at the same weight as the
 * typeface so Android never fakes a bold on it.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class ArabicWeightFont(override val weight: FontWeight) : AndroidFont(
    loadingStrategy   = FontLoadingStrategy.Blocking,
    typefaceLoader    = Loader,
    variationSettings = FontVariation.Settings()
) {
    override val style: FontStyle = FontStyle.Normal

    /** Two steps heavier, up to ExtraBold: Normal text gets SemiBold Arabic, SemiBold gets Bold. */
    val arabicWeight: Int get() = (weight.weight + 200).coerceAtMost(800)

    private object Loader : TypefaceLoader {
        override fun loadBlocking(context: Context, font: AndroidFont): Typeface? =
            (font as ArabicWeightFont).build(context)

        override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface? =
            loadBlocking(context, font)
    }

    private fun build(context: Context): Typeface {
        val jakarta = android.graphics.fonts.Font.Builder(context.resources, R.font.plus_jakarta_sans)
            .setFontVariationSettings("'wght' ${weight.weight}")
            .setWeight(arabicWeight)
            .build()
        return Typeface.CustomFallbackBuilder(android.graphics.fonts.FontFamily.Builder(jakarta).build())
            .setSystemFallback("sans-serif")
            .setStyle(android.graphics.fonts.FontStyle(arabicWeight, android.graphics.fonts.FontStyle.FONT_SLANT_UPRIGHT))
            .build()
    }

    override fun equals(other: Any?) = other is ArabicWeightFont && other.weight == weight
    override fun hashCode() = weight.hashCode()
    override fun toString() = "ArabicWeightFont(weight=$weight)"
}
