package com.distrigo.app.core.text

import com.distrigo.app.R
import com.distrigo.app.core.format.AppDates
import com.distrigo.app.core.locale.AppLanguage
import com.distrigo.app.ui.common.ltrIsolate
import com.distrigo.app.ui.common.userText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** The pieces Phase 1 lays down for the screens' text (docs/i18n_rtl_plan.md): what they promise, without Android. */
class Phase1ToolsTest {

    @Test
    fun aTextIsComparedByWhatItWillShow() {
        assertEquals(UiText.Res(R.string.common_error_unknown), UiText.Res(R.string.common_error_unknown))
        assertEquals(UiText.Res(R.string.settings_language_title, "a", 2), UiText.Res(R.string.settings_language_title, listOf("a", 2)))
        assertEquals(UiText.Raw("SARL Benali"), UiText.Raw("SARL Benali"))
    }

    @Test
    fun aRefusalSaysTheRepositorysWords() {
        val words = UiText.Res(R.string.common_error_unknown)
        assertSame(words, userText(UserFacingException(words)))
        assertEquals(UiText.Raw("Stock insuffisant"), userText(IllegalStateException("Stock insuffisant")))
        assertEquals(UiText.Res(R.string.common_error_unknown), userText(IllegalStateException()))
    }

    @Test
    fun languagesAreFoundByTheirLanguageNotTheirRegion() {
        assertEquals(AppLanguage.ARABIC, AppLanguage.of(Locale.forLanguageTag("ar-EG")))
        assertEquals(AppLanguage.ARABIC, AppLanguage.of(Locale.forLanguageTag("ar-DZ-u-nu-latn")))
        assertEquals(AppLanguage.FRENCH, AppLanguage.of(Locale.FRANCE))
        // Anything else, or nothing chosen, is French (D1)
        assertEquals(AppLanguage.FRENCH, AppLanguage.of(Locale.US))
        assertEquals(AppLanguage.FRENCH, AppLanguage.of(null))
    }

    @Test
    fun aDateFollowsTheLanguageWithDigitsInLatin() {
        val day = LocalDate.of(2026, 10, 27)
        assertEquals("mardi 27 octobre", day.format(AppDates.formatter("EEEE d MMMM", Locale.FRENCH)))
        val arabic = day.format(AppDates.formatter("d MMMM yyyy", AppLanguage.ARABIC.locale))
        assertEquals("27", arabic.substringBefore(' '))
        assertEquals("2026", arabic.substringAfterLast(' '))
        assertSame(AppDates.formatter("dd/MM", Locale.FRENCH), AppDates.formatter("dd/MM", Locale.FRENCH))
    }

    @Test
    fun aNumberIsFencedLeftToRight() {
        assertEquals("${Char(0x2066)}-1 250,00 DA${Char(0x2069)}", ltrIsolate("-1 250,00 DA"))
    }
}
