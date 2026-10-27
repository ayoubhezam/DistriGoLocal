package com.distrigo.app.core.format

import android.content.res.Configuration
import android.text.TextUtils
import android.view.View
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.distrigo.app.ui.onboarding.BrandTextField
import com.distrigo.app.ui.products.FormField
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DecimalStyle
import java.util.Locale

/**
 * Digits 0–9 under a phone in Arabic, with Android's own number formatting (ICU) rather than the desktop JDK's the unit
 * tests run on, and an Arabic keyboard's digits typed into real fields.
 */
@RunWith(AndroidJUnit4::class)
class WesternDigitsDeviceTest {

    @get:Rule
    val compose = createComposeRule()

    /** Arabic (Egypt), whose digits are Arabic-Indic by default. */
    private val egypt = Locale.forLanguageTag("ar-EG")
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
    fun aLocaleAskedForLatinDigitsWritesThemAndKeepsItsDirection() {
        // What Android writes under such a phone on its own: the reason for the rule
        assertEquals("٥", String.format(egypt, "%d", 5))
        val latin = WesternDigits.latin(egypt)
        assertEquals("05", String.format(latin, "%02d", 5))
        assertEquals("27", NumberFormat.getIntegerInstance(latin).format(27))
        // What the date picker's header is written with
        assertEquals('0', DecimalStyle.of(latin).zeroDigit)
        // Still Arabic, still right to left
        assertEquals("ar", latin.language)
        assertEquals(View.LAYOUT_DIRECTION_RTL, TextUtils.getLayoutDirectionFromLocale(latin))
    }

    @Test
    fun theDefaultLocaleWritesLatinDigits() {
        Locale.setDefault(egypt)
        WesternDigits.applyToDefaultLocale()
        assertEquals("09:05", "%02d:%02d".format(9, 5))
        // What the date picker's days and years are written with
        assertEquals("2026", NumberFormat.getIntegerInstance().apply { isGroupingUsed = false }.format(2026))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun theDatePickerWritesLatinDigits() {
        Locale.setDefault(egypt)
        WesternDigits.applyToDefaultLocale()
        val day = LocalDate.of(2026, 10, 27).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        compose.setContent {
            // The activity's configuration, as MainActivity overrides it
            val latin = Configuration(LocalConfiguration.current).apply { setLocale(WesternDigits.latin(egypt)) }
            CompositionLocalProvider(LocalConfiguration provides latin) {
                DatePicker(rememberDatePickerState(initialSelectedDateMillis = day))
            }
        }
        compose.onAllNodesWithText("٢٧", substring = true).assertCountEquals(0)
        assertTrue(compose.onAllNodesWithText("27", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("2026", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun aPriceTypedOnAnArabicKeyboardIsKeptInLatinDigits() {
        var price by mutableStateOf("")
        compose.setContent { FormField(label = "Prix de vente", value = price, onValueChange = { price = it }, isDecimal = true) }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("١٢٥٠٫٥")
        compose.runOnIdle { assertEquals("1250.5", price) }
        assertTrue(compose.onAllNodesWithText("1250.5").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun aPhoneTypedOnAnArabicKeyboardIsKeptInLatinDigits() {
        var phone by mutableStateOf("")
        compose.setContent {
            BrandTextField(
                value = phone, onValueChange = { phone = it }, label = "Téléphone", placeholder = "",
                icon = Icons.Outlined.Phone, keyboardType = KeyboardType.Phone,
            )
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("٠٥٥٥ ١٢ ٣٤ ٥٦")
        compose.runOnIdle { assertEquals("0555 12 34 56", phone) }
    }

    @Test
    fun aNameKeepsTheDigitsItWasWrittenWith() {
        var name by mutableStateOf("")
        compose.setContent { FormField(label = "Nom", value = name, onValueChange = { name = it }) }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("زيت ٥ لتر")
        compose.runOnIdle { assertEquals("زيت ٥ لتر", name) }
    }
}
