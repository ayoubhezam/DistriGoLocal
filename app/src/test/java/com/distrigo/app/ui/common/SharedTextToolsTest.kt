package com.distrigo.app.ui.common

import com.distrigo.app.R
import com.distrigo.app.core.format.approximateLengthText
import com.distrigo.app.core.text.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 3's shared text tools (docs/i18n_rtl_plan.md): which sentence and which plural form they pick. */
class SharedTextToolsTest {

    @Test
    fun aQuantityTakesItsUnitsPluralForm() {
        assertEquals(UiText.Plural(R.plurals.common_qty_carton, 3, listOf("3")), quantityText(3.0, "carton"))
        assertEquals(UiText.Plural(R.plurals.common_qty_piece, 1, listOf("1")), quantityText(1.0, "pièce"))
        assertEquals(UiText.Plural(R.plurals.common_qty_kg, 100, listOf("1.25")), quantityText(1.25, "kg"))
        // A unit the app does not know is written as stored
        assertEquals(UiText.Raw("2 sacs"), quantityText(2.0, "sac"))
    }

    @Test
    fun aFractionReadsAsFrenchDoesToday() {
        // « 0,5 carton » but « 1,5 cartons », as ProductUnit.plural wrote them
        assertEquals(1, pluralCount(0.5))
        assertEquals(100, pluralCount(1.5))
        assertEquals(0, pluralCount(0.0))
        assertEquals(12, pluralCount(-12.0))
    }

    @Test
    fun aLengthIsRoundedAsBefore() {
        assertEquals(UiText.Plural(R.plurals.common_duration_days, 30), approximateLengthText(30))
        assertEquals(UiText.Plural(R.plurals.common_duration_months, 6), approximateLengthText(184))
        assertEquals(UiText.Plural(R.plurals.common_duration_years, 1), approximateLengthText(380))
        assertEquals(UiText.Plural(R.plurals.common_duration_months, 18), approximateLengthText(547))
    }

    @Test
    fun aNumericDateReadsDayFirstInArabic() {
        val day = java.time.LocalDate.of(2026, 10, 29)
        val arabic = java.util.Locale.forLanguageTag("ar-DZ-u-nu-latn")
        val rlm = Char(0x200F)
        // A right-to-left mark after each slash: the day on the right of an Arabic line
        assertEquals("29/${rlm}10/${rlm}2026", day.format(com.distrigo.app.core.format.AppDates.numeric("dd/MM/yyyy", arabic)))
        assertEquals("29/${rlm}10", day.format(com.distrigo.app.core.format.AppDates.numeric("dd/MM", arabic)))
        // French as before
        assertEquals("29/10/2026", day.format(com.distrigo.app.core.format.AppDates.numeric("dd/MM/yyyy", java.util.Locale.FRENCH)))
        assertEquals("2026-10-29", com.distrigo.app.core.format.AppDates.shownIsoDay("2026-10-29", java.util.Locale.FRENCH))
        assertEquals("29/${rlm}10/${rlm}2026", com.distrigo.app.core.format.AppDates.shownIsoDay("2026-10-29", arabic))
    }

    @Test
    fun everyBuiltInHasItsTranslation() {
        assertEquals(emptyList<String>(), BuiltInNames.untranslatedTypes())
        // 5 client motifs and 7 supplier ones, « Autre » in both
        assertEquals(11, BuiltInNames.motifCount())
    }

    @Test
    fun inFrenchABuiltInReadsAsStored() {
        val casse = com.distrigo.app.data.model.DefaultPerteType.CASSE
        assertEquals("Casse", BuiltInNames.type(casse.uuid, "Casse"))
        assertEquals("Casses diverses", BuiltInNames.type(casse.uuid, "Casses diverses"))
        assertEquals("Produit périmé", BuiltInNames.motif("Produit périmé"))
    }

    @Test
    fun aDraftsAgeIsCountedInItsUnit() {
        val now = java.time.Instant.parse("2026-10-28T12:00:00Z")
        assertEquals(UiText.Res(R.string.common_time_just_now), relativeTime("2026-10-28T11:59:30Z", now))
        assertEquals(UiText.Res(R.string.common_time_minutes_ago, 12), relativeTime("2026-10-28T11:48:00Z", now))
        assertEquals(UiText.Res(R.string.common_time_yesterday), relativeTime("2026-10-27T10:00:00Z", now))
        assertEquals(UiText.Plural(R.plurals.common_time_days_ago, 3), relativeTime("2026-10-25T12:00:00Z", now))
        assertEquals(null, relativeTime("not a date", now))
    }
}
