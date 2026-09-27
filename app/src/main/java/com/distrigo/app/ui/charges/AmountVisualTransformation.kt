package com.distrigo.app.ui.charges

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.distrigo.app.data.model.Amount

/**
 * Shows a typed amount with its thousands apart — "3500.5" as "3 500.5" — while the field keeps the
 * digits as typed, so reading it back never has to strip the spaces.
 */
internal object AmountVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val shown = Amount.groupThousands(raw)

        // Where each typed character lands in what is shown, and back: only spaces are added.
        val toShown = IntArray(raw.length + 1)
        var s = 0
        for (i in raw.indices) {
            while (s < shown.length && shown[s] == ' ' && raw[i] != ' ') s++
            toShown[i] = s
            s++
        }
        toShown[raw.length] = shown.length
        val toRaw = IntArray(shown.length + 1)
        var r = 0
        for (j in shown.indices) {
            toRaw[j] = r
            if (shown[j] != ' ') r++
        }
        toRaw[shown.length] = raw.length

        return TransformedText(AnnotatedString(shown), object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = toShown[offset.coerceIn(0, raw.length)]
            override fun transformedToOriginal(offset: Int): Int = toRaw[offset.coerceIn(0, shown.length)]
        })
    }
}
