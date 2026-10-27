package com.distrigo.app.ui.common

import androidx.compose.ui.text.input.KeyboardType
import com.distrigo.app.core.format.westernNumber

/**
 * What a field typed on [keyboard] keeps of [typed]: a number or a phone number keeps 0–9, an Arabic keyboard's digits
 * and decimal mark included (westernNumber), so it shows 0–9 and parses; any other field keeps what was typed, a name's
 * or a note's digits as they were written.
 */
fun numberFieldInput(typed: String, keyboard: KeyboardType): String =
    if (keyboard in NUMBER_KEYBOARDS) westernNumber(typed) else typed

private val NUMBER_KEYBOARDS = setOf(KeyboardType.Number, KeyboardType.Decimal, KeyboardType.Phone, KeyboardType.NumberPassword)
