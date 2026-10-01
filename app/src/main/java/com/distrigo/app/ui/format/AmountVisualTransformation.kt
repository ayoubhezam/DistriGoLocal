package com.distrigo.app.ui.format

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.distrigo.app.core.format.MoneyFormat
import com.distrigo.app.core.format.MoneyFormatter

/**
 * Shows a typed amount the business's way — "3500.5" as "3 500,5", "3,500.5" or "3.500,5" — while the
 * field keeps what Amount.sanitizeInput leaves: digits and one '.', so reading it back never has to
 * undo a format.
 *
 * Unlike the formatter it does not pad the centimes: "12." stays "12," while the user is typing.
 * Data class, so an unchanged format is an equal transformation and the field is not reset.
 */
data class AmountVisualTransformation(val format: MoneyFormat) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val shown = AmountInputDisplay.of(text.text, format)
        return TransformedText(AnnotatedString(shown.text), object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = shown.toShown[offset.coerceIn(0, text.length)]
            override fun transformedToOriginal(offset: Int): Int = shown.toRaw[offset.coerceIn(0, shown.text.length)]
        })
    }
}

/**
 * The text an amount field shows for what it holds, and where each cursor position lands either way.
 * Pure, so the mapping is tested without a device.
 */
internal object AmountInputDisplay {

    class Shown(val text: String, val toShown: IntArray, val toRaw: IntArray)

    /**
     * [raw] is digits and at most one '.'. The '.' becomes the format's decimal mark, one for one; a
     * thousands mark is inserted before every third digit of the integer part, counting from its end.
     * Only inserted marks have no character in [raw], which is what the mapping follows — the mark
     * itself may well be a ',' or '.' the user could have typed.
     */
    fun of(raw: String, format: MoneyFormat): Shown {
        val thousands = if (format.thousands == ' ') MoneyFormatter.NO_BREAK_SPACE else format.thousands
        val integerLength = raw.indexOf('.').let { if (it < 0) raw.length else it }
        val text = StringBuilder(raw.length + raw.length / 3)
        val toShown = IntArray(raw.length + 1)
        val inserted = BooleanArray(raw.length + raw.length / 3 + 1)
        for (i in raw.indices) {
            if (i in 1 until integerLength && (integerLength - i) % 3 == 0) {
                inserted[text.length] = true
                text.append(thousands)
            }
            toShown[i] = text.length
            text.append(if (raw[i] == '.') format.decimal else raw[i])
        }
        toShown[raw.length] = text.length

        val toRaw = IntArray(text.length + 1)
        var r = 0
        for (j in text.indices) {
            toRaw[j] = r
            if (!inserted[j]) r++
        }
        toRaw[text.length] = raw.length
        return Shown(text.toString(), toShown, toRaw)
    }
}
