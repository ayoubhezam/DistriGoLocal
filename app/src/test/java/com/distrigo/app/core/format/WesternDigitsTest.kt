package com.distrigo.app.core.format

import com.distrigo.app.data.model.Quantity
import com.distrigo.app.ui.mouvements.formatMovementDateTime
import com.distrigo.app.ui.products.isExpiringSoon
import com.distrigo.app.ui.rapports.percent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Digits 0–9 whatever the phone's language, and an Arabic keyboard's ٠–٩ read as 0–9 (the owner's rule, 2026-10-27). */
class WesternDigitsTest {

    /** A phone in Arabic (Egypt), whose locale writes Arabic-Indic digits: the case the rule is for. */
    private val arabicIndic = Locale.forLanguageTag("ar-EG-u-nu-arab")
    private lateinit var saved: Locale

    @Before
    fun saveDefault() {
        saved = Locale.getDefault()
    }

    @After
    fun restoreDefault() {
        Locale.setDefault(saved)
    }

    @Test
    fun arabicIndicDigitsBecomeLatin() {
        assertEquals("0123456789", westernDigits("٠١٢٣٤٥٦٧٨٩"))
        // The extended set, from a Persian or Urdu keyboard
        assertEquals("0123456789", westernDigits("۰۱۲۳۴۵۶۷۸۹"))
        assertEquals("0555 12 34 56", westernDigits("٠٥٥٥ ١٢ ٣٤ ٥٦"))
        assertEquals("+213 5", westernDigits("+٢١٣ ٥"))
    }

    @Test
    fun nothingButTheDigitsChanges() {
        assertEquals("زيت 5 لتر", westernDigits("زيت ٥ لتر"))
        assertEquals("Prix : 1 250,00 DA", westernDigits("Prix : 1 250,00 DA"))
        val ascii = "V-6DED-000027"
        assertSame(ascii, westernDigits(ascii))
    }

    @Test
    fun aNumberFieldReadsTheArabicSeparators() {
        assertEquals("1250.5", westernNumber("١٢٥٠٫٥"))
        assertEquals("12500", westernNumber("١٢٬٥٠٠"))
        assertEquals("3,5", westernNumber("٣,٥"))
    }

    @Test
    fun latinDigitsKeepTheLanguageAndRegion() {
        val latin = WesternDigits.latin(Locale.forLanguageTag("ar-EG"))
        assertEquals("ar", latin.language)
        assertEquals("EG", latin.country)
        assertEquals("latn", latin.getUnicodeLocaleType("nu"))
        assertSame(latin, WesternDigits.latin(latin))
        assertEquals("latn", WesternDigits.latin(arabicIndic).getUnicodeLocaleType("nu"))
    }

    @Test
    fun theDefaultLocaleIsAskedForLatinDigits() {
        Locale.setDefault(arabicIndic)
        // What the platform writes on its own under such a phone, so the next line means something
        assertEquals("٠٥", "%02d".format(5))
        WesternDigits.applyToDefaultLocale()
        assertEquals("05", "%02d".format(5))
        assertEquals("ar", Locale.getDefault().language)
        assertEquals("EG", Locale.getDefault().country)
        assertEquals("1234", java.text.NumberFormat.getIntegerInstance().apply { isGroupingUsed = false }.format(1234))
    }

    /** What the app writes itself, with the phone in Arabic-Indic digits and nothing applied. */
    @Test
    fun theAppsFormattersWriteLatinDigitsUnderAnArabicIndicPhone() {
        Locale.setDefault(arabicIndic)
        MoneyFormat.entries.forEach { format ->
            assertAscii(MoneyFormatter.of(format).da(-1236790.5))
        }
        assertEquals("1.25", Quantity.format(1.25))
        assertAscii(percent(0.125))
        assertEquals("27/10/2026", LocalDate.of(2026, 10, 27).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
        assertEquals("13:45", java.time.LocalTime.of(13, 45).format(DateTimeFormatter.ofPattern("HH:mm")))
        val moment = LocalDate.of(2026, 10, 27).atTime(9, 5).atZone(ZoneId.systemDefault()).toInstant()
        assertEquals("2026-10-27 à 09:05", formatMovementDateTime(moment.toString()))
    }

    @Test
    fun anExpiryDateWrittenInArabicIndicDigitsStillReads() {
        Locale.setDefault(arabicIndic)
        val soon = westernToArabicIndic(LocalDate.now().plusDays(10).toString())
        assertTrue(isExpiringSoon(soon))
        assertTrue(isExpiringSoon(LocalDate.now().plusDays(10).toString()))
    }

    @Test
    fun aPercentageIsWrittenAsTheLanguageWritesIt() {
        assertEquals("75%", percentText("75", Locale.forLanguageTag("ar-DZ")))
        assertEquals("75 %", percentText("75", Locale.FRENCH))
        Locale.setDefault(WesternDigits.latin(arabicIndic))
        assertEquals("12,5%", percent(0.125))
        Locale.setDefault(Locale.FRANCE)
        assertEquals("12,5 %", percent(0.125))
    }

    private fun assertAscii(text: String) =
        assertTrue("« $text » has a digit that is not 0–9", text.none { it.isDigit() && it !in '0'..'9' })

    private fun westernToArabicIndic(text: String) = text.map { if (it in '0'..'9') '٠' + (it - '0') else it }.joinToString("")
}
